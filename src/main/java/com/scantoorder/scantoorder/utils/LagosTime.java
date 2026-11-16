package com.scantoorder.scantoorder.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Days, weeks and months as a business in Lagos sees them. Records are stamped in the server's own zone,
 * so a Lagos period is converted to that zone before it is compared.
 */
public final class LagosTime {

    public static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private LagosTime() {
    }

    // A half-open range [from, to) in the server's zone.
    public record Range(LocalDateTime from, LocalDateTime to) {
    }

    public static Range period(String name, ZonedDateTime now) {
        LocalDate today = now.withZoneSameInstant(LAGOS).toLocalDate();
        LocalDate first;
        LocalDate after;
        switch (name == null ? "today" : name) {
            case "today" -> {
                first = today;
                after = today.plusDays(1);
            }
            case "week" -> {
                first = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                after = first.plusWeeks(1);
            }
            case "month" -> {
                first = today.withDayOfMonth(1);
                after = first.plusMonths(1);
            }
            default -> throw new IllegalArgumentException(name);
        }
        return new Range(local(first), local(after));
    }

    public static Range today() {
        return period("today", ZonedDateTime.now(LAGOS));
    }

    // The hour of the day, in Lagos, of a time stamped in the server's zone.
    public static int hourInLagos(LocalDateTime stamp) {
        return stamp.atZone(ZoneId.systemDefault()).withZoneSameInstant(LAGOS).getHour();
    }

    private static LocalDateTime local(LocalDate lagosMidnight) {
        return lagosMidnight.atStartOfDay(LAGOS).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }
}
