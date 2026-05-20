package com.deepthoughtnet.clinic.llm.spi;

public class AiProviderException extends RuntimeException {
    private final boolean retryable;
    private final Integer statusCode;
    private final String providerName;
    private final String model;
    private final String endpointPath;

    public AiProviderException(String message,
                               boolean retryable,
                               Integer statusCode,
                               String providerName,
                               String model,
                               String endpointPath,
                               Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.statusCode = statusCode;
        this.providerName = providerName;
        this.model = model;
        this.endpointPath = endpointPath;
    }

    public static AiProviderException retryable(String message,
                                                Integer statusCode,
                                                String providerName,
                                                String model,
                                                String endpointPath,
                                                Throwable cause) {
        return new AiProviderException(message, true, statusCode, providerName, model, endpointPath, cause);
    }

    public static AiProviderException fatal(String message,
                                            Integer statusCode,
                                            String providerName,
                                            String model,
                                            String endpointPath,
                                            Throwable cause) {
        return new AiProviderException(message, false, statusCode, providerName, model, endpointPath, cause);
    }

    public boolean retryable() {
        return retryable;
    }

    public Integer statusCode() {
        return statusCode;
    }

    public String providerName() {
        return providerName;
    }

    public String model() {
        return model;
    }

    public String endpointPath() {
        return endpointPath;
    }
}
