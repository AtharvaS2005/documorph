package com.documorph.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.ForumPost;

@Repository
public interface ForumPostRepository extends JpaRepository<ForumPost, UUID> {
    List<ForumPost> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

	boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}