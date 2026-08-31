package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.InitializePaymentRequest;
import com.scantoorder.scantoorder.dtos.respond.InitializePaymentResponse;
import com.scantoorder.scantoorder.dtos.respond.PaymentStatusResponse;
import com.scantoorder.scantoorder.data.model.DinningSession;
import com.scantoorder.scantoorder.data.model.Order;
import com.scantoorder.scantoorder.data.model.OrderStatus;
import com.scantoorder.scantoorder.data.model.Payment;
import com.scantoorder.scantoorder.data.model.PaymentStatus;
import com.scantoorder.scantoorder.data.model.RestaurantTable;
import com.scantoorder.scantoorder.data.model.Seat;
import com.scantoorder.scantoorder.data.repository.DinningSessionRepo;
import com.scantoorder.scantoorder.data.repository.OrderRepo;
import com.scantoorder.scantoorder.data.repository.PaymentRepo;
import com.scantoorder.scantoorder.data.repository.SeatRepo;
import com.scantoorder.scantoorder.data.repository.TableRepo;
import com.scantoorder.scantoorder.exception.*;
import com.scantoorder.scantoorder.service.Interface.PaymentService;
import com.scantoorder.scantoorder.service.Interface.PaystackClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private TableRepo tableRepo;

    @Autowired
    private SeatRepo seatRepo;

    @Autowired
    private DinningSessionRepo dinningSessionRepo;

    @Autowired
    private PaystackClient paystackClient;

    private HttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        PaystackClientImpl target = AopTestUtils.getTargetObject(paystackClient);
        target.setHttpClient(mockHttpClient);
    }

    private Order createTestOrder(BigDecimal amount) {
        RestaurantTable table = new RestaurantTable();
        table.setCapacity(4);
        tableRepo.save(table);

        Seat seat = new Seat();
        seatRepo.save(seat);

        DinningSession dinningSession = new DinningSession();
        dinningSession.setCustomerPhone("08012345678");
        dinningSessionRepo.save(dinningSession);

        Order order = new Order();
        order.setTable(table);
        order.setSeat(seat);
        order.setDinningSession(dinningSession);
        order.setTotalAmount(amount);
        return orderRepo.save(order);
    }

    @Transactional
    @Test
    void testInitializePayment_Success() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(1500.00));

        String mockResponseBody = "{\n" +
                "  \"status\": true,\n" +
                "  \"message\": \"Authorization URL created\",\n" +
                "  \"data\": {\n" +
                "    \"authorization_url\": \"https://checkout.paystack.com/auth123\",\n" +
                "    \"reference\": \"ref123\"\n" +
                "  }\n" +
                "}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockHttpClient.send(any(), any())).thenAnswer(invocation -> mockResponse);

        InitializePaymentRequest request = new InitializePaymentRequest(order.getOrderId(), "user@example.com");
        InitializePaymentResponse response = paymentService.initializePayment(request);

        assertNotNull(response);
        assertNotNull(response.getReference());
        assertEquals("https://checkout.paystack.com/auth123", response.getAuthorizationUrl());

        Payment savedPayment = paymentRepo.findByReference(response.getReference()).orElse(null);
        assertNotNull(savedPayment);
        assertEquals(PaymentStatus.PENDING, savedPayment.getStatus());
        assertEquals(BigDecimal.valueOf(1500.00).setScale(2), savedPayment.getAmount().setScale(2));
        assertEquals(order.getOrderId(), savedPayment.getOrder().getOrderId());
    }

    @Transactional
    @Test
    void testInitializePayment_ThrowsIfAlreadyPaid() {
        Order order = createTestOrder(BigDecimal.valueOf(1500.00));
        order.setOrderStatus(OrderStatus.PAID);
        orderRepo.save(order);

        InitializePaymentRequest request = new InitializePaymentRequest(order.getOrderId(), "user@example.com");
        assertThrows(PaymentAlreadyProcessedException.class, () -> paymentService.initializePayment(request));
    }

    @Transactional
    @Test
    void testInitializePayment_TimeoutScenario() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(1500.00));

        when(mockHttpClient.send(any(), any())).thenThrow(new HttpConnectTimeoutException("Connection timed out"));

        InitializePaymentRequest request = new InitializePaymentRequest(order.getOrderId(), "user@example.com");
        assertThrows(PaymentGatewayTimeoutException.class, () -> paymentService.initializePayment(request));
    }

    @Transactional
    @Test
    void testInitializePayment_503GatewayOutage() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(1500.00));

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(503);
        when(mockResponse.body()).thenReturn("Service Unavailable");
        doReturn(mockResponse).when(mockHttpClient).send(any(), any());

        InitializePaymentRequest request = new InitializePaymentRequest(order.getOrderId(), "user@example.com");
        assertThrows(PaymentGatewayUnavailableException.class, () -> paymentService.initializePayment(request));
    }

    @Transactional
    @Test
    void testInitializePayment_400BadRequest() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(1500.00));

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("{\"status\":false,\"message\":\"Invalid email address\"}");
        doReturn(mockResponse).when(mockHttpClient).send(any(), any());

        InitializePaymentRequest request = new InitializePaymentRequest(order.getOrderId(), "invalid-email");
        PaymentValidationException exception = assertThrows(PaymentValidationException.class, () -> paymentService.initializePayment(request));
        assertEquals("Invalid email address", exception.getMessage());
    }

    @Transactional
    @Test
    void testVerifyAndSyncPayment_SuccessfulStatus() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(2500.00));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepo.save(payment);
        String reference = payment.getReference();

        String mockResponseBody = "{\n" +
                "  \"status\": true,\n" +
                "  \"message\": \"Verification successful\",\n" +
                "  \"data\": {\n" +
                "    \"status\": \"success\",\n" +
                "    \"reference\": \"" + reference + "\",\n" +
                "    \"amount\": 250000\n" +
                "  }\n" +
                "}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockHttpClient.send(any(), any())).thenAnswer(invocation -> mockResponse);

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(reference);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESSFUL, response.getPaymentStatus());
        assertEquals(OrderStatus.PAID, response.getOrderStatus());

        Payment updatedPayment = paymentRepo.findByReference(reference).orElse(null);
        assertNotNull(updatedPayment);
        assertEquals(PaymentStatus.SUCCESSFUL, updatedPayment.getStatus());
        assertEquals(OrderStatus.PAID, updatedPayment.getOrder().getOrderStatus());
    }

    @Transactional
    @Test
    void testVerifyAndSyncPayment_FailedStatus() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(2500.00));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepo.save(payment);
        String reference = payment.getReference();

        String mockResponseBody = "{\n" +
                "  \"status\": true,\n" +
                "  \"message\": \"Verification failed\",\n" +
                "  \"data\": {\n" +
                "    \"status\": \"failed\",\n" +
                "    \"reference\": \"" + reference + "\",\n" +
                "    \"amount\": 250000\n" +
                "  }\n" +
                "}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockHttpClient.send(any(), any())).thenAnswer(invocation -> mockResponse);

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(reference);

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getPaymentStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, response.getOrderStatus());

        Payment updatedPayment = paymentRepo.findByReference(reference).orElse(null);
        assertNotNull(updatedPayment);
        assertEquals(PaymentStatus.FAILED, updatedPayment.getStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, updatedPayment.getOrder().getOrderStatus());
    }

    @Transactional
    @Test
    void testVerifyAndSyncPayment_AmountMismatchTampering() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(2500.00));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepo.save(payment);
        String reference = payment.getReference();

        // Gateway returns 1000 kobo (10.00) instead of 250000 kobo (2500.00)
        String mockResponseBody = "{\n" +
                "  \"status\": true,\n" +
                "  \"message\": \"Verification successful\",\n" +
                "  \"data\": {\n" +
                "    \"status\": \"success\",\n" +
                "    \"reference\": \"" + reference + "\",\n" +
                "    \"amount\": 1000\n" +
                "  }\n" +
                "}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockHttpClient.send(any(), any())).thenAnswer(invocation -> mockResponse);

        assertThrows(PaymentValidationException.class, () -> paymentService.verifyAndSyncPayment(reference));

        Payment updatedPayment = paymentRepo.findByReference(reference).orElse(null);
        assertNotNull(updatedPayment);
        assertEquals(PaymentStatus.FAILED, updatedPayment.getStatus());
    }

    @Transactional
    @Test
    void testVerifyAndSyncPayment_TimeoutScenario_ReturnsPending() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(2500.00));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepo.save(payment);
        String reference = payment.getReference();

        when(mockHttpClient.send(any(), any())).thenThrow(new HttpConnectTimeoutException("Connection timed out"));

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(reference);

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, response.getOrderStatus());
    }

    @Transactional
    @Test
    void testVerifyAndSyncPayment_503GatewayOutage_ReturnsPending() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(2500.00));

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepo.save(payment);
        String reference = payment.getReference();

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(503);
        when(mockResponse.body()).thenReturn("Service Unavailable");
        doReturn(mockResponse).when(mockHttpClient).send(any(), any());

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(reference);

        assertNotNull(response);
        assertEquals(PaymentStatus.PENDING, response.getPaymentStatus());
        assertEquals(OrderStatus.PENDING_PAYMENT, response.getOrderStatus());
    }

    @Transactional
    @Test
    void testVerifyAndSyncPayment_Idempotency() throws IOException, InterruptedException {
        Order order = createTestOrder(BigDecimal.valueOf(3500.00));
        order.setOrderStatus(OrderStatus.PAID);
        orderRepo.save(order);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment = paymentRepo.save(payment);
        
        payment.setStatus(PaymentStatus.SUCCESSFUL);
        payment = paymentRepo.save(payment);
        String reference = payment.getReference();

        PaymentStatusResponse response = paymentService.verifyAndSyncPayment(reference);

        assertNotNull(response);
        assertEquals(PaymentStatus.SUCCESSFUL, response.getPaymentStatus());
        assertEquals(OrderStatus.PAID, response.getOrderStatus());

        // Verify that no HTTP client request was executed because payment was already SUCCESSFUL
        verifyNoInteractions(mockHttpClient);
    }
}
