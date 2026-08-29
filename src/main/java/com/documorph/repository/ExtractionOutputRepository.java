package com.documorph.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.ExtractionOutput;

@Repository
public interface ExtractionOutputRepository extends JpaRepository<ExtractionOutput, UUID> {
	Optional<ExtractionOutput> findByJobId(UUID jobId);
}