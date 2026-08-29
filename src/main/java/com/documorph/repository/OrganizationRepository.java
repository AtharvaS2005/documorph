package com.documorph.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.Organization;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
	Optional<Organization> findByInviteCode(String inviteCode);
}