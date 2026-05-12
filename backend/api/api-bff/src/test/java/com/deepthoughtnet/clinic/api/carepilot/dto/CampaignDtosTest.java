package com.deepthoughtnet.clinic.api.carepilot.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CampaignDtosTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createCampaignRequestAllowsMissingTemplateId() throws Exception {
        String json = """
                {
                  "name": "Follow-up reminder",
                  "campaignType": "FOLLOW_UP_REMINDER",
                  "triggerType": "SCHEDULED",
                  "audienceType": "ALL_PATIENTS",
                  "notes": "v1 campaign"
                }
                """;

        CampaignDtos.CreateCampaignRequest request = objectMapper.readValue(json, CampaignDtos.CreateCampaignRequest.class);

        assertThat(request.templateId()).isNull();
        assertThat(request.triggerType().name()).isEqualTo("SCHEDULED");
    }

    @Test
    void createCampaignRequestRejectsInvalidEnum() {
        String json = """
                {
                  "name": "Invalid enum",
                  "campaignType": "FOLLOW_UP_REMINDER",
                  "triggerType": "EVENT",
                  "audienceType": "ALL_PATIENTS"
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, CampaignDtos.CreateCampaignRequest.class))
                .isInstanceOf(Exception.class);
    }

    @Test
    void createCampaignRequestRejectsMalformedTemplateId() {
        String json = """
                {
                  "name": "Bad template",
                  "campaignType": "FOLLOW_UP_REMINDER",
                  "triggerType": "SCHEDULED",
                  "audienceType": "ALL_PATIENTS",
                  "templateId": "not-a-uuid"
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, CampaignDtos.CreateCampaignRequest.class))
                .isInstanceOf(Exception.class);
    }
}
