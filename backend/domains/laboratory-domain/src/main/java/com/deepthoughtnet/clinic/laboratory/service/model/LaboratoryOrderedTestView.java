package com.deepthoughtnet.clinic.laboratory.service.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LaboratoryOrderedTestView(
        UUID labOrderItemId,
        String state,
        int latestResultRevision,
        String latestVerificationDecision,
        OffsetDateTime latestVerificationAt,
        UUID latestVerificationBy,
        Integer latestPublicationArtifactNumber,
        OffsetDateTime latestPublicationAt,
        UUID latestPublicationBy,
        List<String> publicationChannels,
        String latestResultSnapshotJson,
        OffsetDateTime latestResultEnteredAt,
        UUID latestResultEnteredBy,
        List<LaboratorySpecimenLinkView> specimenLinks
) {
}
