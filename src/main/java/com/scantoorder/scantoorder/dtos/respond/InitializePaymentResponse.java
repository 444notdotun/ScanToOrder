package com.scantoorder.scantoorder.dtos.respond;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InitializePaymentResponse {
    private String authorizationUrl;
    private String reference;
}

