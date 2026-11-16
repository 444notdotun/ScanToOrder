package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantActionResponse {
    private String tenantId;
    private String slug;
    private String status;
    private String managerUsername;
    private boolean emailSent;
    private String message;
}
