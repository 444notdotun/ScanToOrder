package com.scantoorder.scantoorder.exception;

public class PlaceClosedException extends ScanToOrderException {
    private final String businessName;

    public PlaceClosedException(String message) {
        this(message, null);
    }

    public PlaceClosedException(String message, String businessName) {
        super(message);
        this.businessName = businessName;
    }

    public String getBusinessName() {
        return businessName;
    }
}
