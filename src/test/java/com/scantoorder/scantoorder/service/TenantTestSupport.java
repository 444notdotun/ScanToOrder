package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Menu;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.service.Interface.MenuService;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TenantTestSupport {
    public static final String MAIN_SLUG = "testplace";
    public static final String OTHER_SLUG = "otherplace";

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private MenuService menuService;

    public Tenant main() {
        return tenant(MAIN_SLUG, "Test Place");
    }

    public Tenant other() {
        return tenant(OTHER_SLUG, "Other Place");
    }

    public Tenant tenant(String slug, String name) {
        return tenantRepo.findBySlug(slug).orElseGet(() -> {
            Tenant tenant = new Tenant();
            tenant.setName(name);
            tenant.setSlug(slug);
            tenant.setType(TenantType.RESTAURANT);
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setOpenForOrders(true);
            tenant.setHoldsEnabled(true);
            tenant.setOwnerName("Owner of " + name);
            tenant.setEmail(slug + "@example.com");
            tenant.setPhone("08000000000");
            tenant = tenantRepo.save(tenant);
            menuService.ensureActiveMenu(tenant);
            return tenant;
        });
    }

    public Tenant resetMain() {
        Tenant tenant = main();
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setOpenForOrders(true);
        tenant.setHoldsEnabled(true);
        tenant.setType(TenantType.RESTAURANT);
        return tenantRepo.save(tenant);
    }

    public String mainId() {
        return main().getTenantId();
    }

    public Menu menuOf(Tenant tenant) {
        return menuService.ensureActiveMenu(tenant);
    }

    public Tenant pendingTenant(String namePrefix) {
        String name = uniqueName(namePrefix);
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setSlug(name.toLowerCase().replaceAll("[^a-z0-9]", ""));
        tenant.setType(TenantType.RESTAURANT);
        tenant.setStatus(TenantStatus.PENDING_APPROVAL);
        tenant.setHoldsEnabled(true);
        tenant.setOwnerName("Owner");
        tenant.setEmail(UUID.randomUUID().toString().substring(0, 12) + "@example.com");
        tenant.setPhone("08000000000");
        return tenantRepo.save(tenant);
    }

    public Tenant freshActiveTenant() {
        Tenant tenant = pendingTenant("fresh");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setApprovedAt(java.time.LocalDateTime.now());
        tenant = tenantRepo.save(tenant);
        menuService.ensureActiveMenu(tenant);
        return tenant;
    }

    public void signInAs(WorkerRole role, Tenant tenant) {
        WorkerPrincipal principal = new WorkerPrincipal("worker-" + role.name(), role.name(), tenant == null ? null : tenant.getTenantId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        SecurityContextHolder.setContext(context);
    }

    public void signInAsManager() {
        signInAs(WorkerRole.MANAGER, main());
    }

    public void signOut() {
        SecurityContextHolder.clearContext();
    }

    public String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
