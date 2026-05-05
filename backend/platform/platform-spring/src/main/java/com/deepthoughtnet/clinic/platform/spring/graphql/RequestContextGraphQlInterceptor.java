package com.deepthoughtnet.clinic.platform.spring.graphql;

import com.deepthoughtnet.clinic.platform.spring.context.RequestContextHolder;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;


/**
 * Ensures RequestContext is available in GraphQL execution as well.
 * REST filters already set RequestContextHolder; this bridges it for GraphQL pipeline.
 */
@Slf4j
public class RequestContextGraphQlInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        // If RequestContext was already set by servlet filter, expose it via GraphQL context.
        log.info(" ** GraphQL Interceptor HIT. thread={}", Thread.currentThread().getName());
        var ctx = RequestContextHolder.get();
        log.info("GraphQL Interceptor ctxPresent={}", ctx != null);
        if (ctx != null) {
            request.configureExecutionInput((executionInput, builder) ->
                    builder.graphQLContext(graphQLContextBuilder ->
                            graphQLContextBuilder.put("requestContext", ctx)
                    ).build()
            );
        }
        return chain.next(request);
    }
}
