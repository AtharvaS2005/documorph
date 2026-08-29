package com.documorph.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.EventLog;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, UUID> {
	List<EventLog> findByWorkspaceIdOrderByEventTimestampDesc(UUID workspaceId);
}