package com.documorph.service;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.documorph.config.JwtService;
import com.documorph.dto.AuthResponse;
import com.documorph.dto.LoginRequest;
import com.documorph.dto.SetupRequest;
import com.documorph.dto.SignUpRequest;
import com.documorph.exception.EmailAlreadyExistsException;
import com.documorph.exception.InvalidCredentialsException;
import com.documorph.exception.ForbiddenException;
import com.documorph.exception.ResourceNotFoundException;
import com.documorph.model.EventLog;
import com.documorph.model.Organization;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.EventLogRepository;
import com.documorph.repository.OrganizationRepository;
import com.documorph.repository.UserRepository;
import com.documorph.repository.WorkspaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final OrganizationRepository organizationRepository;
    private final EventLogRepository eventLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse signUp(SignUpRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("Email address is already in use.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isSetupCompleted(false)
                .build();
        user = userRepository.save(user);

        Workspace workspace = Workspace.builder().user(user).build();
        workspace = workspaceRepository.save(workspace);

        eventLogRepository.save(EventLog.builder()
                .actorUser(user)
                .workspace(workspace)
                .eventType("user_signup_completed")
                .targetId(user.getId().toString())
                .build());

        // The token lets the new account call /api/auth/setup as itself; setup
        // used to trust a userId from the request body instead.
        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .isSetupCompleted(false)
                .workspaceId(workspace.getId())
                .token(jwtService.generateToken(user.getEmail()))
                .message("Sign up successful. Please complete setup.")
                .build();
    }

    @Transactional
    public AuthResponse setupUser(SetupRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        if (Boolean.TRUE.equals(user.getIsSetupCompleted())) {
            throw new ForbiddenException("Account setup is already complete.");
        }

        Workspace workspace = workspaceRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found for user."));

        user.setUserType(request.getUserType());
        user.setIsSetupCompleted(true);

        if (request.getUserType() == User.UserType.ORGANIZATION) {
            Organization org = null;

            if (request.getInviteCode() != null && !request.getInviteCode().isBlank()) {
                org = organizationRepository.findByInviteCode(request.getInviteCode())
                        .orElseThrow(() -> new ResourceNotFoundException("Invalid organization invite code."));
            } else if (request.getOrganizationName() != null && !request.getOrganizationName().isBlank()) {
                org = Organization.builder()
                        .name(request.getOrganizationName())
                        .inviteCode(UUID.randomUUID().toString())
                        .build();
                org = organizationRepository.save(org);
            }

            if (org != null) {
                workspace.setOrganization(org);
                workspaceRepository.save(workspace);
            }
        }

        userRepository.save(user);

        eventLogRepository.save(EventLog.builder()
                .actorUser(user)
                .workspace(workspace)
                .eventType("user_type_selected")
                .targetId(user.getUserType().name())
                .build());

		String token = jwtService.generateToken(user.getEmail());

		return AuthResponse.builder().userId(user.getId()).email(user.getEmail()).userType(user.getUserType())
				.isSetupCompleted(true).workspaceId(workspace.getId())
				.organizationId(workspace.getOrganization() != null ? workspace.getOrganization().getId() : null)
				.token(token).message("Setup completed successfully.").build();
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        Workspace workspace = workspaceRepository.findByUserId(user.getId()).orElse(null);
        String token = jwtService.generateToken(user.getEmail());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .userType(user.getUserType())
                .isSetupCompleted(user.getIsSetupCompleted())
                .workspaceId(workspace != null ? workspace.getId() : null)
                .organizationId(workspace != null && workspace.getOrganization() != null
                        ? workspace.getOrganization().getId() : null)
                .token(token)
                .message("Login successful.")
                .build();
    }
}
