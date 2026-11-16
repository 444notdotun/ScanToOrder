package com.scantoorder.scantoorder.dtos.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateStaffRequest {
    @NotBlank(message = "Tell us the person's name")
    @Size(max = 80, message = "That name is too long")
    private String name;

    @NotBlank(message = "Choose a role: chef or waiter")
    private String role;

    // A short handle such as "ada". The sign-in becomes slug.handle.
    @NotBlank(message = "Choose a short handle for the sign-in")
    @JsonAlias("username")
    private String handle;
}
