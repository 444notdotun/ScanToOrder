package com.scantoorder.scantoorder.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // What a guest or staff member reads when the server gives us nothing they could act on. The real error is in the log.
    static final String SOMETHING_WENT_WRONG = "Something went wrong on our side. Try again in a moment.";
    static final String BAD_REQUEST_PLAIN = "Something in that didn't look right. Check it and try again.";

    @ExceptionHandler(PaymentGatewayTimeoutException.class)
    public ResponseEntity<Object> handlePaymentGatewayTimeoutException(PaymentGatewayTimeoutException ex) {
        return buildResponse(HttpStatus.GATEWAY_TIMEOUT, "Paystack took too long to answer. Nothing was charged. Try again in a moment.");
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ResponseEntity<Object> handlePaymentGatewayUnavailableException(PaymentGatewayUnavailableException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Paystack isn't answering right now. Nothing was charged. Try again in a moment.");
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Object> handlePaymentGatewayException(PaymentGatewayException ex) {
        // The technical detail is for the log; a person is told what to do.
        String message = ex.getMessage() != null && ex.getMessage().startsWith("Paystack couldn't")
                ? ex.getMessage()
                : "We couldn't reach Paystack just now. Nothing was charged. Try again in a moment.";
        return buildResponse(HttpStatus.BAD_GATEWAY, message);
    }

    @ExceptionHandler(PaymentAlreadyProcessedException.class)
    public ResponseEntity<Object> handlePaymentAlreadyProcessedException(PaymentAlreadyProcessedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PaymentValidationException.class)
    public ResponseEntity<Object> handlePaymentValidationException(PaymentValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({
            ResourceNotFoundException.class,
            CategoryCanNotBeFoundException.class,
            ItemNotFound.class,
            MenuNotFoundException.class,
            OrderNotFoundException.class,
            SeatNotFoundException.class,
            SessionNotFoundException.class,
            TableNotFoundException.class
    })
    public ResponseEntity<Object> handleNotFoundExceptions(Exception ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // These come from the framework or the database layer, so their own text is for the log only.
    @ExceptionHandler({EntityNotFoundException.class, org.springframework.web.servlet.resource.NoResourceFoundException.class})
    public ResponseEntity<Object> handleFrameworkNotFound(Exception ex) {
        log.info("Not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "We can't find that.");
    }

    @ExceptionHandler(OrderStatusException.class)
    public ResponseEntity<Object> handleOrderStatusException(OrderStatusException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(SeatStatusException.class)
    public ResponseEntity<Object> handleSeatStatusException(SeatStatusException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<Object> handleInvalidWebhookSignatureException(InvalidWebhookSignatureException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(PayoutAccountRejectedException.class)
    public ResponseEntity<Object> handlePayoutAccountRejectedException(PayoutAccountRejectedException ex) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(AccountVerificationException.class)
    public ResponseEntity<Object> handleAccountVerificationException(AccountVerificationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PlaceClosedException.class)
    public ResponseEntity<Object> handlePlaceClosedException(PlaceClosedException ex) {
        Map<String, Object> body = body(HttpStatus.CONFLICT, ex.getMessage());
        // A closed place the guest scanned is named, so the screen can say who is closed. An unknown link has no name.
        if (ex.getBusinessName() != null) body.put("businessName", ex.getBusinessName());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(BadSignInException.class)
    public ResponseEntity<Object> handleBadSignInException(BadSignInException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(SuspendedTenantException.class)
    public ResponseEntity<Object> handleSuspendedTenantException(SuspendedTenantException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(TryAgainLaterException.class)
    public ResponseEntity<Object> handleTryAgainLater(TryAgainLaterException ex) {
        return buildResponse(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequestException(InvalidRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ConfirmationNeededException.class)
    public ResponseEntity<Object> handleConfirmationNeededException(ConfirmationNeededException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("confirmationNeeded", true);
        body.put("paidUndeliveredOrders", ex.getPaidUndeliveredOrders());
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflictException(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SeatTakenException.class)
    public ResponseEntity<Object> handleSeatTakenException(SeatTakenException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SeatOwnershipException.class)
    public ResponseEntity<Object> handleSeatOwnershipException(SeatOwnershipException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(SessionEndedException.class)
    public ResponseEntity<Object> handleSessionEndedException(SessionEndedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "You are not allowed to do that");
    }

    // A form's mistakes come back as plain sentences, one per field, so a screen can put each under its own field.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), plainSentence(error.getDefaultMessage())));
        log.info("Request rejected by validation: {}", ex.getMessage());
        String first = fieldErrors.values().stream().findFirst().orElse(BAD_REQUEST_PLAIN);
        Map<String, Object> body = body(HttpStatus.BAD_REQUEST, first);
        body.put("fieldErrors", fieldErrors);
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // A link or query value that is the wrong shape. The field path is for the log, never for a person.
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex) {
        log.info("Request rejected by validation: {}", ex.getMessage());
        String first = ex.getConstraintViolations().stream()
                .map(violation -> plainSentence(violation.getMessage()))
                .findFirst().orElse(BAD_REQUEST_PLAIN);
        return buildResponse(HttpStatus.BAD_REQUEST, first);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<Object> handleHandlerMethodValidationException(org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
        log.info("Request rejected by validation: {}", ex.getMessage());
        String first = ex.getAllErrors().stream()
                .map(error -> plainSentence(error.getDefaultMessage()))
                .findFirst().orElse(BAD_REQUEST_PLAIN);
        return buildResponse(HttpStatus.BAD_REQUEST, first);
    }

    // A link with letters where a number belongs, for example /tables/abc.
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.info("Link value of the wrong type: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "That link doesn't look right. Go back and try again.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.info("Unreadable request body: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_PLAIN);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.info("Wrong method: {}", ex.getMessage());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "That isn't something this page can do.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Object> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.info("Unsupported media type: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, BAD_REQUEST_PLAIN);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("Database constraint violated: {}", ex.getMostSpecificCause().getMessage());
        return buildResponse(HttpStatus.CONFLICT, "That clashes with something that already exists. Check it and try again.");
    }

    // Anything we did not plan for: the person gets a calm sentence, the log gets the stack trace.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        log.error("Unhandled error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, SOMETHING_WENT_WRONG);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleCatchAllException(Exception ex) {
        log.error("Unhandled error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, SOMETHING_WENT_WRONG);
    }

    // A validation message written for a person is kept as it is. A leftover technical one is replaced.
    private static String plainSentence(String message) {
        if (message == null || message.isBlank()) return BAD_REQUEST_PLAIN;
        return message;
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }

    private ResponseEntity<Object> buildResponse(HttpStatus status, String message) {
        return new ResponseEntity<>(body(status, message), status);
    }
}
