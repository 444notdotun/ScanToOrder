package com.scantoorder.scantoorder.service;

import com.scantoorder.scantoorder.dtos.request.ClaimSeatRequest;
import com.scantoorder.scantoorder.dtos.request.JoinSeatRequest;
import com.scantoorder.scantoorder.exception.GlobalExceptionHandler;
import com.scantoorder.scantoorder.exception.PlaceClosedException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// A guest or a staff member never reads a field path, a class name or a developer's sentence.
class PlainMessagesTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @SuppressWarnings("unused")
    public void scan(@Pattern(regexp = "^[a-z0-9]+$", message = "That link doesn't look right. Go back and try again.") String slug) {
    }

    private static String said(ResponseEntity<Object> response) {
        return (String) ((Map<?, ?>) response.getBody()).get("message");
    }

    @Test
    void testThatAWrongTableLinkIsSaidInPlainWordsWithNoFieldPath() throws Exception {
        Method method = PlainMessagesTest.class.getMethod("scan", String.class);
        Set<ConstraintViolation<PlainMessagesTest>> violations = validator.forExecutables()
                .validateParameters(this, method, new Object[]{"NOT A SLUG!"});
        assertFalse(violations.isEmpty());

        ResponseEntity<Object> response = handler.handleConstraintViolationException(new ConstraintViolationException(violations));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String message = said(response);
        assertEquals("That link doesn't look right. Go back and try again.", message);
        assertFalse(message.contains("Validation failed"));
        assertFalse(message.contains("slug"));
    }

    @Test
    void testThatAFormMistakeNamesTheFieldAndSaysWhatToDo() {
        JoinSeatRequest join = new JoinSeatRequest();
        join.setJoinCode("K7M2QD");
        join.setCustomerName("Ada");
        join.setCustomerPhoneNumber("");

        Set<ConstraintViolation<JoinSeatRequest>> violations = validator.validate(join);

        assertTrue(violations.stream().anyMatch(v -> v.getMessage().equals("Enter your 11-digit phone number")));
        ClaimSeatRequest claim = new ClaimSeatRequest();
        claim.setTableId("1");
        claim.setSeatId("abc");
        claim.setCustomerName("Ada");
        claim.setCustomerPhoneNumber("0801");
        assertTrue(validator.validate(claim).stream().anyMatch(v -> v.getMessage().equals("Enter your 11-digit phone number")));
    }

    @Test
    void testThatNoRequestMessageReadsLikeCode() throws Exception {
        File folder = new File("target/classes/com/scantoorder/scantoorder/dtos/request");
        File[] classes = folder.listFiles((dir, name) -> name.endsWith(".class"));
        assertNotNull(classes);
        for (File file : classes) {
            Class<?> type = Class.forName("com.scantoorder.scantoorder.dtos.request." + file.getName().replace(".class", ""));
            for (Field field : type.getDeclaredFields()) {
                for (var annotation : field.getAnnotations()) {
                    try {
                        Method message = annotation.annotationType().getMethod("message");
                        String text = (String) message.invoke(annotation);
                        String where = type.getSimpleName() + "." + field.getName() + ": " + text;
                        assertFalse(text.matches(".*\\b(cannot be|can not be|must be exactly|Invalid identifier|null|blank)\\b.*"), where);
                        assertFalse(text.matches(".*[a-z][A-Z].*"), where);
                        assertFalse(text.startsWith("{"), where);
                    } catch (NoSuchMethodException ignored) {
                        // Not a validation annotation.
                    }
                }
            }
        }
    }

    @Test
    void testThatAnUnexpectedErrorGivesACalmSentenceAndNeverTheInternalText() {
        String message = said(handler.handleCatchAllException(new IllegalStateException("could not execute statement [ERROR: relation \"orders\" does not exist]")));
        assertEquals("Something went wrong on our side. Try again in a moment.", message);

        assertEquals("Something went wrong on our side. Try again in a moment.",
                said(handler.handleRuntimeException(new RuntimeException("NullPointerException at OrderService.java:44"))));
    }

    @Test
    void testThatADatabaseClashNeverShowsTheConstraintName() {
        String message = said(handler.handleDataIntegrityViolationException(
                new org.springframework.dao.DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_tenant_slug\"")));
        assertFalse(message.contains("uk_tenant_slug"));
        assertFalse(message.contains("constraint"));
    }

    @Test
    void testThatALinkWithLettersWhereANumberBelongsIsAskedAgainPlainly() {
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException("abc", Integer.class, "displayNumber", null, new NumberFormatException("For input string: \"abc\""));
        ResponseEntity<Object> response = handler.handleTypeMismatch(mismatch);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("That link doesn't look right. Go back and try again.", said(response));
    }

    @Test
    void testThatAClosedPlaceIsNamedSoTheScreenCanSayWhoIsClosed() {
        ResponseEntity<Object> response = handler.handlePlaceClosedException(new PlaceClosedException(TenantAccess.PLACE_CLOSED, "Triple4 Ikeja"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Triple4 Ikeja", ((Map<?, ?>) response.getBody()).get("businessName"));

        ResponseEntity<Object> unknown = handler.handlePlaceClosedException(new PlaceClosedException(TenantAccess.PLACE_CLOSED));
        assertFalse(((Map<?, ?>) unknown.getBody()).containsKey("businessName"));
    }
}
