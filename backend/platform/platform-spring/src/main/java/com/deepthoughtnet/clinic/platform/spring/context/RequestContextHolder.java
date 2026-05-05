package com.deepthoughtnet.clinic.platform.spring.context;

import com.deepthoughtnet.clinic.platform.core.context.RequestContext;
import java.util.UUID;

public final class RequestContextHolder {
    private static final InheritableThreadLocal<RequestContext> CTX = new InheritableThreadLocal<>();

    private RequestContextHolder() {}

    public static void set(RequestContext ctx) { CTX.set(ctx); }
    public static RequestContext get() { return CTX.get(); }
    public static void clear() { CTX.remove(); }

    /** Prefer using this in services/resolvers to avoid NPEs. */
    public static RequestContext require() {
        RequestContext ctx = CTX.get();
        if (ctx == null) {
            throw new IllegalStateException("Missing RequestContext (tenant header / interceptor not applied?)");
        }
        return ctx;
    }

    public static UUID requireTenantId() {
        return require().tenantId().value();
    }
}
