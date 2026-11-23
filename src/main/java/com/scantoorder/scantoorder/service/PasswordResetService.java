package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.config.RateSettings;
import com.scantoorder.scantoorder.data.model.PasswordResetToken;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.PasswordResetTokenRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.service.email.EmailTemplate;
import com.scantoorder.scantoorder.service.email.Mailer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Forgot password, for the person who runs a business (a restaurant manager or an event planner). Staff have no email, so a manager
 * resets theirs instead. Nobody can tell from the answer, or from how long it took, whether an email was sent.
 */
@Slf4j
@Service
public class PasswordResetService {

    public static final String ASKED = "If that email is on a Plately account, a link is on its way. It works for 15 minutes.";
    public static final String DEAD_LINK = "This link has expired or was already used. Ask for a new one.";
    public static final String CHANGED = "Your password is changed. Sign in with it now.";
    static final Duration LIFETIME = Duration.ofMinutes(15);
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordResetTokenRepo tokenRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RateLimiter limiter;
    @Autowired
    private RateSettings rates;
    @Autowired
    private Mailer mailer;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;
    // Every answer takes at least this long, whether or not an email is sent.
    @Value("${app.password-reset.min-millis:600}")
    private long floorMillis;

    // The email goes out on its own thread, so how long the mail provider takes never shows in the answer.
    private Executor sender = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "reset-email");
        thread.setDaemon(true);
        return thread;
    });

    public String ask(String rawEmail, String address) {
        long started = System.nanoTime();
        try {
            String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
            // Both counters always move, so a refusal on one does not show on the other.
            boolean emailOk = limiter.allow("reset-email", email, rates.getEmailPerAddressHour(), WINDOW);
            boolean addressOk = limiter.allow("reset-address", address == null ? "unknown" : address, rates.getEmailPerIpHour(), WINDOW);
            if (emailOk && addressOk && !email.isBlank()) {
                issueFor(email);
            }
        } catch (RuntimeException e) {
            // Whatever went wrong, the answer stays the same. The log has the type, never the address.
            log.warn("A password reset request failed: {}", e.getClass().getSimpleName());
        }
        waitOutTheFloor(started);
        return ASKED;
    }

    private void issueFor(String email) {
        Optional<Tenant> found = tenantRepo.findByEmailIgnoreCase(email).filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE);
        if (found.isEmpty()) {
            return;
        }
        Tenant tenant = found.get();
        Optional<Worker> manager = workerRepo.findFirstByTenant_TenantIdAndRole(tenant.getTenantId(), WorkerRole.MANAGER).filter(Worker::isActive);
        if (manager.isEmpty()) {
            return;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordResetToken saved = transactionTemplate.execute(status -> {
            Instant now = Instant.now();
            tokenRepo.endAllFor(manager.get().getWorkerId(), now);
            PasswordResetToken row = new PasswordResetToken();
            row.setWorker(manager.get());
            row.setTokenHash(hash(token));
            row.setCreatedAt(now);
            row.setExpiresAt(now.plus(LIFETIME));
            return tokenRepo.save(row);
        });
        String link = frontendUrl + "/reset-password?token=" + token;
        EmailTemplate.Rendered body = EmailTemplate.of(tenant.getName(), "Reset your password")
                .paragraph("Someone asked to reset the password for " + tenant.getName() + ".")
                .paragraph("If it was you, choose a new one here. The link works for 15 minutes, once.")
                .button("Choose a new password", link)
                .note("If you didn't ask, ignore this email. Your password stays the same.")
                .render();
        EmailMessage message = EmailMessage.of(tenant.getEmail(), "Reset your Plately password", body, "password-reset", "reset:" + saved.getId());
        sender.execute(() -> mailer.send(message));
    }

    public String reset(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException(DEAD_LINK);
        }
        String result = transactionTemplate.execute(status -> {
            PasswordResetToken row = tokenRepo.findByTokenHash(hash(token.trim())).orElse(null);
            Instant now = Instant.now();
            if (row == null || row.getUsedAt() != null || !row.getExpiresAt().isAfter(now) || !row.getWorker().isActive()) {
                return null;
            }
            if (newPassword == null || newPassword.length() < 8) {
                throw new InvalidRequestException("Your new password needs at least 8 characters.");
            }
            // Only one request can use a link; the other sees it as already used.
            if (tokenRepo.markUsed(row.getId(), now) != 1) {
                return null;
            }
            Worker worker = row.getWorker();
            worker.setPassword(passwordEncoder.encode(newPassword));
            worker.setMustChangePassword(false);
            worker.setPasswordChangedAt(now);
            workerRepo.save(worker);
            tokenRepo.endAllFor(worker.getWorkerId(), now);
            return CHANGED;
        });
        if (result == null) {
            throw new InvalidRequestException(DEAD_LINK);
        }
        return result;
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void waitOutTheFloor(long startedNanos) {
        long left = floorMillis - (System.nanoTime() - startedNanos) / 1_000_000;
        if (left > 0) {
            try {
                Thread.sleep(left);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
