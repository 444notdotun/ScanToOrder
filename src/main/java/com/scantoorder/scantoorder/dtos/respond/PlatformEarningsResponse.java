package com.scantoorder.scantoorder.dtos.respond;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// What Plately earned in a period: the charge on restaurant sales and the fee for events. Money is in whole kobo.
@Data
public class PlatformEarningsResponse {
    private String period;
    private long chargesKobo;
    private long eventFeesKobo;
    private long totalKobo;
    private long liveBusinesses;
    private long totalBusinesses;
    // Charges by restaurant, biggest first.
    private List<TenantCharge> byRestaurant = new ArrayList<>();
    private List<EventFee> eventFees = new ArrayList<>();
    private long failedPayments;
    private long refundsKobo;
    private long refundsCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TenantCharge {
        private String name;
        private long chargeKobo;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventFee {
        private String eventName;
        private String plannerName;
        private int guests;
        private long amountKobo;
    }
}
