package com.deepthoughtnet.clinic.api.patientportal.careai;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ConversationEntityMergeService {
    public Map<String, Object> merge(Map<String, Object> currentEntities, Map<String, Object> incomingEntities) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (currentEntities != null) {
            currentEntities.forEach((key, value) -> {
                if (shouldKeep(value)) {
                    merged.put(key, value);
                }
            });
        }
        if (incomingEntities != null) {
            incomingEntities.forEach((key, value) -> {
                if (shouldKeep(value)) {
                    merged.put(key, value);
                }
            });
        }
        return Map.copyOf(merged);
    }

    public Map<String, Object> mergeWithChanges(Map<String, Object> currentEntities,
                                                Map<String, Object> incomingEntities,
                                                List<String> changes) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (currentEntities != null) {
            currentEntities.forEach((key, value) -> {
                if (shouldKeep(value)) {
                    merged.put(key, value);
                }
            });
        }
        if (incomingEntities != null) {
            incomingEntities.forEach((key, value) -> {
                if (!shouldKeep(value)) {
                    return;
                }
                Object previous = merged.put(key, value);
                if (!equalsValue(previous, value)) {
                    changes.add(key);
                }
            });
        }
        return Map.copyOf(merged);
    }

    public Map<String, Object> copy(Map<String, Object> entities) {
        if (entities == null || entities.isEmpty()) {
            return Map.of();
        }
        return merge(Map.of(), entities);
    }

    public Map<String, Object> appendPending(Map<String, Object> currentPending, Map<String, Object> incomingPending) {
        return merge(currentPending, incomingPending);
    }

    public List<String> mergeCompletedSteps(List<String> currentSteps, List<String> newSteps) {
        List<String> merged = new ArrayList<>();
        if (currentSteps != null) {
            for (String step : currentSteps) {
                if (StringUtils.hasText(step) && !merged.contains(step)) {
                    merged.add(step);
                }
            }
        }
        if (newSteps != null) {
            for (String step : newSteps) {
                if (StringUtils.hasText(step) && !merged.contains(step)) {
                    merged.add(step);
                }
            }
        }
        return List.copyOf(merged);
    }

    private boolean shouldKeep(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private boolean equalsValue(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }
}
