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

    @Autowired
    private TenantRepo tenantRepo;

    public void requireTakingOrders(Tenant tenant) {
        if (tenant == null || !tenant.isTakingOrders()) {
            throw new PlaceClosedException(PLACE_CLOSED);
        }
    }

    // An unknown slug gets the same answer as a closed place, so a scan reveals nothing about other businesses.
    public Tenant openTenantBySlug(String slug) {
        Tenant tenant = tenantRepo.findBySlug(slug == null ? "" : slug.toLowerCase()).orElse(null);
        requireTakingOrders(tenant);
        return tenant;
    }
}
