package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.exception.GlobalExceptionHandler;
import com.scantoorder.scantoorder.exception.PaymentGatewayException;
import com.scantoorder.scantoorder.exception.PaymentGatewayTimeoutException;
import com.scantoorder.scantoorder.exception.PaymentGatewayUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMessagesTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private String said(ResponseEntity<Object> response) {
        return (String) ((Map<?, ?>) response.getBody()).get("message");
    }

    @Test
    void testThatAGatewayFailureIsSaidInOurVoiceAndNeverCarriesTheTechnicalDetail() {
        String message = said(handler.handlePaymentGatewayException(new PaymentGatewayException("Error communicating with Paystack: Connection reset by peer")));

        assertEquals("We couldn't reach Paystack just now. Nothing was charged. Try again in a moment.", message);
        assertFalse(message.contains("Connection reset"));
    }

    @Test
    void testThatAMessageThatIsAlreadyInOurVoiceIsKept() {
        String ours = "Paystack couldn't take that payment. Check the details and try again.";

        assertEquals(ours, said(handler.handlePaymentGatewayException(new PaymentGatewayException(ours))));
    }

    @Test
    void testThatAnOutageAndATimeoutTellThePersonNothingWasCharged() {
        assertTrue(said(handler.handlePaymentGatewayUnavailableException(new PaymentGatewayUnavailableException("Paystack service is currently down"))).contains("Nothing was charged"));
        assertTrue(said(handler.handlePaymentGatewayTimeoutException(new PaymentGatewayTimeoutException("Timeout connecting to Paystack"))).contains("Nothing was charged"));
    }
}
