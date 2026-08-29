package com.documorph.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documorph.dto.MeResponse;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final WorkspaceRepository workspaceRepository;

	@Transactional(readOnly = true)
	public MeResponse getMe(String email) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new ResourceNotFoundException("User not found."));
		Workspace workspace = workspaceRepository.findByUserId(user.getId())
				.orElseThrow(() -> new ResourceNotFoundException("Workspace not found."));

		String orgName = null;
		UUID orgId = null;
		if (workspace.getOrganization() != null) {
			orgId = workspace.getOrganization().getId();
			orgName = workspace.getOrganization().getName();
		}

		return MeResponse.builder().userId(user.getId()).email(user.getEmail()).userType(user.getUserType())
				.workspaceId(workspace.getId()).organizationId(orgId).organizationName(orgName)
				.inviteCode(workspace.getOrganization() != null ? workspace.getOrganization().getInviteCode() : null)
				.build();
	}
}