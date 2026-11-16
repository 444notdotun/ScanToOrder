package com.scantoorder.scantoorder.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Times as a person writes them: "12:00 am", never "0:00" or "00:00".
 */
public final class TimeText {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private TimeText() {
    }

    // "12:00 am", "6:30 pm".
    public static String clock(LocalDateTime time) {
        return time.format(CLOCK).toLowerCase(Locale.ENGLISH);
    }

    // "16 Nov 2026, 12:00 am".
    public static String dayAndClock(LocalDateTime time) {
        return time.format(DAY) + ", " + clock(time);
    }
}
