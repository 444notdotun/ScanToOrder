package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.TenantActionResponse;
import com.scantoorder.scantoorder.dtos.respond.TenantSummaryResponse;

import java.util.List;

public interface PlatformService {
    List<TenantSummaryResponse> listTenants(String status);

    TenantActionResponse approve(String tenantId);

    TenantActionResponse decline(String tenantId, String reason);

    TenantActionResponse suspend(String tenantId);

    TenantActionResponse reinstate(String tenantId);

    TenantActionResponse resendSignIn(String tenantId);
}
