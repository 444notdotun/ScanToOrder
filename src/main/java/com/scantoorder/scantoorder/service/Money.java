package com.scantoorder.scantoorder.service;

import java.math.BigDecimal;

final class Money {

    private Money() {
    }

    static String naira(long kobo) {
        return String.format(java.util.Locale.US, "₦%,.2f", BigDecimal.valueOf(kobo, 2));
    }
}
