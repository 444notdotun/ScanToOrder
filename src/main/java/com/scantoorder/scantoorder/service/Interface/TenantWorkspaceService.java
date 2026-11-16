package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.MyTenantResponse;

public interface TenantWorkspaceService {
    MyTenantResponse getMyTenant(String tenantId);

    MyTenantResponse setOpenForOrders(String tenantId, boolean open);

    MyTenantResponse setHolds(String tenantId, boolean enabled);
}
