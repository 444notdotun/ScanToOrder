package com.scantoorder.scantoorder.config;

import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The ids of suspended businesses, kept in a Redis set so the sign-in filter can refuse their staff at once. The set is filled from the
 * database when the app starts, and changed whenever a business is suspended or reinstated. If Redis cannot be reached, the filter falls
 * back to the business's status, which it already has from the database.
 */
@Slf4j
@Component
@Order(3)
public class SuspendedTenants implements ApplicationRunner {

    static final String KEY = "suspended:tenants";

    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private TenantRepo tenantRepo;

    @Override
    public void run(ApplicationArguments args) {
        refill();
    }

    // Starts the set again from the database.
    public void refill() {
        try {
            List<String> ids = tenantRepo.findByStatusOrderByCreatedAtDesc(TenantStatus.SUSPENDED).stream().map(Tenant::getTenantId).toList();
            redis.delete(KEY);
            if (!ids.isEmpty()) {
                redis.opsForSet().add(KEY, ids.toArray(String[]::new));
            }
        } catch (RuntimeException e) {
            log.warn("The list of suspended businesses could not be filled in Redis");
        }
    }

    public void add(String tenantId) {
        try {
            redis.opsForSet().add(KEY, tenantId);
        } catch (RuntimeException e) {
            log.warn("A suspended business could not be added to the list in Redis");
        }
    }

    public void remove(String tenantId) {
        try {
            redis.opsForSet().remove(KEY, tenantId);
        } catch (RuntimeException e) {
            log.warn("A reinstated business could not be removed from the list in Redis");
        }
    }

    // True when this business is suspended: by the Redis set, or by its own status when Redis cannot answer.
    public boolean blocks(Tenant tenant) {
        if (tenant == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.opsForSet().isMember(KEY, tenant.getTenantId()));
        } catch (RuntimeException e) {
            return tenant.getStatus() == TenantStatus.SUSPENDED;
        }
    }
}
