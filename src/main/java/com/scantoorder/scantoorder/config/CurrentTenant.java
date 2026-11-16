package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.CustomerPrincipal;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The tenant of the signed-in caller, read from the token and never from the request.
 */
public final class CurrentTenant {

    private CurrentTenant() {
    }

    public static String id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        String tenantId = null;
        if (principal instanceof CustomerPrincipal customer) {
            tenantId = customer.getTenantId();
        } else if (principal instanceof WorkerPrincipal worker) {
            tenantId = worker.getTenantId();
        }
        if (tenantId == null) {
            throw new AccessDeniedException("This is not available to your account");
        }
        return tenantId;
    }
}
