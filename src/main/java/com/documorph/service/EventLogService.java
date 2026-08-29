package com.documorph.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.documorph.model.EventLog;
import com.documorph.model.User;
import com.documorph.model.Workspace;
import com.documorph.repository.EventLogRepository;

import lombok.RequiredArgsConstructor;

/**
 * Audit trail writer. Runs in its own transaction (REQUIRES_NEW) so that a
 * logging failure can be caught by the caller without marking the caller's
 * transaction rollback-only.
 */
@Service
@RequiredArgsConstructor
public class EventLogService {

	private final EventLogRepository eventLogRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(User actor, Workspace workspace, String eventType, String targetId) {
		eventLogRepository.save(EventLog.builder().actorUser(actor).workspace(workspace).eventType(eventType)
				.targetId(targetId).build());
	}
}