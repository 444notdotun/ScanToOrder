package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Tenant;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lets a restaurant without a Paystack subaccount take payments, for testing only. Paystack refuses to create a
 * subaccount on the owner's account, so in test mode the whole amount goes to Plately's own test account instead.
 * With real money a restaurant still needs its subaccount.
 */
@Component
public class UnsplitPayments {
    static final String TEST_KEY_PREFIX = "sk_test_";

    @Value("${app.payments.allow-unsplit:false}")
    private boolean allowed;

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @PostConstruct
    void validate() {
        if (allowed && !paystackSecretKey.startsWith(TEST_KEY_PREFIX)) {
            throw new IllegalStateException("PAYMENTS_ALLOW_UNSPLIT is for Paystack test mode only. Turn it off, or use a key that starts with sk_test_.");
        }
    }

    // True only with the setting on and a test key.
    public boolean active() {
        return allowed && paystackSecretKey.startsWith(TEST_KEY_PREFIX);
    }

    // True when this restaurant has no subaccount and is taking payments the test way.
    public boolean appliesTo(Tenant tenant) {
        String subaccount = tenant.getPaystackSubaccountCode();
        return active() && (subaccount == null || subaccount.isBlank());
    }
}
