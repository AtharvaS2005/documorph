package com.documorph.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.RecordSet;

@Repository
public interface RecordSetRepository extends JpaRepository<RecordSet, UUID> {
	List<RecordSet> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

	List<RecordSet> findByCategoryIdOrderByCreatedAtAsc(UUID categoryId);

	boolean existsByCategoryId(UUID categoryId);
	boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);
}