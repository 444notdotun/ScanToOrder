package com.scantoorder.scantoorder.dtos.request;

import com.scantoorder.scantoorder.data.model.RefundMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SettleRefundRequest {
    @NotNull(message = "Say how you paid the guest back: CASH or TRANSFER")
    private RefundMethod method;
    // Required for a transfer: the bank's reference, so the payment can be found later.
    @Size(max = 100, message = "That reference is too long")
    private String transferReference;
}
