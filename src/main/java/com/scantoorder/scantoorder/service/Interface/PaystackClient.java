package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;

import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.PaystackSplit;
import com.scantoorder.scantoorder.dtos.respond.SubaccountData;
import java.math.BigDecimal;
import java.util.List;

public interface PaystackClient {
    // The split is explicit on every payment: subaccount, Plately's flat charge, and the fee bearer.
    PaystackInitResponseData initialize(String email, long amountKobo, String reference, PaystackSplit split);
    PaystackVerifyResponseData verify(String reference);

    // Returns the new subaccount's code. Throws PayoutAccountRejectedException when Paystack refuses the account.
    String createSubaccount(String businessName, String bankCode, String accountNumber, java.math.BigDecimal percentageCharge);

    // Changes where a subaccount's share is paid. Throws PayoutAccountRejectedException when Paystack refuses it.
    void updateSubaccount(String subaccountCode, String bankCode, String accountNumber);

    // Every subaccount on Plately's Paystack account, so the seeder can reuse one instead of making a twin.
    List<SubaccountData> listSubaccounts();

    List<BankData> listBanks();

    // Returns the account holder's name, or throws AccountVerificationException when the bank has no such account.
    String resolveAccountName(String bankCode, String accountNumber);
}
