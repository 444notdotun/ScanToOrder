package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.PayoutAccountRequest;
import com.scantoorder.scantoorder.dtos.respond.MyTenantResponse;

public interface TenantWorkspaceService {
    MyTenantResponse getMyTenant(String tenantId);

    MyTenantResponse setOpenForOrders(String tenantId, boolean open);

    MyTenantResponse setHolds(String tenantId, boolean enabled);

    // Moves the business's payouts to another bank account. Needs the manager's own password.
    MyTenantResponse changePayoutAccount(String tenantId, String workerId, PayoutAccountRequest request);
}
