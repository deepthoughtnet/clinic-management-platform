package com.deepthoughtnet.clinic.api.errors;

import com.deepthoughtnet.clinic.platform.core.errors.BadRequestException;
import com.deepthoughtnet.clinic.platform.core.errors.ForbiddenException;
import com.deepthoughtnet.clinic.platform.core.errors.UnauthorizedException;
import com.deepthoughtnet.clinic.appointment.service.model.DoctorAvailabilityConflictException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.deepthoughtnet.clinic.identity.exception.TenantModuleDisabledException;
import com.deepthoughtnet.clinic.platform.spring.context.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import jakarta.validation.ConstraintViolationException;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalRestExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalRestExceptionHandler.class);
    private static final Pattern UUID_PATTERN = Pattern.compile("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<?> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", userMessage(ex.getMessage(), "Invalid request"), req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<?> handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "unauthorized", userMessage(ex.getMessage(), "Authentication is required"), req);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<?> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "forbidden", userMessage(ex.getMessage(), "You do not have permission to perform this action"), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to perform this action", req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> messages = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                messages.add(fe.getField() + ": " + userMessage(fe.getDefaultMessage(), "Invalid value"))
        );
        ex.getBindingResult().getGlobalErrors().forEach(err ->
                messages.add(userMessage(err.getDefaultMessage(), "Validation failed"))
        );
        String message = messages.isEmpty() ? "Validation failed" : String.join(", ", messages);
        return build(HttpStatus.BAD_REQUEST, "validation_failed", message, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleBadJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String message = "Malformed JSON request body";
        Throwable root = ex.getMostSpecificCause();
        if (root instanceof JsonMappingException mappingException) {
            String path = jsonPath(mappingException);
            if (path != null && !path.isBlank()) {
                message = "Invalid value for " + path;
            } else {
                message = "Invalid value in JSON request body";
            }
            return build(HttpStatus.BAD_REQUEST, "invalid_json", message, req);
        }
        if (root != null && root.getMessage() != null) {
            String msg = root.getMessage().toLowerCase(Locale.ROOT);
            if (msg.contains("invalid")) {
                message = "Malformed or invalid JSON request body";
            }
        }
        return build(HttpStatus.BAD_REQUEST, "invalid_json", message, req);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<?> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "missing_header", "Missing required header: " + ex.getHeaderName(), req);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "file_too_large", "Uploaded file exceeds the maximum allowed size", req);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<?> handleMultipart(MultipartException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "invalid_multipart", "Invalid multipart upload request", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String field = ex.getName() == null ? "parameter" : ex.getName();
        String message = "Invalid value for " + field;
        if (ex.getRequiredType() != null && ex.getValue() != null) {
            message = "Invalid value for " + field + ". Expected " + ex.getRequiredType().getSimpleName();
        }
        return build(HttpStatus.BAD_REQUEST, "invalid_parameter", message, req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "missing_parameter", "Missing required parameter: " + ex.getParameterName(), req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> {
                    String field = violation.getPropertyPath() == null ? "value" : violation.getPropertyPath().toString();
                    return field + ": " + userMessage(violation.getMessage(), "Invalid value");
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, "validation_failed", message, req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String code = codeFor(status);
        return build(status, code, userMessage(ex.getReason(), defaultMessageFor(status)), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", userMessage(ex.getMessage(), "Invalid request"), req);
    }

    @ExceptionHandler(TenantModuleDisabledException.class)
    public ResponseEntity<?> handleTenantModuleDisabled(TenantModuleDisabledException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "module_disabled", "AI module is not enabled for this clinic.", req);
    }

    @ExceptionHandler(DoctorAvailabilityConflictException.class)
    public ResponseEntity<?> handleDoctorAvailabilityConflict(DoctorAvailabilityConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "conflict", userMessage(ex.getMessage(), "Availability already exists for this doctor, day, and time range."), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleUnhandled(Exception ex, HttpServletRequest req) {
        if (isClientDisconnect(ex)) {
            log.warn(
                    "voice.response.client-disconnected requestId={} correlationId={} {} {} error={}",
                    correlationId(req),
                    correlationId(req),
                    req.getMethod(),
                    req.getRequestURI(),
                    summarizeDisconnect(ex)
            );
            return ResponseEntity.noContent().build();
        }
        log.error(
                "Unhandled exception requestId={} correlationId={} {} {}",
                correlationId(req),
                correlationId(req),
                req.getMethod(),
                req.getRequestURI(),
                ex
        );
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error", req);
    }

    private ResponseEntity<?> build(HttpStatus status, String code, String message, HttpServletRequest req) {
        String correlationId = correlationId(req);
        ApiError body = ApiError.of(status.value(), code, message, path(req), correlationId);
        MediaType responseType = errorResponseMediaType(req);
        if (MediaType.TEXT_PLAIN.includes(responseType)) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(message);
        }
        return ResponseEntity.status(status)
                .contentType(responseType)
                .body(body);
    }

    private String jsonPath(JsonMappingException ex) {
        if (ex == null || ex.getPath() == null || ex.getPath().isEmpty()) {
            return null;
        }
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null && !ref.getFieldName().isBlank()) {
                if (path.length() > 0) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.length() == 0 ? null : path.toString();
    }

    private MediaType errorResponseMediaType(HttpServletRequest req) {
        String accept = Optional.ofNullable(req)
                .map(request -> request.getHeader(HttpHeaders.ACCEPT))
                .orElse("");
        if (accept == null || accept.isBlank() || accept.contains(MediaType.ALL_VALUE)) {
            return MediaType.APPLICATION_JSON;
        }
        if (accept.contains(MediaType.APPLICATION_PDF_VALUE)
                || accept.contains(MediaType.TEXT_HTML_VALUE)
                || accept.contains(MediaType.TEXT_PLAIN_VALUE)) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_JSON;
    }

    private String path(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        return req.getRequestURI();
    }

    private String correlationId(HttpServletRequest req) {
        String correlationId = req == null ? null : req.getHeader(CorrelationId.HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationId.MDC_KEY);
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "unknown";
        }
        return correlationId;
    }

    private String codeFor(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "unauthorized";
            case FORBIDDEN -> "forbidden";
            case NOT_FOUND -> "not_found";
            case CONFLICT -> "conflict";
            case BAD_REQUEST -> "bad_request";
            default -> "error";
        };
    }

    private String defaultMessageFor(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> "Authentication is required";
            case FORBIDDEN -> "You do not have permission to perform this action";
            case NOT_FOUND -> "Requested resource was not found";
            case CONFLICT -> "Request could not be completed due to a conflict";
            case BAD_REQUEST -> "Invalid request";
            default -> "Request failed";
        };
    }

    private String userMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        String normalized = redactInternalReferences(message.trim());
        if (normalized.startsWith("No enum constant")
                || normalized.contains("org.springframework")
                || normalized.contains("java.lang.reflect")
                || normalized.contains("Cannot invoke")
                || normalized.contains("could not deserialize")) {
            return fallback;
        }
        return normalized;
    }

    private String redactInternalReferences(String message) {
        if (message == null || message.isBlank()) {
            return message;
        }
        return UUID_PATTERN.matcher(message).replaceAll("[hidden reference]");
    }

    private boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if ("org.springframework.web.context.request.async.AsyncRequestNotUsableException".equals(className)) {
                return true;
            }
            if (current instanceof IOException && containsDisconnectSignal(message)) {
                return true;
            }
            if (containsDisconnectSignal(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsDisconnectSignal(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset by peer")
                || normalized.contains("connection aborted")
                || normalized.contains("clientabortexception")
                || normalized.contains("asyncrequestnotusableexception");
    }

    private String summarizeDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getClass().getSimpleName() + ": " + current.getMessage();
            }
            current = current.getCause();
        }
        return throwable.getClass().getSimpleName();
    }
}
