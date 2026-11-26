package com.luxray.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Genera y valida JWT firmados con HS256.
 * Configurado por propiedad: luxray.security.jwt.secret y luxray.security.jwt.expiration-minutes.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** Secreto de desarrollo conocido en código: nunca debe usarse en producción. */
    private static final String[] SECRETOS_INSEGUROS = {
            "luxray-default-very-long-secret-please-change-in-prod-min-32chars",
            "luxray-jwt-super-secreto-para-desarrollo-min-32caracteres"
    };

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${luxray.security.jwt.secret:luxray-default-very-long-secret-please-change-in-prod-min-32chars}") String secret,
            @Value("${luxray.security.jwt.expiration-minutes:1440}") long expirationMinutes) {
        for (String inseguro : SECRETOS_INSEGUROS) {
            if (inseguro.equals(secret)) {
                log.warn("ADVERTENCIA DE SEGURIDAD: el secreto JWT es el valor de desarrollo por defecto. "
                        + "Define la variable JWT_SECRET con un secreto aleatorio (>= 32 bytes) en producción.");
            }
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMinutes * 60_000L;
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(exp)
                .claims(claims)
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getSubject(String token) {
        return parse(token).getSubject();
    }

    public long getExpirationMs() { return expirationMs; }
}
