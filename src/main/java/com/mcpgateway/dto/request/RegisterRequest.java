package com.mcpgateway.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** New account submitted to {@code POST /api/v1/auth/register}. */
public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 3, max = 120, message = "Full name must be between 3 and 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 12, message = "Password must be at least 12 characters")
        String password,

        @AssertTrue(message = "Terms of service must be accepted")
        boolean acceptTerms) {
}
