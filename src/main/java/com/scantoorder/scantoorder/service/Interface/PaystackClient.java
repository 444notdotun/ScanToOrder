package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;

import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.PaystackSplit;
import java.math.BigDecimal;
import java.util.List;

public interface PaystackClient {
    // The split is explicit on every payment: subaccount, Plately's flat charge, and the fee bearer.
    PaystackInitResponseData initialize(String email, long amountKobo, String reference, PaystackSplit split);
    PaystackVerifyResponseData verify(String reference);

    // Returns the new subaccount's code. Throws PayoutAccountRejectedException when Paystack refuses the account.
    String createSubaccount(String businessName, String bankCode, String accountNumber, java.math.BigDecimal percentageCharge);

    // Refunds the full amount of a transaction. A refund reference that already exists counts as accepted.
    void createRefund(String transactionReference, String refundReference);

    List<BankData> listBanks();

    // Returns the account holder's name, or throws AccountVerificationException when the bank has no such account.
    String resolveAccountName(String bankCode, String accountNumber);
}
