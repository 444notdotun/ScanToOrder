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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentGatewayTimeoutException.class)
    public ResponseEntity<Object> handlePaymentGatewayTimeoutException(PaymentGatewayTimeoutException ex) {
        return buildResponse(HttpStatus.GATEWAY_TIMEOUT, ex.getMessage());
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    public ResponseEntity<Object> handlePaymentGatewayUnavailableException(PaymentGatewayUnavailableException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(PaymentGatewayException.class)
    public ResponseEntity<Object> handlePaymentGatewayException(PaymentGatewayException ex) {
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
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
            EntityNotFoundException.class,
            CategoryCanNotBeFoundException.class,
            ItemNotFound.class,
            MenuNotFoundException.class,
            OrderNotFoundException.class,
            SeatNotFoundException.class,
            SessionNotFoundException.class,
            TableNotFoundException.class,
            org.springframework.web.servlet.resource.NoResourceFoundException.class
    })
    public ResponseEntity<Object> handleNotFoundExceptions(Exception ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OrderStatusException.class)
    public ResponseEntity<Object> handleOrderStatusException(OrderStatusException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(SeatStatusException.class)
    public ResponseEntity<Object> handleSeatStatusException(SeatStatusException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        StringBuilder message = new StringBuilder("Validation failed: ");
        ex.getBindingResult().getFieldErrors().forEach(error ->
                message.append(error.getField()).append(" - ").append(error.getDefaultMessage()).append("; ")
        );
        return buildResponse(HttpStatus.BAD_REQUEST, message.toString().trim());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex) {
        StringBuilder message = new StringBuilder("Validation failed: ");
        ex.getConstraintViolations().forEach(violation ->
                message.append(violation.getPropertyPath()).append(" - ").append(violation.getMessage()).append("; ")
        );
        return buildResponse(HttpStatus.BAD_REQUEST, message.toString().trim());
    }

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public ResponseEntity<Object> handleHandlerMethodValidationException(org.springframework.web.method.annotation.HandlerMethodValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed: " + ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Object> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        String message = "A database constraint was violated (e.g., duplicate entry or invalid reference).";
        ex.getMostSpecificCause();
        message = "Data constraint violation: " + ex.getMostSpecificCause().getMessage();
        return buildResponse(HttpStatus.CONFLICT, message);
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleCatchAllException(Exception ex) {
        ex.printStackTrace();
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#x27;")
                    .replace("/", "&#x2F;");
    }

    private ResponseEntity<Object> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", escapeHtml(message));
        return new ResponseEntity<>(body, status);
    }
}
