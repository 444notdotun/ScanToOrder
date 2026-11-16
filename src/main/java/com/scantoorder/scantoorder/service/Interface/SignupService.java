package com.scantoorder.scantoorder.service.Interface;

import com.scantoorder.scantoorder.dtos.request.SignupRequest;
import com.scantoorder.scantoorder.dtos.request.VerifyAccountRequest;
import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.SignupResponse;
import com.scantoorder.scantoorder.dtos.respond.VerifyAccountResponse;

import java.util.List;

public interface SignupService {
    List<BankData> banks();

    VerifyAccountResponse verifyAccount(VerifyAccountRequest request);

    SignupResponse signup(SignupRequest request);
}
