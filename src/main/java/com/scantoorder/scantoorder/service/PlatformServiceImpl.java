package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.exception.PayoutAccountRejectedException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.respond.TenantActionResponse;
import com.scantoorder.scantoorder.dtos.respond.TenantSummaryResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.EmailSender;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import com.scantoorder.scantoorder.utils.TemporaryPassword;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PlatformServiceImpl implements PlatformService {

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EmailSender emailSender;
    @Autowired
    private MenuService menuService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PaystackClient paystackClient;
    @Autowired
    private ChargeCalculator chargeCalculator;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public List<TenantSummaryResponse> listTenants(String status) {
        List<Tenant> tenants;
        if (status == null || status.isBlank()) {
            tenants = tenantRepo.findAllByOrderByCreatedAtDesc();
        } else {
            tenants = tenantRepo.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        }
        return tenants.stream().map(TenantSummaryResponse::of).toList();
    }

    // The account and its status are saved together; the email goes out only once they are safely saved.
    @Override
    public TenantActionResponse approve(String tenantId) {
        record Approved(Tenant tenant, String username, String password) {
        }
        // The payout account is set up with Paystack first. If Paystack refuses it, nothing has changed.
        Tenant waiting = find(tenantId);
        if (waiting.getStatus() != TenantStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only an application that is waiting can be approved.");
        }
        String subaccountCode = waiting.getType() == TenantType.RESTAURANT ? createSubaccount(waiting) : null;
        Approved approved = transactionTemplate.execute(status -> {
            Tenant tenant = find(tenantId);
            if (tenant.getStatus() != TenantStatus.PENDING_APPROVAL) {
                throw new ConflictException("Only an application that is waiting can be approved.");
            }
            String username = managerUsername(tenant);
            if (workerRepo.existsByUsername(username)) {
                throw new ConflictException("The sign-in " + username + " is already taken.");
            }
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setApprovedAt(LocalDateTime.now());
            tenant.setPaystackSubaccountCode(subaccountCode);
            tenantRepo.save(tenant);
            menuService.ensureActiveMenu(tenant);
            String password = TemporaryPassword.generate();
            Worker manager = new Worker();
            manager.setTenant(tenant);
            manager.setUsername(username);
            manager.setFullName(tenant.getOwnerName());
            manager.setRole(WorkerRole.MANAGER);
            manager.setPassword(passwordEncoder.encode(password));
            manager.setMustChangePassword(true);
            workerRepo.save(manager);
            return new Approved(tenant, username, password);
        });
        boolean sent = sendSignIn(approved.tenant(), approved.username(), approved.password());
        return action(approved.tenant(), approved.username(), sent,
                sent ? approved.tenant().getName() + " is approved and its sign-in is on the way."
                        : approved.tenant().getName() + " is approved, but the email could not be sent. Use resend sign-in to try again.");
    }

    @Override
    public TenantActionResponse decline(String tenantId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidRequestException("Say why the application is declined.");
        }
        Tenant tenant = transactionTemplate.execute(status -> {
            Tenant found = find(tenantId);
            if (found.getStatus() != TenantStatus.PENDING_APPROVAL) {
                throw new ConflictException("Only an application that is waiting can be declined.");
            }
            found.setStatus(TenantStatus.DECLINED);
            found.setDeclineReason(reason.trim());
            return tenantRepo.save(found);
        });
        boolean sent = send(tenant.getEmail(), "About your Plately application",
                "Hello " + tenant.getOwnerName() + ",\n\n"
                        + "We could not approve " + tenant.getName() + " this time.\n\n"
                        + "Reason: " + tenant.getDeclineReason() + "\n\n"
                        + "You can fix the details and send the form again at " + frontendUrl + "/signup.\n\n"
                        + "The Plately team");
        return action(tenant, null, sent, sent ? "Application declined and the owner has been told."
                : "Application declined, but the email could not be sent.");
    }

    @Override
    public TenantActionResponse suspend(String tenantId) {
        Tenant tenant = transactionTemplate.execute(status -> {
            Tenant found = find(tenantId);
            if (found.getStatus() != TenantStatus.ACTIVE) {
                throw new ConflictException("Only an active business can be suspended.");
            }
            found.setStatus(TenantStatus.SUSPENDED);
            return tenantRepo.save(found);
        });
        return action(tenant, null, false, tenant.getName() + " is suspended. Guests already seated can finish; no new guests can sit down.");
    }

    @Override
    public TenantActionResponse reinstate(String tenantId) {
        Tenant tenant = transactionTemplate.execute(status -> {
            Tenant found = find(tenantId);
            if (found.getStatus() != TenantStatus.SUSPENDED) {
                throw new ConflictException("Only a suspended business can be reinstated.");
            }
            found.setStatus(TenantStatus.ACTIVE);
            return tenantRepo.save(found);
        });
        return action(tenant, null, false, tenant.getName() + " is active again.");
    }

    @Override
    public TenantActionResponse resendSignIn(String tenantId) {
        record Reissued(Tenant tenant, String username, String password) {
        }
        Reissued reissued = transactionTemplate.execute(status -> {
            Tenant tenant = find(tenantId);
            if (tenant.getStatus() != TenantStatus.ACTIVE && tenant.getStatus() != TenantStatus.SUSPENDED) {
                throw new ConflictException("This business has no sign-in yet. Approve it first.");
            }
            Worker manager = workerRepo.findFirstByTenant_TenantIdAndRole(tenantId, WorkerRole.MANAGER)
                    .orElseThrow(() -> new ResourceNotFoundException("We cannot find this business's manager"));
            String password = TemporaryPassword.generate();
            manager.setPassword(passwordEncoder.encode(password));
            manager.setMustChangePassword(true);
            manager.setActive(true);
            workerRepo.save(manager);
            return new Reissued(tenant, manager.getUsername(), password);
        });
        boolean sent = sendSignIn(reissued.tenant(), reissued.username(), reissued.password());
        return action(reissued.tenant(), reissued.username(), sent,
                sent ? "A new sign-in is on the way." : "The new sign-in was saved, but the email could not be sent. Try again.");
    }

    private String createSubaccount(Tenant tenant) {
        if (tenant.getBankCode() == null || tenant.getAccountNumber() == null) {
            throw new PayoutAccountRejectedException(PaystackClientImpl.PAYOUT_REJECTED);
        }
        return paystackClient.createSubaccount(tenant.getName(), tenant.getBankCode(), tenant.getAccountNumber(),
                chargeCalculator.percent());
    }

    private boolean sendSignIn(Tenant tenant, String username, String password) {
        return send(tenant.getEmail(), "Your Plately sign-in",
                "Hello " + tenant.getOwnerName() + ",\n\n"
                        + tenant.getName() + " is approved on Plately. Welcome.\n\n"
                        + "Username: " + username + "\n"
                        + "Temporary password: " + password + "\n"
                        + "Sign in here: " + frontendUrl + "/login\n\n"
                        + "You will choose your own password the first time you sign in.\n\n"
                        + "The Plately team");
    }

    private boolean send(String to, String subject, String body) {
        try {
            emailSender.send(to, subject, body);
            return true;
        } catch (EmailSendException e) {
            log.warn("An email to a business owner could not be sent");
            return false;
        }
    }

    private Tenant find(String tenantId) {
        return tenantRepo.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("We cannot find that business"));
    }

    private String managerUsername(Tenant tenant) {
        return tenant.getSlug() + ".manager";
    }

    private TenantStatus parseStatus(String status) {
        try {
            return TenantStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Choose a status: pending_approval, declined, active or suspended");
        }
    }

    private TenantActionResponse action(Tenant tenant, String managerUsername, boolean emailSent, String message) {
        return new TenantActionResponse(tenant.getTenantId(), tenant.getSlug(), tenant.getStatus().name(),
                managerUsername, emailSent, message);
    }
}
