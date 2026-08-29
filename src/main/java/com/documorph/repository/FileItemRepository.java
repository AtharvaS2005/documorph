package com.documorph.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.FileItem;

@Repository
public interface FileItemRepository extends JpaRepository<FileItem, UUID> {
	List<FileItem> findBySessionId(UUID sessionId);
}