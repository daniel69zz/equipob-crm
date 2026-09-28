package com.maxiconecta.crm.gateway.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Emite y valida los tokens JWT del inicio de sesión (firma HMAC-SHA256).
 */
@Service
public class JwtService {

    private static final int LONGITUD_MINIMA_SECRETO = 32;

    private final SecretKey clave;
    private final Duration duracion;

    public JwtService(JwtProperties propiedades) {
        String secreto = propiedades.secreto();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException(
                    "crm.seguridad.jwt.secreto (JWT_SECRETO) debe tener al menos " + LONGITUD_MINIMA_SECRETO + " caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofMinutes(propiedades.expiracionMinutos() > 0 ? propiedades.expiracionMinutos() : 60);
    }

    public TokenEmitido emitir(String usuario, String nombreCompleto, String rol, Collection<String> permisos) {
        Instant ahora = Instant.now();
        Instant expiraEn = ahora.plus(duracion);
        String token = Jwts.builder()
                .subject(usuario)
                .claim("nombre", nombreCompleto)
                .claim("rol", rol)
                .claim("permisos", List.copyOf(permisos))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiraEn))
                .signWith(clave)
                .compact();
        return new TokenEmitido(token, expiraEn);
    }

    public Optional<SesionToken> validar(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
            Object permisosEnToken = claims.getOrDefault("permisos", List.of());
            List<String> permisos = permisosEnToken instanceof List<?> lista
                    ? lista.stream().map(String::valueOf).toList()
                    : List.of();
            return Optional.of(new SesionToken(claims.getSubject(), claims.get("nombre", String.class),
                    claims.get("rol", String.class), permisos));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record TokenEmitido(String token, Instant expiraEn) {
    }
}
