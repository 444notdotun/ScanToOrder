package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.EventPayment;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.Refund;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.TenantStatus;
import com.scantoorder.scantoorder.data.repository.EventPaymentRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.RefundRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.respond.PlatformEarningsResponse;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.utils.LagosTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Plately earns, added up from records: the charge recorded on each restaurant payment and the fee on each paid event.
 */
@Service
public class PlatformEarningsService {

    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private EventPaymentRepo eventPaymentRepo;
    @Autowired
    private RefundRepo refundRepo;
    @Autowired
    private TenantRepo tenantRepo;

    @Transactional(readOnly = true)
    public PlatformEarningsResponse earnings(String period) {
        return earnings(period, ZonedDateTime.now(LagosTime.LAGOS));
    }

    // The clock is a parameter so a week can be checked on a known day.
    @Transactional(readOnly = true)
    public PlatformEarningsResponse earnings(String period, ZonedDateTime now) {
        String name = period == null || period.isBlank() ? "month" : period.trim().toLowerCase();
        LagosTime.Range range;
        try {
            range = LagosTime.period(name, now);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Choose today, week or month.");
        }

        PlatformEarningsResponse response = new PlatformEarningsResponse();
        response.setPeriod(name);

        Map<String, Long> byRestaurant = new LinkedHashMap<>();
        long charges = 0;
        for (Payment payment : paymentRepo.findByStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(PaymentStatus.SUCCESSFUL, range.from(), range.to())) {
            long charge = payment.getPlatelyChargeKobo() == null ? 0 : payment.getPlatelyChargeKobo();
            charges += charge;
            byRestaurant.merge(payment.getTenant().getName(), charge, Long::sum);
        }
        byRestaurant.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> response.getByRestaurant().add(new PlatformEarningsResponse.TenantCharge(entry.getKey(), entry.getValue())));

        long fees = 0;
        for (EventPayment fee : eventPaymentRepo.findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(PaymentStatus.SUCCESSFUL, range.from(), range.to())) {
            fees += fee.getAmountKobo();
            response.getEventFees().add(new PlatformEarningsResponse.EventFee(fee.getEvent().getName(), fee.getTenant().getName(), fee.getGuests(), fee.getAmountKobo()));
        }
        response.setChargesKobo(charges);
        response.setEventFeesKobo(fees);
        response.setTotalKobo(charges + fees);

        List<Tenant> everyone = tenantRepo.findAllByOrderByCreatedAtDesc();
        response.setTotalBusinesses(everyone.size());
        response.setLiveBusinesses(everyone.stream().filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE).count());

        response.setFailedPayments(paymentRepo.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(PaymentStatus.FAILED, range.from(), range.to()));
        List<Refund> refunds = refundRepo.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(range.from(), range.to());
        response.setRefundsCount(refunds.size());
        response.setRefundsKobo(refunds.stream().mapToLong(Refund::getAmountKobo).sum());
        return response;
    }
}
