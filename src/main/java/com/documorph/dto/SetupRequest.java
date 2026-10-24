package com.documorph.dto;

import com.documorph.model.User.UserType;

import jakarta.validation.constraints.NotNull;

public class SetupRequest {

    @NotNull(message = "User type is required")
    private UserType userType;

    private String organizationName;
    private String inviteCode;

    public SetupRequest() {
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
