package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.data.model.*;
import com.scantoorder.scantoorder.data.repository.EventPaymentRepo;
import com.scantoorder.scantoorder.data.repository.EventRepo;
import com.scantoorder.scantoorder.data.repository.TenantRepo;
import com.scantoorder.scantoorder.dtos.request.SignupRequest;
import com.scantoorder.scantoorder.dtos.respond.BankData;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaystackInitResponseData;
import com.scantoorder.scantoorder.exception.EmailSendException;
import com.scantoorder.scantoorder.exception.InvalidRequestException;
import com.scantoorder.scantoorder.exception.ResourceNotFoundException;
import com.scantoorder.scantoorder.exception.TryAgainLaterException;
import com.scantoorder.scantoorder.service.Interface.EventService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import com.scantoorder.scantoorder.service.Interface.PlatformService;
import com.scantoorder.scantoorder.service.Interface.ReceiptService;
import com.scantoorder.scantoorder.service.Interface.SignupService;
import com.scantoorder.scantoorder.service.email.EmailMessage;
import com.scantoorder.scantoorder.service.email.InMemoryEmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// The emails added with SendByte: a new sign-up, a business paused and back, an event live, and a receipt the guest asks for.
// These tests really commit, because each email goes out only once its change is saved.
@SpringBootTest(properties = "app.platform-owner.email=owner@plately.test")
class NewEmailsTest {

    @Autowired
    private SignupService signupService;
    @Autowired
    private PlatformService platformService;
    @Autowired
    private EventService events;
    @Autowired
    private EventPaymentService eventPayments;
    @Autowired
    private WebhookProcessor webhooks;
    @Autowired
    private ReceiptService receipts;
    @Autowired
    private TenantRepo tenantRepo;
    @Autowired
    private EventRepo eventRepo;
    @Autowired
    private EventPaymentRepo eventPaymentRepo;
    @Autowired
    private TenantTestSupport tenants;
    @Autowired
    private EventTestSupport eventSupport;
    @Autowired
    private PaymentTestSupport payments;
    @Autowired
    private com.scantoorder.scantoorder.data.repository.TableRepo tableRepo;
    @Autowired
    private com.scantoorder.scantoorder.data.repository.OrderRepo orderRepo;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private SignupService signup;
    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactions;
    @MockitoSpyBean
    private InMemoryEmailSender sender;
    @MockitoBean
    private PaystackClient paystackClient;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("signup:banks");
        when(paystackClient.listBanks()).thenReturn(List.of(new BankData("Guaranty Trust Bank", "058")));
        when(paystackClient.resolveAccountName("058", "0123456789")).thenReturn("THE NEW PLACE LTD");
        when(paystackClient.createSubaccount(anyString(), anyString(), anyString(), any())).thenReturn("ACCT_approved001");
        when(paystackClient.initialize(anyString(), anyLong(), anyString(), any()))
                .thenAnswer(invocation -> new PaystackInitResponseData("https://checkout.example/event", invocation.getArgument(2)));
        Set<String> keys = redis.keys("limit:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        sender.clear();
        reset(sender);
    }

    @AfterEach
    void tearDown() {
        reset(sender);
        sender.clear();
    }

    private List<EmailMessage> sent() {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(sender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    private SignupRequest restaurant(String name) {
        SignupRequest request = new SignupRequest();
        request.setType(TenantType.RESTAURANT);
        request.setBusinessName(name);
        request.setOwnerName("Ngozi <Eze>");
        request.setEmail(UUID.randomUUID().toString().substring(0, 12) + "@example.com");
        request.setPhone("08012345678");
        request.setBankCode("058");
        request.setAccountNumber("0123456789");
        request.setAccountName("THE NEW PLACE LTD");
        return request;
    }

    // ---- a new business signs up: the platform owner is told

    @Test
    void testThatThePlatformOwnerHearsAboutANewSignUpOnceAndTheBusinessGetsNothing() {
        String name = tenants.uniqueName("Mama <b>Put</b> & Sons");
        SignupRequest request = restaurant(name);

        signupService.signup(request);

        List<EmailMessage> mail = sent();
        assertEquals(1, mail.size());
        EmailMessage message = mail.get(0);
        assertEquals("owner@plately.test", message.to());
        assertEquals("New on Plately: " + name, message.subject());
        assertEquals("new-signup", message.tag());
        assertTrue(message.text().contains("Type: Restaurant"));
        assertTrue(message.text().contains("Owner: Ngozi <Eze>"));
        assertTrue(message.text().contains("Email: " + request.getEmail().toLowerCase()));
        assertTrue(message.text().contains("Phone: 08012345678"));
        assertTrue(message.text().contains("/platform"));
        assertTrue(message.html().contains("Sent to the platform owner only."));
        assertTrue(message.html().contains("Ngozi &lt;Eze&gt;"), "names are escaped in the html");
        assertFalse(message.html().contains("<b>Put</b>"));
        assertTrue(message.html().contains("&lt;b&gt;Put&lt;/b&gt; &amp; Sons"));
        assertNotNull(message.idempotencyKey());
    }

    @Test
    void testThatNoEmailGoesWhenNoPlatformOwnerIsSet() {
        ReflectionTestUtils.setField(signup, "platformOwnerEmail", "");
        try {
            signupService.signup(restaurant(tenants.uniqueName("Quiet")));
        } finally {
            ReflectionTestUtils.setField(signup, "platformOwnerEmail", "owner@plately.test");
        }

        assertTrue(sent().isEmpty());
    }

    @Test
    void testThatASignUpIsNeverFailedByAnEmailThatCannotBeSent() {
        doThrow(new EmailSendException("down")).when(sender).send(any(EmailMessage.class));
        String name = tenants.uniqueName("Still Saved");

        assertDoesNotThrow(() -> signupService.signup(restaurant(name)));

        assertTrue(tenantRepo.findBySlug(name.toLowerCase().replaceAll("[^a-z0-9]", "")).isPresent());
    }

    @Test
    void testThatARefusedSignUpSendsNothing() {
        SignupRequest request = restaurant(tenants.uniqueName("Twice"));
        signupService.signup(request);
        sender.clear();
        reset(sender);

        assertThrows(RuntimeException.class, () -> signupService.signup(request));

        assertTrue(sent().isEmpty(), "the second try was refused, so the owner is not told again");
    }

    // ---- a business is paused and comes back

    private Tenant activeBusiness() {
        Tenant tenant = tenants.pendingTenant("Pause");
        platformService.approve(tenant.getTenantId());
        sender.clear();
        reset(sender);
        return tenantRepo.findById(tenant.getTenantId()).orElseThrow();
    }

    @Test
    void testThatSuspendingTellsTheBusinessInFixedWordsWithNoReason() {
        Tenant tenant = activeBusiness();

        platformService.suspend(tenant.getTenantId());

        List<EmailMessage> mail = sent();
        assertEquals(1, mail.size());
        EmailMessage message = mail.get(0);
        assertEquals(tenant.getEmail(), message.to());
        assertEquals("Your Plately account is paused", message.subject());
        assertEquals("suspended", message.tag());
        assertTrue(message.text().contains(tenant.getName() + " is paused on Plately. Guests can't order and your team can't sign in for now. Reply to this email and we'll sort it out with you."));
        assertFalse(message.text().toLowerCase().contains("reason"));
    }

    @Test
    void testThatReinstatingTellsTheBusinessItIsBackWithASignInLink() {
        Tenant tenant = activeBusiness();
        platformService.suspend(tenant.getTenantId());
        sender.clear();
        reset(sender);

        platformService.reinstate(tenant.getTenantId());

        List<EmailMessage> mail = sent();
        assertEquals(1, mail.size());
        EmailMessage message = mail.get(0);
        assertEquals(tenant.getEmail(), message.to());
        assertEquals("Your Plately account is back", message.subject());
        assertEquals("reinstated", message.tag());
        assertTrue(message.text().contains(tenant.getName() + " is back on Plately. Your team can sign in and guests can order again."));
        assertTrue(message.text().contains("Sign in:\nhttp://localhost:3000/staff/login"));
    }

    @Test
    void testThatApprovingAndDecliningSendOnlyTheirOwnEmails() {
        Tenant approved = tenants.pendingTenant("Approve");
        Tenant declined = tenants.pendingTenant("Decline");

        platformService.approve(approved.getTenantId());
        platformService.decline(declined.getTenantId(), "The account name does not match");

        List<EmailMessage> mail = sent();
        assertEquals(2, mail.size());
        assertEquals(List.of("sign-in", "decline"), mail.stream().map(EmailMessage::tag).toList());
        EmailMessage decline = mail.get(1);
        assertTrue(decline.text().contains("Reason: The account name does not match"));
        assertTrue(decline.text().contains("Send the form again:\nhttp://localhost:3000/signup"));
    }

    // ---- an event goes live

    private Tenant plannerBusiness() {
        String slug = "live" + UUID.randomUUID().toString().substring(0, 8);
        Tenant planner = tenants.tenant(slug, "Live Planner " + slug);
        planner.setType(TenantType.EVENT_PLANNER);
        planner.setOpenForOrders(false);
        planner.setPaystackSubaccountCode(null);
        return tenantRepo.save(planner);
    }

    private EventPayment pay(Tenant planner, String eventId, int guests) {
        InitializePaymentResponse response = events.startPayment(planner.getTenantId(), eventId, guests);
        return transactions.execute(status -> eventPaymentRepo.findForUpdateByReference(response.getReference()).orElseThrow());
    }

    private static String charge(EventPayment payment) {
        return "{\"event\":\"charge.success\",\"data\":{\"reference\":\"" + payment.getReference() + "\",\"amount\":" + payment.getAmountKobo() + "}}";
    }

    @Test
    void testThatThePlannerHearsOnceWhenTheFirstPaymentMakesTheEventLiveAndNotOnATopUp() {
        Tenant planner = plannerBusiness();
        String id = eventSupport.draft(planner, EventSeatingMode.FREE, null).getId();
        assertTrue(sent().isEmpty(), "a draft sends nothing");

        EventPayment first = pay(planner, id, 100);
        webhooks.process(charge(first));

        List<EmailMessage> mail = sent();
        assertEquals(1, mail.size());
        EmailMessage message = mail.get(0);
        assertEquals(planner.getEmail(), message.to());
        assertEquals("Test Wedding is live", message.subject());
        assertEquals("event-live", message.tag());
        assertEquals("event-live:" + id, message.idempotencyKey());
        assertTrue(message.text().contains("Venue: The Hall"));
        assertTrue(message.text().contains("Guests covered: 100"));
        assertTrue(message.text().contains("Event code: " + eventRepo.findById(id).orElseThrow().getEventCode()));
        assertTrue(message.text().contains("http://localhost:3000/planner/" + id));

        webhooks.process(charge(first));
        EventPayment topUp = pay(planner, id, 7);
        webhooks.process(charge(topUp));

        assertEquals(1, sent().size(), "a repeat notice and a top-up add no second email");
        assertEquals(107, eventRepo.findById(id).orElseThrow().getPaidGuests());
    }

    @Test
    void testThatAFailedPaymentDoesNotMakeAnEventLiveOrSendAnEmail() {
        Tenant planner = plannerBusiness();
        String id = eventSupport.draft(planner, EventSeatingMode.FREE, null).getId();
        EventPayment payment = pay(planner, id, 10);

        webhooks.process("{\"event\":\"charge.failed\",\"data\":{\"reference\":\"" + payment.getReference() + "\",\"amount\":" + payment.getAmountKobo() + "}}");

        assertTrue(sent().isEmpty());
        assertEquals(EventStatus.DRAFT, eventRepo.findById(id).orElseThrow().getStatus());
    }

    // ---- a receipt, only when the guest asks

    private Payment receiptFor(Tenant tenant, String itemName) {
        Item item = payments.item(tenant, itemName, 1500, true);
        return payments.paidOrder(tenant, OrderStatus.DELIVERED, item);
    }

    @Test
    void testThatTheReceiptIsEmailedOnlyOnRequestWithTheWordsOnTheBoard() {
        Tenant tenant = tenants.freshActiveTenant();
        Payment payment = receiptFor(tenant, "Jollof & <i>chicken</i>");
        assertTrue(sent().isEmpty(), "a payment alone sends no receipt");

        String answer = receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), " guest@example.com ", "10.5.5.5");

        assertEquals("Sent. Check your inbox in a minute.", answer);
        List<EmailMessage> mail = sent();
        assertEquals(1, mail.size());
        EmailMessage message = mail.get(0);
        assertEquals("guest@example.com", message.to());
        assertEquals("Your receipt from " + tenant.getName(), message.subject());
        assertEquals("receipt", message.tag());
        assertTrue(message.text().contains("Receipt: REC-" + payment.getReference()));
        assertTrue(message.text().contains("1 × Jollof & <i>chicken</i>  ₦1,500"));
        assertTrue(message.text().contains("Total  ₦1,500"));
        assertTrue(message.text().contains("Paid through Paystack"));
        // The email holds the whole receipt. The receipt page needs the guest's seat cookie, so no link is sent.
        assertFalse(message.text().contains("View receipt online"));
        assertFalse(message.text().contains("http"));
        assertFalse(message.html().contains("View receipt online"));
        assertFalse(message.html().contains("Button not working"));
        assertFalse(message.html().contains("<a href=\"http"));
        assertTrue(message.text().contains("Something wrong with your order? Reply to this email and we'll pass it to " + tenant.getName() + ".\n\n--\n"), "that is the last line before the footer");
        assertTrue(message.text().contains("Something wrong with your order? Reply to this email and we'll pass it to " + tenant.getName() + "."));
        assertTrue(message.html().contains("Jollof &amp; &lt;i&gt;chicken&lt;/i&gt;"));
        assertFalse(message.html().contains("<i>chicken</i>"));
        assertTrue(message.idempotencyKey().startsWith("receipt:" + payment.getReference() + ":guest@example.com:"));
    }

    @Test
    void testThatTheEmailNamesTheTableByTheNumberOnTheCardNotItsInternalCode() {
        Tenant tenant = tenants.freshActiveTenant();
        Payment payment = receiptFor(tenant, "Suya");
        com.scantoorder.scantoorder.data.model.RestaurantTable table = tableRepo.save(com.scantoorder.scantoorder.data.model.RestaurantTable.builder().tenant(tenant).displayNumber(7).Capacity(4).build());
        Order order = payment.getOrder();
        order.setTable(table);
        orderRepo.save(order);

        receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), "guest@example.com", "10.3.3.3");

        String text = sent().get(0).text();
        assertTrue(text.contains("Table: 7\n"));
        assertFalse(text.contains(table.getTableNumber()), "the internal table code is never shown");
    }

    @Test
    void testThatADoubleTapUsesTheSameKeySoItIsSentOnce() {
        Tenant tenant = tenants.freshActiveTenant();
        Payment payment = receiptFor(tenant, "Plantain");

        receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), "guest@example.com", "10.6.6.6");
        receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), "guest@example.com", "10.6.6.6");

        List<EmailMessage> mail = sent();
        assertEquals(2, mail.size());
        assertEquals(mail.get(0).idempotencyKey(), mail.get(1).idempotencyKey(), "the provider delivers a repeated key once");
    }

    @Test
    void testThatABadEmailIsRefusedInPlainWordsAndNothingIsSent() {
        Tenant tenant = tenants.freshActiveTenant();
        Payment payment = receiptFor(tenant, "Chapman");

        for (String bad : new String[]{"nope", "a@b", "two words@example.com", "", "  "}) {
            InvalidRequestException refused = assertThrows(InvalidRequestException.class,
                    () -> receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), bad, "10.7.7.7"));
            assertEquals("That email doesn't look right.", refused.getMessage());
        }
        assertTrue(sent().isEmpty());
    }

    @Test
    void testThatAReceiptIsEmailedAtMostThreeTimesAnHour() {
        Tenant tenant = tenants.freshActiveTenant();
        Payment payment = receiptFor(tenant, "Moi moi");

        for (int i = 0; i < 3; i++) {
            receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), "guest" + i + "@example.com", "10.8.8." + i);
        }
        TryAgainLaterException limited = assertThrows(TryAgainLaterException.class,
                () -> receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), "guest9@example.com", "10.8.8.9"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, limited.getStatus());
        assertEquals("That's a few receipts already. Try again in an hour.", limited.getMessage());
        assertEquals(3, sent().size());
    }

    @Test
    void testThatOneAddressCanAskForAtMostTwentyReceiptsAnHour() {
        Tenant tenant = tenants.freshActiveTenant();

        for (int i = 0; i < 20; i++) {
            Payment payment = receiptFor(tenant, "Dish " + i);
            receipts.emailReceipt(tenant.getTenantId(), payment.getReference(), "guest@example.com", "10.9.0.1");
        }
        Payment onemore = receiptFor(tenant, "One more");

        assertThrows(TryAgainLaterException.class, () -> receipts.emailReceipt(tenant.getTenantId(), onemore.getReference(), "guest@example.com", "10.9.0.1"));
        assertEquals(20, sent().size());
    }

    @Test
    void testThatSomeoneElsesReceiptIsNotFoundAndASendFailureIsAnHonestTryAgain() {
        Tenant mine = tenants.freshActiveTenant();
        Tenant theirs = tenants.freshActiveTenant();
        Payment theirPayment = receiptFor(theirs, "Theirs");
        Payment myPayment = receiptFor(mine, "Mine");

        assertThrows(ResourceNotFoundException.class, () -> receipts.emailReceipt(mine.getTenantId(), theirPayment.getReference(), "guest@example.com", "10.4.4.4"));

        doThrow(new EmailSendException("down")).when(sender).send(any(EmailMessage.class));
        TryAgainLaterException down = assertThrows(TryAgainLaterException.class,
                () -> receipts.emailReceipt(mine.getTenantId(), myPayment.getReference(), "guest@example.com", "10.4.4.5"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, down.getStatus());
        assertEquals("We couldn't send it just now. Try again in a minute.", down.getMessage());
        Mockito.reset(sender);
    }
}
