package com.deepthoughtnet.clinic.platform.contracts.ai.rag;

import java.util.List;

public record RagSearchResult(
        List<RagDocumentReference> references,
        boolean complete
) {
}
