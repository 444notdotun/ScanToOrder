package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
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

import java.util.ArrayList;
import java.util.List;

@Service
public class TenantWorkspaceServiceImpl implements TenantWorkspaceService {

    public static final String PAYOUT_NOT_READY =
            "We can't take payments for you yet. Contact us to finish your payout setup.";

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private TableRepo tableRepo;
    @Autowired
    private ItemRepo itemRepo;

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

    private Tenant find(String tenantId) {
        return tenantRepo.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("We cannot find your business"));
    }
}
