package com.scantoorder.scantoorder;

import org.junit.jupiter.api.extension.Extension;

/**
 * Tests never talk to Paystack. Whatever PAYSTACK_BASE_URL says, the app under test is pointed at a
 * port nothing listens on, so a call nobody mocked fails at once instead of reaching the real service.
 * Tests that need an answer mock the client or give the client a fake HTTP client.
 */
public class NoRealPaystack implements Extension {
    static {
        System.setProperty("paystack.base.url", "http://127.0.0.1:9");
    }
}
