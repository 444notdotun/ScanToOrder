package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StaffResponse {
    private String workerId;
    private String username;
    private String fullName;
    private String role;
    // Shown once, when the account is made, and never again.
    private String temporaryPassword;
}
