package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.respond.EventPriceResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * What an event costs: guests x the fee per guest, in whole kobo.
 */
@Component
public class EventPricing {

    @Value("${app.event.fee-per-guest-kobo:50000}")
    private long feePerGuestKobo;

    @PostConstruct
    void validate() {
        if (feePerGuestKobo < 1) {
            throw new IllegalStateException("EVENT_FEE_PER_GUEST_KOBO must be 1 or more");
        }
    }

    public long amountKobo(int guests) {
        return Math.multiplyExact(feePerGuestKobo, (long) guests);
    }

    public EventPriceResponse price(int guests) {
        long amountKobo = amountKobo(guests);
        return new EventPriceResponse(guests, feePerGuestKobo, amountKobo, BigDecimal.valueOf(amountKobo, 2));
    }
}
