package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EventGuestLimitRequest {
    // A higher number of plates, or unlimited.
    @Min(value = 1, message = "A guest needs at least one plate")
    private Integer limit;
    private boolean unlimited;
}
