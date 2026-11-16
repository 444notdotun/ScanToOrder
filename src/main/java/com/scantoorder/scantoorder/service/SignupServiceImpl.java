package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.SignupRequest;
import com.scantoorder.scantoorder.dtos.request.VerifyAccountRequest;
import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.BankListResponse;
import com.scantoorder.scantoorder.dtos.respond.SignupResponse;
import com.scantoorder.scantoorder.dtos.respond.VerifyAccountResponse;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.SignupService;
import com.scantoorder.scantoorder.utils.BusinessNames;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SignupServiceImpl implements SignupService {

    public static final String EMAIL_TAKEN = "This email already has a business on Plately. Sign in instead, or use another email.";
    public static final String NAME_TAKEN = "This name already has a business on Plately. Use another name, or add your area, like Triple4 Ikeja.";
    private static final String BANKS_KEY = "signup:banks";

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private PaystackClient paystackClient;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public List<BankData> banks() {
        Object cached = redisTemplate.opsForValue().get(BANKS_KEY);
        if (cached instanceof BankListResponse list && !list.getBanks().isEmpty()) {
            return list.getBanks();
        }
        List<BankData> banks = paystackClient.listBanks();
        if (!banks.isEmpty()) {
            redisTemplate.opsForValue().set(BANKS_KEY, new BankListResponse(banks), 1, TimeUnit.DAYS);
        }
        return banks;
    }

    @Override
    public VerifyAccountResponse verifyAccount(VerifyAccountRequest request) {
        return new VerifyAccountResponse(paystackClient.resolveAccountName(request.getBankCode(), request.getAccountNumber()));
    }

    @Transactional
    @Override
    public SignupResponse signup(SignupRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        Tenant existing = tenantRepo.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && existing.getStatus() != TenantStatus.DECLINED) {
            throw new ConflictException(EMAIL_TAKEN);
        }
        String businessName = request.getBusinessName().trim();
        String normalised = BusinessNames.normalise(businessName);
        tenantRepo.findByNormalizedName(normalised)
                .filter(other -> existing == null || !other.getTenantId().equals(existing.getTenantId()))
                .ifPresent(other -> {
                    throw new ConflictException(NAME_TAKEN);
                });
        // A declined owner who sends the form again replaces the old application and keeps its slug.
        Tenant tenant = existing != null ? existing : new Tenant();
        if (existing == null) {
            tenant.setSlug(freeSlug(businessName));
        }
        tenant.setName(businessName);
        tenant.setType(request.getType());
        tenant.setOwnerName(request.getOwnerName().trim());
        tenant.setEmail(email);
        tenant.setPhone(request.getPhone().trim());
        tenant.setStatus(TenantStatus.PENDING_APPROVAL);
        tenant.setDeclineReason(null);
        tenant.setOpenForOrders(false);
        tenant.setHoldsEnabled(request.getType() == TenantType.RESTAURANT);
        applyBank(tenant, request);
        try {
            tenant = tenantRepo.saveAndFlush(tenant);
        } catch (DataIntegrityViolationException e) {
            // Two people sending the same name or email at once: the database's unique indexes decide.
            String cause = String.valueOf(e.getMostSpecificCause().getMessage());
            throw new ConflictException(cause.contains("normalized_name") ? NAME_TAKEN : EMAIL_TAKEN);
        }
        return new SignupResponse(tenant.getSlug(), tenant.getStatus().name(),
                "Thank you. We will look at your application and email you at " + email + ".");
    }

    // Answers from the same unique index that guards the sign-up, so the form can say so while the owner types.
    @Override
    public boolean nameAvailable(String name) {
        String normalised = BusinessNames.normalise(name);
        return !normalised.isEmpty() && !tenantRepo.existsByNormalizedName(normalised);
    }

    // A restaurant's account is checked again here, so a number changed after the first check is caught.
    private void applyBank(Tenant tenant, SignupRequest request) {
        if (request.getType() == TenantType.EVENT_PLANNER) {
            tenant.setBankCode(null);
            tenant.setBankName(null);
            tenant.setAccountNumber(null);
            tenant.setAccountName(null);
            return;
        }
        if (isBlank(request.getBankCode()) || isBlank(request.getAccountNumber()) || isBlank(request.getAccountName())) {
            throw new InvalidRequestException("Restaurants need a payout account. Choose your bank, add the account number and check the name.");
        }
        String bankName = banks().stream()
                .filter(bank -> bank.getCode().equals(request.getBankCode()))
                .map(BankData::getName)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("We don't know that bank. Choose it from the list."));
        String resolved = paystackClient.resolveAccountName(request.getBankCode(), request.getAccountNumber());
        if (!resolved.trim().equalsIgnoreCase(request.getAccountName().trim())) {
            throw new InvalidRequestException("The account name doesn't match the bank's record. Check the account again before you send the form.");
        }
        tenant.setBankCode(request.getBankCode());
        tenant.setBankName(bankName);
        tenant.setAccountNumber(request.getAccountNumber());
        tenant.setAccountName(resolved.trim());
    }

    // Lower case letters and digits only; if taken, the next free number is added.
    private String freeSlug(String businessName) {
        String base = businessName.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            throw new InvalidRequestException("Use some letters or numbers in your business name.");
        }
        String slug = base;
        int next = 2;
        while (tenantRepo.existsBySlug(slug)) {
            slug = base + next++;
        }
        return slug;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
