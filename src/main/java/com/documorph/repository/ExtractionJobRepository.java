package com.documorph.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documorph.model.ExtractionJob;

@Repository
public interface ExtractionJobRepository extends JpaRepository<ExtractionJob, UUID> {
	Optional<ExtractionJob> findByFileId(UUID fileId);

	List<ExtractionJob> findByStatusInAndExternalJobIdIsNotNull(List<ExtractionJob.JobStatus> statuses);
}