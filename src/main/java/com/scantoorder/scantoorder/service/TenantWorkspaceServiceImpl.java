package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.Worker;
import com.scantoorder.scantoorder.data.repository.WorkerRepo;
import com.scantoorder.scantoorder.dtos.request.PayoutAccountRequest;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.service.Interface.EmailSender;
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
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.exception.SuspendedTenantException;
import com.scantoorder.scantoorder.service.Interface.TenantWorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private EmailSender emailSender;

    @Override
    public MyTenantResponse getMyTenant(String tenantId) {
        return MyTenantResponse.of(find(tenantId));
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
            if (tenant.getPaystackSubaccountCode() == null || tenant.getPaystackSubaccountCode().isBlank()) {
                throw new ConflictException(PAYOUT_NOT_READY);
            }
        }
        tenant.setOpenForOrders(open);
        return MyTenantResponse.of(tenantRepo.save(tenant));
    }

    @Transactional
    @Override
    public MyTenantResponse setHolds(String tenantId, boolean enabled) {
        Tenant tenant = find(tenantId);
        if (enabled && tenant.getType() == TenantType.EVENT_PLANNER) {
            throw new InvalidRequestException("Holding seats isn't available for events yet.");
        }
        tenant.setHoldsEnabled(enabled);
        return MyTenantResponse.of(tenantRepo.save(tenant));
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
        return MyTenantResponse.of(tenant);
    }

    private void tellTheOwner(Tenant tenant) {
        String number = tenant.getAccountNumber();
        String last4 = number.substring(number.length() - 4);
        try {
            emailSender.send(tenant.getEmail(), "Your payout account was changed",
                    "Hello " + tenant.getOwnerName() + ",\n\n"
                            + "The payout account for " + tenant.getName() + " was changed.\n\n"
                            + "New bank: " + tenant.getBankName() + "\n"
                            + "New account ending: " + last4 + "\n"
                            + "When: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) + "\n\n"
                            + "If this wasn't you, contact Plately straight away.\n\n"
                            + "The Plately team");
        } catch (EmailSendException e) {
            log.warn("The payout account change email could not be sent for tenant {}", tenant.getTenantId());
        }
    }

    private Tenant find(String tenantId) {
        return tenantRepo.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("We cannot find your business"));
    }
}
