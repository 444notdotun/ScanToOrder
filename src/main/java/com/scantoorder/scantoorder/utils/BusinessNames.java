package com.scantoorder.scantoorder.utils;

import java.util.Locale;

/**
 * "Triple4" and "triple 4" are the same business name: names are compared with the case and every space removed.
 */
public final class BusinessNames {

    private BusinessNames() {
    }

    public static String normalise(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
