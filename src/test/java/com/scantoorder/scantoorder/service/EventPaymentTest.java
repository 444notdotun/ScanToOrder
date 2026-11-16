package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.EventPaymentRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.dtos.request.EventGuestRequest;
import com.scantoorder.scantoorder.dtos.respond.EventPriceResponse;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.dtos.respond.PaystackVerifyResponseData;
import com.scantoorder.scantoorder.exception.ConflictException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.service.Interface.EventService;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class EventPaymentTest {
    @Autowired
    private EventService events;
    @Autowired
    private EventGuestService guests;
    @Autowired
    private EventPricing pricing;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private WebhookProcessor webhooks;
    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventPaymentRepo paymentRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private EventTestSupport support;
    @Autowired
    private WebApplicationContext wac;
    @Value("${app.event.fee-per-guest-kobo}")
    private long fee;
    @MockitoBean
    private PaystackClient paystackClient;

    private Tenant planner;
    private String plannerId;

    @BeforeEach
    void setUp() {
        clearInvocations(paystackClient);
        planner = support.planner();
        plannerId = planner.getTenantId();
        when(paystackClient.initialize(anyString(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new PaystackInitResponseData("https://checkout.example/event", invocation.getArgument(2)));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String charge(String event, EventPayment payment, long amountKobo) {
        return "{\"event\":\"" + event + "\",\"data\":{\"reference\":\"" + payment.getReference() + "\",\"amount\":" + amountKobo + "}}";
    }

    private EventPayment pay(String eventId, int count) {
        InitializePaymentResponse response = events.startPayment(plannerId, eventId, count);
        return paymentRepo.findForUpdateByReference(response.getReference()).orElseThrow();
    }

    private Event reload(String eventId) {
        return eventRepo.findById(eventId).orElseThrow();
    }

    // ---- price

    @Test
    void testThatThePriceIsGuestsTimesTheFeeInWholeKobo() {
        String id = support.draft(planner, EventSeatingMode.ASSIGNED, 2).getId();

        EventPriceResponse price = events.price(plannerId, id, 120);

        assertEquals(120, price.getGuests());
        assertEquals(fee, price.getFeePerGuestKobo());
        assertEquals(120 * fee, price.getAmountKobo());
        assertEquals(120 * fee, price.getAmount().movePointRight(2).longValueExact());
        assertEquals(50000, fee, "the default fee is 500 naira");
        assertThrows(InvalidRequestException.class, () -> events.price(plannerId, id, 0));
        assertEquals(Math.multiplyExact(fee, 1000L), pricing.amountKobo(1000));
    }

    // ---- paying

    @Test
    void testThatPayingStartsAPaystackPaymentWithThePlannersEmailAndNoSplit() {
        String id = support.draft(planner, EventSeatingMode.ASSIGNED, 2).getId();

        InitializePaymentResponse response = events.startPayment(plannerId, id, 40);

        assertTrue(response.getReference().startsWith("EVENT-"));
        verify(paystackClient).initialize(eq(planner.getEmail()), eq(40 * fee), eq(response.getReference()), isNull());
        EventPayment saved = paymentRepo.findForUpdateByReference(response.getReference()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals(40, saved.getGuests());
        assertEquals(40 * fee, saved.getAmountKobo());
        assertEquals(plannerId, saved.getTenant().getTenantId());
        assertEquals(0, reload(id).getPaidGuests());
    }

    @Test
    void testThatTheReceiptGoesToTheEmailThePlannerGaveAndElseToTheBusinessEmail() {
        String id = support.draft(planner, EventSeatingMode.ASSIGNED, 2).getId();

        events.startPayment(plannerId, id, 10, "  receipts@real-mail.com ");
        events.startPayment(plannerId, id, 10, "   ");
        events.startPayment(plannerId, id, 10, null);

        verify(paystackClient).initialize(eq("receipts@real-mail.com"), anyLong(), anyString(), isNull());
        verify(paystackClient, times(2)).initialize(eq(planner.getEmail()), anyLong(), anyString(), isNull());
    }

    @Test
    void testThatADraftRefusesGuestsUntilItIsPaidFor() {
        Event draft = eventRepo.findById(support.draft(planner, EventSeatingMode.ASSIGNED, 2).getId()).orElseThrow();
        EventGuestRequest request = new EventGuestRequest();
        request.setName("Too Early");

        ConflictException refused = assertThrows(ConflictException.class, () -> guests.add(plannerId, draft.getId(), request));

        assertEquals("Pay for the event first. Until then it is a draft and codes cannot be made.", refused.getMessage());
        assertEquals(EventStatus.DRAFT, reload(draft.getId()).getStatus());
    }

    @Test
    void testThatTheFirstSuccessfulPaymentActivatesTheEventAndATopUpAddsToTheGuestsPaidFor() {
        String id = support.draft(planner, EventSeatingMode.FREE, null).getId();
        EventPayment first = pay(id, 100);

        webhooks.process(charge("charge.success", first, first.getAmountKobo()));

        assertEquals(EventStatus.ACTIVE, reload(id).getStatus());
        assertEquals(100, reload(id).getPaidGuests());
        assertEquals(PaymentStatus.SUCCESSFUL, paymentRepo.findForUpdateByReference(first.getReference()).orElseThrow().getStatus());

        EventPayment topUp = pay(id, 7);
        webhooks.process(charge("charge.success", topUp, topUp.getAmountKobo()));

        assertEquals(107, reload(id).getPaidGuests());
        assertEquals(EventStatus.ACTIVE, reload(id).getStatus());
    }

    @Test
    void testThatARepeatedNoticeAddsGuestsOnlyOnce() {
        String id = support.draft(planner, EventSeatingMode.FREE, null).getId();
        EventPayment payment = pay(id, 25);

        webhooks.process(charge("charge.success", payment, payment.getAmountKobo()));
        webhooks.process(charge("charge.success", payment, payment.getAmountKobo()));
        when(paystackClient.verify(payment.getReference()))
                .thenReturn(new PaystackVerifyResponseData("success", payment.getAmountKobo(), payment.getReference()));
        PaymentStatusResponse verified = paymentService.verifyAndSyncPayment(plannerId, payment.getReference());

        assertEquals(25, reload(id).getPaidGuests());
        assertEquals(PaymentStatus.SUCCESSFUL, verified.getPaymentStatus());
        verify(paystackClient, never()).verify(anyString());
    }

    @Test
    void testThatTheVerifyPathSettlesAPaymentAndTheWebhookAfterItAddsNothing() {
        String id = support.draft(planner, EventSeatingMode.FREE, null).getId();
        EventPayment payment = pay(id, 30);
        when(paystackClient.verify(payment.getReference()))
                .thenReturn(new PaystackVerifyResponseData("success", payment.getAmountKobo(), payment.getReference()));

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(plannerId, payment.getReference());
        webhooks.process(charge("charge.success", payment, payment.getAmountKobo()));

        assertEquals(PaymentStatus.SUCCESSFUL, response.getPaymentStatus());
        assertEquals(30, reload(id).getPaidGuests());
        assertEquals(EventStatus.ACTIVE, reload(id).getStatus());
    }

    @Test
    void testThatAWrongAmountOrAFailedNoticeAddsNothing() {
        String id = support.draft(planner, EventSeatingMode.FREE, null).getId();
        EventPayment payment = pay(id, 10);

        webhooks.process(charge("charge.success", payment, 100));
        assertEquals(0, reload(id).getPaidGuests());
        assertEquals(EventStatus.DRAFT, reload(id).getStatus());

        webhooks.process(charge("charge.failed", payment, payment.getAmountKobo()));
        assertEquals(PaymentStatus.FAILED, paymentRepo.findForUpdateByReference(payment.getReference()).orElseThrow().getStatus());
        assertEquals(0, reload(id).getPaidGuests());
    }

    @Test
    void testThatAnotherPlannersEventAnswersLikeAMissingOneForEveryAction() {
        Tenant other = support.otherPlanner();
        String theirs = support.draft(other, EventSeatingMode.FREE, null).getId();
        EventPayment theirPayment = events.startPayment(other.getTenantId(), theirs, 5) == null ? null
                : paymentRepo.findAll().stream().filter(p -> p.getEvent().getId().equals(theirs)).findFirst().orElseThrow();

        assertThrows(ResourceNotFoundException.class, () -> events.get(plannerId, theirs));
        assertThrows(ResourceNotFoundException.class, () -> events.update(plannerId, theirs, support.request(EventSeatingMode.FREE, null)));
        assertThrows(ResourceNotFoundException.class, () -> events.end(plannerId, theirs));
        assertThrows(ResourceNotFoundException.class, () -> events.price(plannerId, theirs, 3));
        assertThrows(ResourceNotFoundException.class, () -> events.startPayment(plannerId, theirs, 3));
        assertThrows(ResourceNotFoundException.class, () -> events.get(plannerId, "no-such-event"));
        assertThrows(ResourceNotFoundException.class, () -> paymentService.verifyAndSyncPayment(plannerId, theirPayment.getReference()));
        assertTrue(events.list(plannerId).stream().noneMatch(e -> e.getId().equals(theirs)));
    }

    @Test
    void testThatAnEndedEventTakesNoPaymentAndARestaurantCannotRunEvents() {
        String id = support.draft(planner, EventSeatingMode.FREE, null).getId();
        events.end(plannerId, id);
        assertThrows(ConflictException.class, () -> events.startPayment(plannerId, id, 5));

        Tenant restaurant = tenants.main();
        ConflictException refused = assertThrows(ConflictException.class,
                () -> events.create(restaurant.getTenantId(), support.request(EventSeatingMode.FREE, null)));
        assertEquals(EventServiceImpl.ONLY_PLANNERS, refused.getMessage());
    }

    @Test
    void testThatTimesAndSeatingAreCheckedAndSeatingCannotChangeOnceThereAreGuests() {
        var bad = support.request(EventSeatingMode.FREE, null);
        bad.setEndsAt(bad.getStartsAt().minusMinutes(1));
        assertThrows(InvalidRequestException.class, () -> events.create(plannerId, bad));
        var past = support.request(EventSeatingMode.FREE, null);
        past.setStartsAt(LocalDateTime.now().minusDays(2));
        past.setEndsAt(LocalDateTime.now().minusDays(1));
        assertThrows(InvalidRequestException.class, () -> events.create(plannerId, past));

        Event event = support.activeEvent(planner, EventSeatingMode.FREE, null, 3);
        support.guest(planner, event, "Someone");
        assertThrows(ConflictException.class, () -> events.update(plannerId, event.getId(), support.request(EventSeatingMode.ASSIGNED, null)));
        var rename = support.request(EventSeatingMode.FREE, 3);
        rename.setName("Renamed");
        assertEquals("Renamed", events.update(plannerId, event.getId(), rename).getName());
    }

    // ---- roles

    private void signIn(String role) {
        Object principal = role.equals("CUSTOMER")
                ? new CustomerPrincipal("s", "seat", "table", "member", plannerId)
                : new WorkerPrincipal("worker-" + role, role, role.equals("PLATFORM_ADMIN") ? null : plannerId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void testThatOnlyThePlannersManagerCreatesPricesAndPaysForEvents() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        String id = support.draft(planner, EventSeatingMode.FREE, null).getId();
        String body = "{\"name\":\"Gala\",\"startsAt\":\"" + LocalDateTime.now().minusHours(1) + "\",\"endsAt\":\"" + LocalDateTime.now().plusHours(4)
                + "\",\"seatingMode\":\"FREE\",\"expectedGuests\":5}";

        for (String role : List.of("CHEF", "WAITER", "CUSTOMER", "PLATFORM_ADMIN")) {
            signIn(role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(), role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/events/{id}/price", id).param("guests", "5")).andReturn().getResponse().getStatus(), role);
            assertEquals(403, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/events/{id}/pay", id).contentType(MediaType.APPLICATION_JSON).content("{\"guests\":5}")).andReturn().getResponse().getStatus(), role);
        }
        signIn("MANAGER");
        assertEquals(201, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/events/{id}/price", id).param("guests", "5")).andReturn().getResponse().getStatus());
        assertEquals(400, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/events/{id}/price", id).param("guests", "0")).andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/events/{id}/pay", id).contentType(MediaType.APPLICATION_JSON).content("{\"guests\":5}")).andReturn().getResponse().getStatus());
        // Staff of the planner may look at its events.
        signIn("WAITER");
        assertEquals(200, mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/events/{id}", id)).andReturn().getResponse().getStatus());
    }
}
