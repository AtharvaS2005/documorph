package com.documorph.dto;

import java.util.UUID;

import com.documorph.model.User.UserType;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeResponse {
	private UUID userId;
	private String email;
	private UserType userType;
	private UUID workspaceId;
	private UUID organizationId;
	private String organizationName;
	private String inviteCode;
}