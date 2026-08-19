package com.scantoorder.scantoorder.dtos.respond;

import com.scantoorder.scantoorder.data.model.WorkerRole;
import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String username;
    private WorkerRole role;
}
