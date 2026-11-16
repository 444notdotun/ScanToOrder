package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;

import com.scantoorder.scantoorder.dtos.respond.BankData;
import java.math.BigDecimal;
import java.util.List;

public interface PaystackClient {
    PaystackInitResponseData initialize(String email, BigDecimal amount, String reference);
    PaystackVerifyResponseData verify(String reference);

    List<BankData> listBanks();

    // Returns the account holder's name, or throws AccountVerificationException when the bank has no such account.
    String resolveAccountName(String bankCode, String accountNumber);
}
