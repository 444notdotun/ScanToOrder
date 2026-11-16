package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Ready in about 14 min" on a dish, from what really happened: the median time from paid to delivered over the last
 * 30 days, and only for a dish with at least 5 delivered orders in that time. Anything thinner gets no figure at all.
 */
@Service
public class ReadyTimeService {

    public static final int DAYS = 30;
    public static final int MIN_ORDERS = 5;

    @Autowired
    private OrderItemRepo orderItemRepo;

    @Transactional(readOnly = true)
    public Map<String, Integer> byItemName(String tenantId) {
        return byItemName(tenantId, LocalDateTime.now());
    }

    // The clock is a parameter so the window can be checked on a known day.
    @Transactional(readOnly = true)
    public Map<String, Integer> byItemName(String tenantId, LocalDateTime now) {
        // One entry per order for each dish, so an order with two of the same dish counts once.
        Map<String, Map<String, Long>> secondsByDish = new HashMap<>();
        for (OrderItem line : orderItemRepo.findDeliveredSince(tenantId, now.minusDays(DAYS))) {
            long seconds = Duration.between(line.getOrder().getPaidAt(), line.getOrder().getDeliveredAt()).getSeconds();
            if (seconds < 0) continue;
            secondsByDish.computeIfAbsent(line.getItem().getItemName(), name -> new LinkedHashMap<>()).put(line.getOrder().getOrderId(), seconds);
        }
        Map<String, Integer> result = new HashMap<>();
        secondsByDish.forEach((dish, orders) -> {
            if (orders.size() < MIN_ORDERS) return;
            List<Long> sorted = new ArrayList<>(orders.values());
            Collections.sort(sorted);
            int middle = sorted.size() / 2;
            double median = sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
            result.put(dish, Math.max(1, (int) Math.round(median / 60.0)));
        });
        return result;
    }
}
