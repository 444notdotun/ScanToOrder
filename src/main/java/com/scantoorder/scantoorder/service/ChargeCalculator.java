package com.scantoorder.scantoorder.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Splits a payment between Plately and the restaurant. All money is whole kobo.
 */
@Component
public class ChargeCalculator {

    public record Split(long amountKobo, long platelyChargeKobo, long tenantAmountKobo) {
    }

    // A placeholder until pricing is decided.
    @Value("${app.charge.percent:2.0}")
    private BigDecimal percent;

    @Value("${app.charge.paystack-fee-bearer:account}")
    private String feeBearer;

    @PostConstruct
    void validate() {
        if (percent == null || percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException("PLATELY_CHARGE_PERCENT must be between 0 and 100");
        }
        if (!"account".equals(feeBearer) && !"subaccount".equals(feeBearer)) {
            throw new IllegalStateException("PAYSTACK_FEE_BEARER must be account or subaccount");
        }
    }

    // Rounded down to the kobo, so a guest is never charged more than the percentage by rounding.
    public long chargeKobo(long amountKobo) {
        if (amountKobo < 0) {
            throw new IllegalArgumentException("An amount cannot be negative");
        }
        return BigDecimal.valueOf(amountKobo).multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .longValueExact();
    }

    public Split split(long amountKobo) {
        long charge = chargeKobo(amountKobo);
        return new Split(amountKobo, charge, amountKobo - charge);
    }

    public String feeBearer() {
        return feeBearer;
    }

    public BigDecimal percent() {
        return percent;
    }
}
