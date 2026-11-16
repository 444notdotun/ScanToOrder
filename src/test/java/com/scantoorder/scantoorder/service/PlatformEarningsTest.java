package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.Event;
import com.scantoorder.scantoorder.data.model.EventPayment;
import com.scantoorder.scantoorder.data.model.EventSeatingMode;
import com.scantoorder.scantoorder.data.model.Item;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.Tenant;
import com.scantoorder.scantoorder.data.model.WorkerPrincipal;
import com.scantoorder.scantoorder.data.repository.EventPaymentRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.dtos.respond.PlatformEarningsResponse;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.utils.LagosTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PlatformEarningsTest {
    @Autowired
    private PlatformEarningsService earnings;
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private EventPaymentRepo eventPaymentRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private PaymentTestSupport pay;
    @Autowired
    private EventTestSupport events;
    @Autowired
    private WebApplicationContext wac;

    private Tenant restaurant;
    private Item plate;

    @BeforeEach
    void setUp() {
        restaurant = tenants.freshActiveTenant();
        plate = pay.item(restaurant, "Earnings plate", 2000, true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Payment sale(long chargeKobo, PaymentStatus status, LocalDateTime paidAt) {
        Payment payment = pay.payment(pay.order(restaurant, OrderStatus.PAID, plate, 1), status);
        payment.setPlatelyChargeKobo(chargeKobo);
        payment.setPaidAt(paidAt);
        return paymentRepo.saveAndFlush(payment);
    }

    @Test
    void testThatChargesAndEventFeesAreAddedUpForThePeriodOnly() {
        ZonedDateTime now = ZonedDateTime.now(LagosTime.LAGOS);
        LocalDateTime inside = LocalDateTime.now();
        sale(4000, PaymentStatus.SUCCESSFUL, inside);
        sale(1500, PaymentStatus.SUCCESSFUL, inside);
        sale(9999, PaymentStatus.SUCCESSFUL, inside.minusMonths(3));
        sale(7777, PaymentStatus.FAILED, inside);
        Tenant planner = events.planner();
        Event event = events.activeEvent(planner, EventSeatingMode.FREE, 2, 10);
        EventPayment fee = new EventPayment();
        fee.setTenant(planner);
        fee.setEvent(event);
        fee.setGuests(10);
        fee.setAmountKobo(500_000);
        fee.setReference("EVENT-EARN0001");
        fee.setStatus(PaymentStatus.SUCCESSFUL);
        eventPaymentRepo.saveAndFlush(fee);

        PlatformEarningsResponse month = earnings.earnings("month", now);

        assertTrue(month.getChargesKobo() >= 5500);
        assertTrue(month.getEventFeesKobo() >= 500_000);
        assertEquals(month.getChargesKobo() + month.getEventFeesKobo(), month.getTotalKobo());
        assertTrue(month.getByRestaurant().stream().anyMatch(r -> r.getName().equals(restaurant.getName()) && r.getChargeKobo() == 5500));
        assertTrue(month.getEventFees().stream().anyMatch(f -> f.getAmountKobo() == 500_000 && f.getGuests() == 10 && f.getEventName().equals(event.getName())));
        assertTrue(month.getFailedPayments() >= 1);
        assertTrue(month.getTotalBusinesses() >= month.getLiveBusinesses());
    }

    @Test
    void testThatAnUnknownPeriodIsRefusedInPlainWords() {
        InvalidRequestException refused = assertThrows(InvalidRequestException.class, () -> earnings.earnings("decade", ZonedDateTime.now(LagosTime.LAGOS)));
        assertEquals("Choose today, week or month.", refused.getMessage());
    }

    @Test
    void testThatAMonthOldSaleIsNotInThisWeeksFigures() {
        ZonedDateTime now = ZonedDateTime.now(LagosTime.LAGOS);
        sale(3333, PaymentStatus.SUCCESSFUL, LocalDateTime.now().minusDays(40));

        PlatformEarningsResponse week = earnings.earnings("week", now);

        assertTrue(week.getByRestaurant().stream().noneMatch(r -> r.getName().equals(restaurant.getName())));
    }

    private int statusFor(String role) throws Exception {
        WorkerPrincipal principal = new WorkerPrincipal("w-" + role, role, restaurant.getTenantId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        return mockMvc.perform(MockMvcRequestBuilders.request(HttpMethod.GET, "/api/v1/platform/earnings")).andReturn().getResponse().getStatus();
    }

    @Test
    void testThatOnlyThePlatformOwnerMayReadTheEarnings() throws Exception {
        assertEquals(200, statusFor("PLATFORM_ADMIN"));
        assertEquals(403, statusFor("MANAGER"));
        assertEquals(403, statusFor("WAITER"));
    }
}
