package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.SignupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Checks a bank account with Paystack and gives back the bank's name and the holder's name.
 */
@Component
public class BankResolver {

    public record Resolved(String bankName, String accountName) {
    }

    @Autowired
    private SignupService signupService;
    @Autowired
    private PaystackClient paystackClient;

    // Throws AccountVerificationException when the bank has no such account.
    public Resolved resolve(String bankCode, String accountNumber) {
        String bankName = signupService.banks().stream()
                .filter(bank -> bank.getCode().equals(bankCode))
                .map(BankData::getName)
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("We don't know that bank. Choose it from the list."));
        return new Resolved(bankName, paystackClient.resolveAccountName(bankCode, accountNumber).trim());
    }
}
