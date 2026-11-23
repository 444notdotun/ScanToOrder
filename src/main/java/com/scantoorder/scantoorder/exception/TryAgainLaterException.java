package com.scantoorder.scantoorder.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// "Not now": a limit was reached (429) or something we depend on did not answer (503). The message says what to do.
@Getter
public class TryAgainLaterException extends ScanToOrderException {
    private final HttpStatus status;

    public TryAgainLaterException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
