package com.deepthoughtnet.clinic.api.appointment.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record QueueReorderRequest(@NotEmpty List<UUID> orderedAppointmentIds) {
}
