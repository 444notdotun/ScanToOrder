package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;

import java.math.BigDecimal;

public interface PaystackClient {
    PaystackInitResponseData initialize(String email, BigDecimal amount, String reference);
    PaystackVerifyResponseData verify(String reference);
}
