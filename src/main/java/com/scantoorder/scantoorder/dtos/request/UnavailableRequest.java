package com.scantoorder.scantoorder.dtos.request;

import com.scantoorder.scantoorder.data.model.UnavailableReason;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UnavailableRequest {
    @NotNull(message = "Say why the kitchen can't make it: RAN_OUT, SOMETHING_WRONG or EQUIPMENT_DOWN")
    private UnavailableReason reason;
}
