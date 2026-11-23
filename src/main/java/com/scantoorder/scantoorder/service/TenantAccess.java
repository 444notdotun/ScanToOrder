package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.exception.PlaceClosedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Who may start a new session at a business. A guest already seated is never asked again.
 */
@Component
public class TenantAccess {

    public static final String PLACE_CLOSED = "This place isn't taking orders on Plately yet.";

    // What a guest reads when their place has been paused. It is not the staff's wording: the guest has nobody to "contact".
    public static final String PLACE_PAUSED = "This place isn't taking orders right now. A waiter can help you.";

    @Autowired
    private TenantRepo tenantRepo;

    // A guest already seated at a suspended business cannot start or pay for another order. A closed place is different: seated guests finish.
    public void requireNotSuspended(Tenant tenant) {
        if (tenant != null && tenant.getStatus() == com.scantoorder.scantoorder.data.model.TenantStatus.SUSPENDED) {
            throw new PlaceClosedException(PLACE_PAUSED, tenant.getName());
        }
    }

    public void requireTakingOrders(Tenant tenant) {
        if (tenant == null || !tenant.isTakingOrders()) {
            throw new PlaceClosedException(PLACE_CLOSED, tenant == null ? null : tenant.getName());
        }
    }

    // An unknown slug gets the same answer as a closed place, so a scan reveals nothing about other businesses.
    public Tenant openTenantBySlug(String slug) {
        Tenant tenant = tenantRepo.findBySlug(slug == null ? "" : slug.toLowerCase()).orElse(null);
        requireTakingOrders(tenant);
        return tenant;
    }
}
