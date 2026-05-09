package com.deepthoughtnet.clinic.api.clinicaldocument.ai.service;

import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobEntity;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.db.ClinicalAiJobRepository;
import com.deepthoughtnet.clinic.api.clinicaldocument.ai.model.ClinicalAiJobStatus;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "clinic.ai", name = "enabled", havingValue = "true")
public class ClinicalAiJobProcessor {
    private final ClinicalAiJobRepository repository;
    private final ClinicalDocumentAiExtractionService extractionService;

    public ClinicalAiJobProcessor(ClinicalAiJobRepository repository, ClinicalDocumentAiExtractionService extractionService) {
        this.repository = repository;
        this.extractionService = extractionService;
    }

    @Scheduled(fixedDelayString = "${clinic.ai.jobs.fixedDelay:PT1M}")
    public void processQueuedJobs() {
        List<ClinicalAiJobEntity> jobs = repository.findTop25ByStatusInOrderByCreatedAtAsc(
                List.of(ClinicalAiJobStatus.QUEUED, ClinicalAiJobStatus.RETRY_SCHEDULED)
        );
        for (ClinicalAiJobEntity job : jobs) {
            if (job.getNextAttemptAt() != null && job.getNextAttemptAt().isAfter(OffsetDateTime.now())) {
                continue;
            }
            extractionService.process(job.getId());
        }
    }
}
