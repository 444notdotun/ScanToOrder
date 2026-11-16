package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.service.Interface.DiningSessionService;
import com.scantoorder.scantoorder.service.Interface.EventService;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class ScheduledJobs {

    @Autowired
    private OrderRepo orderRepo;


    @Autowired
    private DiningSessionService diningSessionService;
    @Autowired
    private EventService eventService;

    @Scheduled(fixedRate = 60_000)
    public void expireHolds() {
        int released = diningSessionService.expireHolds();
        if (released > 0) {
            log.info("Released {} expired seat holds", released);
        }
    }

    // Events end by themselves at their end time: codes stop working and their tables are closed.
    @Scheduled(fixedRate = 60_000)
    public void endFinishedEvents() {
        int ended = eventService.endFinishedEvents();
        if (ended > 0) {
            log.info("Ended {} events that reached their end time", ended);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void closeSessionsAtEndOfDay() {
        int closed = diningSessionService.closeAllActiveSessions();
        log.info("Closed {} sessions still active at end of day", closed);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupAbandonedOrders() {
        log.info("Running Midnight Abandoned Order Cleanup");
        List<Order> pendingOrders = orderRepo.findByOrderStatus(OrderStatus.PENDING_PAYMENT);
        for (Order order : pendingOrders) {
            order.setOrderStatus(OrderStatus.CANCELED);
        }
        if (!pendingOrders.isEmpty()) {
            orderRepo.saveAll(pendingOrders);
            log.info("Canceled {} abandoned orders", pendingOrders.size());
        }
    }


    @Scheduled(cron = "0 0 * * * ?")
    public void autoCompleteStaleOrders() {
        log.info("Running Stale READY Auto-Complete");
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(90);
        List<Order> staleOrders = orderRepo.findByOrderStatusAndUpdatedAtBefore(OrderStatus.READY, threshold);
        for (Order order : staleOrders) {
            order.setOrderStatus(OrderStatus.DELIVERED);
        }
        if (!staleOrders.isEmpty()) {
            orderRepo.saveAll(staleOrders);
            log.info("Auto-completed {} stale READY orders", staleOrders.size());
        }
    }
}
