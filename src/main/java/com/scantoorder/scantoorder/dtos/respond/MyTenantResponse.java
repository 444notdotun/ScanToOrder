package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.Tenant;
import lombok.Data;

// The business's own view of itself. The bank account is shown by its last four digits only.
@Data
public class MyTenantResponse {
    private String name;
    private String slug;
    // The business's own email: where a receipt goes unless the manager types another.
    private String email;
    private String type;
    private String status;
    private boolean openForOrders;
    private boolean holdsEnabled;
    private String bankName;
    private String accountLast4;

    public static MyTenantResponse of(Tenant tenant) {
        MyTenantResponse response = new MyTenantResponse();
        response.setName(tenant.getName());
        response.setSlug(tenant.getSlug());
        response.setEmail(tenant.getEmail());
        response.setType(tenant.getType().name());
        response.setStatus(tenant.getStatus().name());
        response.setOpenForOrders(tenant.isOpenForOrders());
        response.setHoldsEnabled(tenant.isHoldsEnabled());
        response.setBankName(tenant.getBankName());
        String number = tenant.getAccountNumber();
        if (number != null && number.length() >= 4) {
            response.setAccountLast4(number.substring(number.length() - 4));
        }
        return response;
    }
}
