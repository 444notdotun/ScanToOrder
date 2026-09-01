package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scantoorder.scantoorder.data.model.WorkerRole;
import lombok.Data;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class AuthResponse {
    private String token;
    private String username;
    private WorkerRole role;
}
