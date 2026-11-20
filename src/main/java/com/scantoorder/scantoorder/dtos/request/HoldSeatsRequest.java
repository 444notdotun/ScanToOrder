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
    @NotEmpty(message = "Choose at least one chair to save")
    @Size(max = 20, message = "That is too many chairs to save at once")
    private List<@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "That link doesn't look right. Go back and try again.") String> seatIds = new ArrayList<>();
}
