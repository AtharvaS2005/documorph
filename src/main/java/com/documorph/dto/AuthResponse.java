package com.documorph.dto;

import java.util.UUID;

import com.documorph.model.User.UserType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UUID userId;
    private String email;
    private UserType userType;
    private Boolean isSetupCompleted;
    private UUID workspaceId;
    private UUID organizationId;
    private String token;
    private String message;
}
