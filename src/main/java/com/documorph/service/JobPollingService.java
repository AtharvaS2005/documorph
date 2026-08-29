package com.documorph.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.documorph.model.ExtractionJob;
import com.documorph.repository.ExtractionJobRepository;

import lombok.RequiredArgsConstructor;

/**
 * Polls Python for job status. Deliberately NOT transactional -- each job is
 * processed in its own transaction (see JobResultProcessor) so one failure
 * cannot mark a shared transaction rollback-only and poison every other job.
 */
@Service
@RequiredArgsConstructor
public class JobPollingService {

	private static final Logger log = LoggerFactory.getLogger(JobPollingService.class);

	private final ExtractionJobRepository extractionJobRepository;
	private final JobResultProcessor jobResultProcessor;

	@Scheduled(fixedDelay = 15000)
	public void pollActiveJobs() {
		List<ExtractionJob> active = extractionJobRepository.findByStatusInAndExternalJobIdIsNotNull(
				List.of(ExtractionJob.JobStatus.QUEUED, ExtractionJob.JobStatus.PROCESSING));

		for (ExtractionJob job : active) {
			UUID jobId = job.getId();
			boolean justCompleted;
			try {
				justCompleted = jobResultProcessor.processOne(jobId);
			} catch (Exception e) {
				log.warn("Status polling failed for job {}", jobId, e);
				continue;
			}
			if (!justCompleted) {
				continue;
			}

			// Side effects run in their own transactions, after the status
			// update has already committed -- a failure here is logged and
			// can never roll back or block job completion.
			try {
				jobResultProcessor.indexForRag(jobId);
			} catch (Exception e) {
				log.warn("RAG indexing failed for job {}", jobId, e);
			}
			try {
				jobResultProcessor.appendToRecordSet(jobId);
			} catch (Exception e) {
				log.warn("Record set append failed for job {}", jobId, e);
			}
		}
	}
}