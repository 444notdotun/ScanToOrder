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
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.service.email.EmailTemplate;
import com.scantoorder.scantoorder.service.email.Mailer;
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
    private Mailer mailer;
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
        // The payout account is set up with Paystack first. If Paystack refuses it, nothing has changed.
        Tenant waiting = find(tenantId);
        if (waiting.getStatus() != TenantStatus.PENDING_APPROVAL) {
            throw new ConflictException("Only an application that is waiting can be approved.");
        }
        String subaccountCode = waiting.getType() == TenantType.RESTAURANT ? createSubaccount(waiting) : null;
        Approved approved;
        try {
            approved = approveInTransaction(tenantId, subaccountCode);
        } catch (RuntimeException e) {
            if (subaccountCode != null) {
                // Paystack already has the subaccount. The platform owner removes it by hand.
                log.error("Approval of tenant {} failed after Paystack created subaccount {}. The subaccount can be removed.", tenantId, subaccountCode);
            }
            throw e;
        }
        boolean sent = sendSignIn(approved.tenant(), approved.username(), approved.password());
        return action(approved.tenant(), approved.username(), sent,
                sent ? approved.tenant().getName() + " is approved and its sign-in is on the way."
                        : approved.tenant().getName() + " is approved, but the email could not be sent. Use resend sign-in to try again.");
    }

    private record Approved(Tenant tenant, String username, String password) {
    }

    private Approved approveInTransaction(String tenantId, String subaccountCode) {
        return transactionTemplate.execute(status -> {
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
        EmailTemplate.Rendered declined = EmailTemplate.of(tenant.getName(), "About your application")
                .paragraph("Hello " + tenant.getOwnerName() + ",")
                .paragraph("We could not approve " + tenant.getName() + " this time.")
                .details(List.of(new EmailTemplate.Row("Reason", tenant.getDeclineReason())))
                .paragraph("You can fix the details and send the form again.")
                .button("Send the form again", frontendUrl + "/signup")
                .render();
        boolean sent = mailer.send(EmailMessage.of(tenant.getEmail(), "About your Plately application", declined, "decline",
                "decline:" + tenant.getTenantId() + ":" + System.currentTimeMillis()));
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
        EmailTemplate.Rendered paused = EmailTemplate.of(tenant.getName(), "Your account is paused")
                .paragraph(tenant.getName() + " is paused on Plately. Guests can't order and your team can't sign in for now. Reply to this email and we'll sort it out with you.")
                .render();
        boolean sent = mailer.send(EmailMessage.of(tenant.getEmail(), "Your Plately account is paused", paused, "suspended",
                "suspend:" + tenant.getTenantId() + ":" + System.currentTimeMillis()));
        return action(tenant, null, sent, tenant.getName() + " is suspended. Guests already seated can finish; no new guests can sit down.");
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
        EmailTemplate.Rendered back = EmailTemplate.of(tenant.getName(), "Your account is back")
                .paragraph(tenant.getName() + " is back on Plately. Your team can sign in and guests can order again.")
                .button("Sign in", frontendUrl + "/staff/login")
                .render();
        boolean sent = mailer.send(EmailMessage.of(tenant.getEmail(), "Your Plately account is back", back, "reinstated",
                "reinstate:" + tenant.getTenantId() + ":" + System.currentTimeMillis()));
        return action(tenant, null, sent, tenant.getName() + " is active again.");
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
        EmailTemplate.Rendered signIn = EmailTemplate.of(tenant.getName(), "Welcome to Plately")
                .paragraph("Hello " + tenant.getOwnerName() + ",")
                .paragraph(tenant.getName() + " is approved on Plately. Welcome.")
                .details(List.of(new EmailTemplate.Row("Username", username), new EmailTemplate.Row("Temporary password", password)))
                .paragraph("You will choose your own password the first time you sign in.")
                .button("Sign in", frontendUrl + "/staff/login")
                .render();
        return mailer.send(EmailMessage.of(tenant.getEmail(), "Your Plately sign-in", signIn, "sign-in",
                "sign-in:" + tenant.getTenantId() + ":" + System.currentTimeMillis()));
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
