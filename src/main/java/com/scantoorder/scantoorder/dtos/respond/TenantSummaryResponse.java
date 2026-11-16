package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.Tenant;
import lombok.Data;

import java.time.LocalDateTime;

// Never carries the account number.
@Data
public class TenantSummaryResponse {
    private String tenantId;
    private String name;
    private String slug;
    private String type;
    private String status;
    private String ownerName;
    private String email;
    private String phone;
    private String declineReason;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    public static TenantSummaryResponse of(Tenant tenant) {
        TenantSummaryResponse response = new TenantSummaryResponse();
        response.setTenantId(tenant.getTenantId());
        response.setName(tenant.getName());
        response.setSlug(tenant.getSlug());
        response.setType(tenant.getType().name());
        response.setStatus(tenant.getStatus().name());
        response.setOwnerName(tenant.getOwnerName());
        response.setEmail(tenant.getEmail());
        response.setPhone(tenant.getPhone());
        response.setDeclineReason(tenant.getDeclineReason());
        response.setCreatedAt(tenant.getCreatedAt());
        response.setApprovedAt(tenant.getApprovedAt());
        return response;
    }
}
