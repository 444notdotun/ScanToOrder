package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// The manager's figures for a period. Money is in whole kobo. Everything is added up from records.
@Data
public class InsightsResponse {
    private String period;
    // False when there is no paid order in the period: the screen says the figures appear after the first one.
    private boolean hasData;
    private long salesKobo;
    private long orders;
    private long platelyChargeKobo;
    private long receivedKobo;
    private long refundsKobo;
    // Paid orders the kitchen could not make as ordered.
    private long ordersKitchenCouldNotMake;
    private List<BestPlate> bestPlates = new ArrayList<>();
    // Orders by hour of the day in Lagos, 0 to 23.
    private List<HourCount> busiestHours = new ArrayList<>();
    private long averageOrderKobo;
    private Double paidToDeliveredMinutes;
    private long guestsSeated;
    private long seatsHeld;
    private long seatsHeldTakenUp;
    private long waiterCalls;
    private Double callToTakenMinutes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BestPlate {
        private String name;
        private long quantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourCount {
        private int hour;
        private long orders;
    }
}
