package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.PayoutAccountRequest;
import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.service.email.EmailTemplate;
import com.scantoorder.scantoorder.service.email.Mailer;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.repository.ItemRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.respond.MyTenantResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.utils.TimeText;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.exception.SuspendedTenantException;
import com.scantoorder.scantoorder.service.Interface.TenantWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TenantWorkspaceServiceImpl implements TenantWorkspaceService {

    public static final String WRONG_PASSWORD = "That password isn't right.";
    public static final String PAYOUT_NOT_READY =
            "We can't take payments for you yet. Contact us to finish your payout setup.";

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private ItemRepo itemRepo;
    @Autowired
    private WorkerRepo workerRepo;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private BankResolver bankResolver;
    @Autowired
    private PaystackClient paystackClient;
    @Autowired
    private Mailer mailer;
    @Autowired
    private UnsplitPayments unsplitPayments;

    private MyTenantResponse view(Tenant tenant) {
        MyTenantResponse response = MyTenantResponse.of(tenant);
        response.setTestPaymentsUnsplit(unsplitPayments.appliesTo(tenant));
        return response;
    }

    @Override
    public MyTenantResponse getMyTenant(String tenantId) {
        return view(find(tenantId));
    }

    // Switching off is always allowed and only stops new guests. Switching on needs something to serve and somewhere to sit.
    @Transactional
    @Override
    public MyTenantResponse setOpenForOrders(String tenantId, boolean open) {
        Tenant tenant = find(tenantId);
        if (open) {
            if (tenant.getStatus() == TenantStatus.SUSPENDED) {
                throw new SuspendedTenantException(AuthImplementation.SUSPENDED);
            }
            if (tenant.getType() == TenantType.EVENT_PLANNER) {
                throw new InvalidRequestException("Events are coming soon, so an event planner can't open for orders yet.");
            }
            List<String> missing = new ArrayList<>();
            if (itemRepo.countByTenant_TenantIdAndIsAvailableTrue(tenantId) == 0) {
                missing.add("at least one plate in stock on your menu");
            }
            if (tableRepo.countActive(tenantId) == 0) {
                missing.add("at least one table");
            }
            if (!missing.isEmpty()) {
                throw new ConflictException("You can open once you have " + String.join(" and ", missing) + ".");
            }
            if ((tenant.getPaystackSubaccountCode() == null || tenant.getPaystackSubaccountCode().isBlank()) && !unsplitPayments.active()) {
                throw new ConflictException(PAYOUT_NOT_READY);
            }
        }
        tenant.setOpenForOrders(open);
        return view(tenantRepo.save(tenant));
    }

    @Transactional
    @Override
    public MyTenantResponse setHolds(String tenantId, boolean enabled) {
        Tenant tenant = find(tenantId);
        if (enabled && tenant.getType() == TenantType.EVENT_PLANNER) {
            throw new InvalidRequestException("Holding seats isn't available for events yet.");
        }
        tenant.setHoldsEnabled(enabled);
        return view(tenantRepo.save(tenant));
    }

    // Paystack is told first and the new details are kept only once it accepts them, so the two never disagree.
    // No transaction is open while Paystack is called.
    @Override
    public MyTenantResponse changePayoutAccount(String tenantId, String workerId, PayoutAccountRequest request) {
        Worker manager = workerRepo.findByWorkerIdAndTenant_TenantId(workerId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("We cannot find your account"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), manager.getPassword())) {
            throw new InvalidRequestException(WRONG_PASSWORD);
        }
        Tenant tenant = find(tenantId);
        String subaccount = tenant.getPaystackSubaccountCode();
        if (subaccount == null || subaccount.isBlank()) {
            throw new ConflictException(PAYOUT_NOT_READY);
        }
        BankResolver.Resolved resolved = bankResolver.resolve(request.getBankCode(), request.getAccountNumber());
        paystackClient.updateSubaccount(subaccount, request.getBankCode(), request.getAccountNumber());
        try {
            tenant.setBankCode(request.getBankCode());
            tenant.setBankName(resolved.bankName());
            tenant.setAccountNumber(request.getAccountNumber());
            tenant.setAccountName(resolved.accountName());
            tenant = tenantRepo.save(tenant);
        } catch (RuntimeException e) {
            // Paystack already pays the new account. The platform owner needs to know which one.
            log.error("Paystack accepted a new payout account for tenant {} (subaccount {}) but it was not saved", tenantId, subaccount);
            throw e;
        }
        tellTheOwner(tenant);
        return view(tenant);
    }

    private void tellTheOwner(Tenant tenant) {
        String number = tenant.getAccountNumber();
        String last4 = number.substring(number.length() - 4);
        EmailTemplate.Rendered body = EmailTemplate.of(tenant.getName(), "Your payout account was changed")
                .paragraph("Hello " + tenant.getOwnerName() + ",")
                .paragraph("The payout account for " + tenant.getName() + " was changed.")
                .details(List.of(
                        new EmailTemplate.Row("New bank", tenant.getBankName()),
                        new EmailTemplate.Row("New account ending", last4),
                        new EmailTemplate.Row("When", TimeText.dayAndClock(LocalDateTime.now()))))
                .note("If this wasn't you, contact Plately straight away.")
                .render();
        mailer.sendAfterCommit(EmailMessage.of(tenant.getEmail(), "Your payout account was changed", body, "payout-changed",
                "payout:" + tenant.getTenantId() + ":" + System.currentTimeMillis()));
    }

    private Tenant find(String tenantId) {
        return tenantRepo.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("We cannot find your business"));
    }
}
