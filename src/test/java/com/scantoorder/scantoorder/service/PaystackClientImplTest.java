package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.respond.PaystackSplit;
import com.scantoorder.scantoorder.exception.PayoutAccountRejectedException;
import com.scantoorder.scantoorder.exception.PaymentGatewayUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaystackClientImplTest {

    private HttpClient http;
    private PaystackClientImpl client;

    @BeforeEach
    void setUp() {
        http = mock(HttpClient.class);
        client = new PaystackClientImpl();
        ReflectionTestUtils.setField(client, "paystackSecretKey", "sk_test_x");
        ReflectionTestUtils.setField(client, "paystackBaseUrl", "https://api.paystack.co");
        ReflectionTestUtils.setField(client, "paystackCallbackUrl", "http://localhost:3000/done");
        client.setHttpClient(http);
    }

    @SuppressWarnings("unchecked")
    private void answer(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(), any())).thenAnswer(invocation -> response);
    }

    private HttpRequest lastRequest() throws Exception {
        org.mockito.ArgumentCaptor<HttpRequest> captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, atLeastOnce()).send(captor.capture(), any());
        return captor.getValue();
    }

    private String bodyOf(HttpRequest request) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer item) { byte[] bytes = new byte[item.remaining()]; item.get(bytes); out.write(bytes, 0, bytes.length); }
            public void onError(Throwable t) { }
            public void onComplete() { }
        });
        return out.toString();
    }

    @Test
    void testThatInitializeSendsTheSubaccountTheFlatChargeAndTheBearer() throws Exception {
        answer(200, "{\"status\":true,\"data\":{\"authorization_url\":\"https://pay/x\"}}");

        client.initialize("guest@example.com", 150_000, "PAYMENT-ABC12345", new PaystackSplit("ACCT_abc", 3_000, "account"));

        String body = bodyOf(lastRequest());
        assertTrue(body.contains("\"amount\":150000"));
        assertTrue(body.contains("\"subaccount\":\"ACCT_abc\""));
        assertTrue(body.contains("\"transaction_charge\":3000"));
        assertTrue(body.contains("\"bearer\":\"account\""));
        assertTrue(body.contains("\"reference\":\"PAYMENT-ABC12345\""));
    }

    @Test
    void testThatAZeroKoboChargeIsStillSentExplicitly() throws Exception {
        answer(200, "{\"status\":true,\"data\":{\"authorization_url\":\"https://pay/x\"}}");
        client.initialize("guest@example.com", 49, "PAYMENT-ZERO0001", new PaystackSplit("ACCT_abc", 0, "subaccount"));
        String body = bodyOf(lastRequest());
        assertTrue(body.contains("\"transaction_charge\":0"));
        assertTrue(body.contains("\"bearer\":\"subaccount\""));
    }

    @Test
    void testThatASubaccountIsCreatedFromTheBusinessNameAndBankDetails() throws Exception {
        answer(201, "{\"status\":true,\"data\":{\"subaccount_code\":\"ACCT_new123\"}}");

        String code = client.createSubaccount("The Corner Table", "058", "0123456789", new java.math.BigDecimal("2.0"));

        assertEquals("ACCT_new123", code);
        HttpRequest request = lastRequest();
        assertTrue(request.uri().toString().endsWith("/subaccount"));
        String body = bodyOf(request);
        assertTrue(body.contains("\"business_name\":\"The Corner Table\""));
        assertTrue(body.contains("\"bank_code\":\"058\""));
    }

    @Test
    void testThatARefusedSubaccountGivesTheKindMessageWithoutTheAccountNumber() throws Exception {
        answer(400, "{\"status\":false,\"message\":\"Account number 0123456789 is invalid\"}");
        PayoutAccountRejectedException exception = assertThrows(PayoutAccountRejectedException.class,
                () -> client.createSubaccount("Odd Place", "058", "0123456789", new java.math.BigDecimal("2.0")));
        assertEquals("Paystack rejected this business's payout account. Ask them to check it and send the form again.", exception.getMessage());
        assertFalse(exception.getMessage().contains("0123456789"));
    }

    @Test
    void testThatASubaccountRequestDuringAnOutageIsNotMistakenForARefusal() throws Exception {
        answer(503, "{}");
        assertThrows(PaymentGatewayUnavailableException.class,
                () -> client.createSubaccount("Odd Place", "058", "0123456789", new java.math.BigDecimal("2.0")));
    }

    @Test
    void testThatAPayoutChangeIsSentToTheSubaccountWithTheNewBankAndNumber() throws Exception {
        answer(200, "{\"status\":true,\"message\":\"Subaccount updated\"}");

        client.updateSubaccount("ACCT_abc", "057", "0000000000");

        HttpRequest request = lastRequest();
        assertEquals("PUT", request.method());
        assertTrue(request.uri().toString().endsWith("/subaccount/ACCT_abc"));
        String body = bodyOf(request);
        assertTrue(body.contains("\"settlement_bank\":\"057\""));
        assertTrue(body.contains("\"account_number\":\"0000000000\""));
    }

    @Test
    void testThatARefusedPayoutChangeNeverCarriesTheAccountNumberAndAnOutageIsNotMistakenForARefusal() throws Exception {
        answer(400, "{\"status\":false,\"message\":\"Account details are invalid\"}");
        PayoutAccountRejectedException refused = assertThrows(PayoutAccountRejectedException.class,
                () -> client.updateSubaccount("ACCT_abc", "057", "0123456789"));
        assertEquals(PaystackClientImpl.PAYOUT_UPDATE_REJECTED, refused.getMessage());
        assertFalse(refused.getMessage().contains("0123456789"));
        answer(503, "{}");
        assertThrows(PaymentGatewayUnavailableException.class, () -> client.updateSubaccount("ACCT_abc", "057", "0123456789"));
    }

    @Test
    void testThatTheSubaccountListGivesCodesAndBusinessNames() throws Exception {
        answer(200, "{\"status\":true,\"data\":[{\"subaccount_code\":\"ACCT_one\",\"business_name\":\"The Corner Table (demo)\"},"
                + "{\"subaccount_code\":\"ACCT_two\",\"business_name\":\"Somewhere Else\"}]}");

        var found = client.listSubaccounts();

        assertEquals(2, found.size());
        assertEquals("ACCT_one", found.get(0).code());
        assertEquals("The Corner Table (demo)", found.get(0).businessName());
        assertTrue(lastRequest().uri().toString().contains("/subaccount?"));
    }
}
