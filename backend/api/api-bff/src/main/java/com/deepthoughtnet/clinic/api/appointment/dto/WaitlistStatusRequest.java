package com.deepthoughtnet.clinic.api.appointment.dto;

import com.deepthoughtnet.clinic.appointment.service.model.WaitlistStatus;
import jakarta.validation.constraints.NotNull;

public record WaitlistStatusRequest(
        @NotNull
        WaitlistStatus status
) {
}
