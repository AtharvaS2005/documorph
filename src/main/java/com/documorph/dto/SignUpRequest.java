package com.documorph.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SignUpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 254, message = "Email is too long")
    private String email;

    @NotBlank(message = "Password is required")
    // BCrypt ignores everything past 72 bytes, so longer passwords would be silently truncated
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
    private String password;

    public SignUpRequest() {
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
