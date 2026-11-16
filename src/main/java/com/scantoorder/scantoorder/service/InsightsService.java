package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderItem;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.Refund;
import com.scantoorder.scantoorder.data.model.RefundStatus;
import com.scantoorder.scantoorder.data.model.ServiceCall;
import com.scantoorder.scantoorder.data.model.SessionSeat;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantType;
import com.scantoorder.scantoorder.data.repository.OrderItemRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.data.repository.ServiceCallRepo;
import com.scantoorder.scantoorder.data.repository.SessionSeatRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.respond.InsightsResponse;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.utils.LagosTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The manager's figures. Every number is added up from records; nothing is estimated.
 */
@Service
public class InsightsService {

    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderItemRepo orderItemRepo;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private SessionSeatRepo sessionSeatRepo;
    @Autowired
    private ServiceCallRepo callRepo;

    @Transactional(readOnly = true)
    public InsightsResponse insights(String tenantId, String period) {
        return insights(tenantId, period, ZonedDateTime.now(LagosTime.LAGOS));
    }

    // The clock is a parameter so a week can be checked on a known day.
    @Transactional(readOnly = true)
    public InsightsResponse insights(String tenantId, String period, ZonedDateTime now) {
        Tenant tenant = tenantRepo.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("We cannot find your business"));
        if (tenant.getType() != TenantType.RESTAURANT) {
            throw new AccessDeniedException("Insights are for restaurants");
        }
        String name = period == null || period.isBlank() ? "today" : period.trim().toLowerCase();
        LagosTime.Range range;
        try {
            range = LagosTime.period(name, now);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Choose today, week or month.");
        }

        InsightsResponse response = new InsightsResponse();
        response.setPeriod(name);
        List<Payment> payments = paymentRepo.findByTenant_TenantIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
                tenantId, PaymentStatus.SUCCESSFUL, range.from(), range.to());
        if (payments.isEmpty()) {
            response.setHasData(false);
            return response;
        }
        response.setHasData(true);

        // Sales and the split.
        long sales = payments.stream().mapToLong(this::kobo).sum();
        long charge = payments.stream().mapToLong(p -> p.getPlatelyChargeKobo() == null ? 0 : p.getPlatelyChargeKobo()).sum();
        Map<String, Order> orders = new LinkedHashMap<>();
        for (Payment payment : payments) {
            orders.putIfAbsent(payment.getOrder().getOrderId(), payment.getOrder());
        }
        long refunds = refundRepo.findByTenant_TenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(refund -> refund.getStatus() == RefundStatus.SETTLED || refund.getStatus() == RefundStatus.CONFIRMED)
                .filter(refund -> within(refundedAt(refund), range))
                .mapToLong(Refund::getAmountKobo).sum();
        response.setSalesKobo(sales);
        response.setOrders(orders.size());
        response.setPlatelyChargeKobo(charge);
        response.setRefundsKobo(refunds);
        response.setReceivedKobo(sales - charge - refunds);
        response.setOrdersKitchenCouldNotMake(orders.values().stream().filter(o -> o.getUnavailableReason() != null).count());
        response.setAverageOrderKobo(BigDecimal.valueOf(sales).divide(BigDecimal.valueOf(orders.size()), 0, RoundingMode.HALF_UP).longValue());

        // Best plates and busiest hours.
        Map<String, Long> byPlate = new LinkedHashMap<>();
        for (String orderId : orders.keySet()) {
            for (OrderItem line : orderItemRepo.findByOrder_OrderId(orderId)) {
                if (line.isSwappedOut() || line.getItem() == null) continue;
                byPlate.merge(line.getItem().getItemName(), (long) line.getQuantity(), Long::sum);
            }
        }
        byPlate.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5)
                .forEach(entry -> response.getBestPlates().add(new InsightsResponse.BestPlate(entry.getKey(), entry.getValue())));
        long[] hours = new long[24];
        for (Order order : orders.values()) {
            if (order.getPaidAt() != null) hours[LagosTime.hourInLagos(order.getPaidAt())]++;
        }
        for (int hour = 0; hour < 24; hour++) {
            response.getBusiestHours().add(new InsightsResponse.HourCount(hour, hours[hour]));
        }

        // Service.
        response.setPaidToDeliveredMinutes(averageMinutes(orders.values().stream()
                .filter(o -> o.getPaidAt() != null && o.getDeliveredAt() != null)
                .map(o -> Duration.between(o.getPaidAt(), o.getDeliveredAt())).toList()));
        List<SessionSeat> seated = sessionSeatRepo.findByTenant_TenantIdAndClaimedAtGreaterThanEqualAndClaimedAtLessThan(tenantId, range.from(), range.to());
        response.setGuestsSeated(seated.size());
        List<SessionSeat> held = sessionSeatRepo.findByTenant_TenantIdAndHeldAtGreaterThanEqualAndHeldAtLessThan(tenantId, range.from(), range.to());
        response.setSeatsHeld(held.size());
        response.setSeatsHeldTakenUp(held.stream().filter(seat -> seat.getClaimedAt() != null).count());
        List<ServiceCall> calls = callRepo.findByTenant_TenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(tenantId, range.from(), range.to());
        response.setWaiterCalls(calls.size());
        response.setCallToTakenMinutes(averageMinutes(calls.stream()
                .filter(c -> c.getTakenAt() != null && c.getCreatedAt() != null)
                .map(c -> Duration.between(c.getCreatedAt(), c.getTakenAt())).toList()));
        return response;
    }

    private long kobo(Payment payment) {
        return payment.getAmount().movePointRight(2).longValueExact();
    }

    private LocalDateTime refundedAt(Refund refund) {
        return refund.getSettledAt() != null ? refund.getSettledAt() : refund.getGuestConfirmedAt();
    }

    private boolean within(LocalDateTime when, LagosTime.Range range) {
        return when != null && !when.isBefore(range.from()) && when.isBefore(range.to());
    }

    // To one decimal place; null when there is nothing to average.
    private Double averageMinutes(List<Duration> durations) {
        if (durations.isEmpty()) return null;
        double seconds = durations.stream().mapToLong(Duration::getSeconds).average().orElse(0);
        return BigDecimal.valueOf(seconds / 60.0).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
