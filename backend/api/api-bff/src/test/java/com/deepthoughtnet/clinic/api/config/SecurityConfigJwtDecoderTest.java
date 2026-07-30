package com.deepthoughtnet.clinic.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityConfigJwtDecoderTest {

    private static final String LOCAL_ISSUER = "http://localhost:8182/auth/realms/clinic-management";
    private static final String PRODUCTION_ISSUER = "https://arogia.deepthoughtnet.com/auth/realms/clinic-management";

    private static HttpServer jwksServer;
    private static RSAKey signingKey;
    private static String jwkSetUri;

    @BeforeAll
    static void startJwkServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048)
                .keyID("clinic-test-key")
                .generate();
        jwksServer = HttpServer.create(new InetSocketAddress(0), 0);
        jwksServer.createContext("/jwks", SecurityConfigJwtDecoderTest::writeJwksResponse);
        jwksServer.start();
        jwkSetUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    }

    @AfterAll
    static void stopJwkServer() {
        if (jwksServer != null) {
            jwksServer.stop(0);
        }
    }

    @Test
    void localIssuerIsAcceptedWhenConfigured() throws Exception {
        Jwt jwt = decoderFor(LOCAL_ISSUER).decode(tokenFor(LOCAL_ISSUER, signingKey, signingKey.getKeyID()));

        assertThat(jwt.getIssuer().toString()).isEqualTo(LOCAL_ISSUER);
    }

    @Test
    void productionIssuerIsRejectedWhenLocalIssuerIsConfigured() throws Exception {
        assertThatThrownBy(() -> decoderFor(LOCAL_ISSUER).decode(tokenFor(PRODUCTION_ISSUER, signingKey, signingKey.getKeyID())))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Invalid issuer")
                .hasMessageContaining(PRODUCTION_ISSUER)
                .hasMessageContaining(LOCAL_ISSUER);
    }

    @Test
    void productionIssuerIsAcceptedWhenConfigured() throws Exception {
        Jwt jwt = decoderFor(PRODUCTION_ISSUER).decode(tokenFor(PRODUCTION_ISSUER, signingKey, signingKey.getKeyID()));

        assertThat(jwt.getIssuer().toString()).isEqualTo(PRODUCTION_ISSUER);
    }

    @Test
    void localhostIssuerIsRejectedWhenProductionIssuerIsConfigured() throws Exception {
        assertThatThrownBy(() -> decoderFor(PRODUCTION_ISSUER).decode(tokenFor(LOCAL_ISSUER, signingKey, signingKey.getKeyID())))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("Invalid issuer")
                .hasMessageContaining(LOCAL_ISSUER)
                .hasMessageContaining(PRODUCTION_ISSUER);
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        RSAKey forgedKey = new RSAKeyGenerator(2048)
                .keyID(signingKey.getKeyID())
                .generate();

        assertThatThrownBy(() -> decoderFor(LOCAL_ISSUER).decode(tokenFor(LOCAL_ISSUER, forgedKey, signingKey.getKeyID())))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Signed JWT rejected");
    }

    private static JwtDecoder decoderFor(String issuer) {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "primaryIssuer", issuer);
        ReflectionTestUtils.setField(config, "configuredJwkSetUri", jwkSetUri);
        return config.jwtDecoder();
    }

    private static String tokenFor(String issuer, RSAKey key, String keyId) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("user-123")
                .issueTime(Date.from(now.minusSeconds(30)))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(keyId)
                        .build(),
                claims
        );
        jwt.sign(new RSASSASigner(key.toPrivateKey()));
        return jwt.serialize();
    }

    private static void writeJwksResponse(HttpExchange exchange) throws IOException {
        byte[] body = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static final class RSAKeyGenerator extends com.nimbusds.jose.jwk.gen.RSAKeyGenerator {
        private RSAKeyGenerator(int keySize) {
            super(keySize);
        }
    }
}
