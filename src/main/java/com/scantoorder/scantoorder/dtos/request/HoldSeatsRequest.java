package com.scantoorder.scantoorder.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HoldSeatsRequest {
    @NotEmpty(message = "choose at least one seat to hold")
    @Size(max = 20, message = "too many seats")
    private List<@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Invalid identifier format") String> seatIds = new ArrayList<>();
}
