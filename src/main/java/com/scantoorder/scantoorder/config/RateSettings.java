package com.scantoorder.scantoorder.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Every limit in one place, each a setting with today's value as its default. A limit of zero or less would lock everyone out, so the app
 * refuses to start with one.
 */
@Getter
@Component
public class RateSettings {

    // Wrong passwords per username before it waits, and how long the wait (and the count) lasts.
    @Value("${app.rate.login-max:5}")
    private int loginMax;
    @Value("${app.rate.login-window-min:15}")
    private int loginWindowMinutes;
    // Requests per client address a minute, over the whole API.
    @Value("${app.rate.api-per-min:600}")
    private int apiPerMinute;
    // Emails an hour: forgot-password per email address, and a receipt per receipt.
    @Value("${app.rate.email-per-address-hour:3}")
    private int emailPerAddressHour;
    // Emails an hour per client address: forgot password, and receipts.
    @Value("${app.rate.email-per-ip-hour:20}")
    private int emailPerIpHour;

    @PostConstruct
    void check() {
        if (loginMax < 1 || loginWindowMinutes < 1 || apiPerMinute < 1 || emailPerAddressHour < 1 || emailPerIpHour < 1) {
            throw new IllegalStateException("Every RATE_ setting must be 1 or more.");
        }
    }
}
