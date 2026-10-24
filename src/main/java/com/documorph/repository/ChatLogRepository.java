package com.documorph.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.ChatLog;

@Repository
public interface ChatLogRepository extends JpaRepository<ChatLog, UUID> {
	List<ChatLog> findTop50ByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
