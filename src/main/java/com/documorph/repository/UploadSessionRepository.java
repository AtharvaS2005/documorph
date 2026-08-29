package com.documorph.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.UploadSession;

@Repository
public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {
	List<UploadSession> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

	boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);

}