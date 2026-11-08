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
 * Emite y valida los tokens JWT del inicio de sesión y los de servicio (firma HMAC-SHA256). Un token de
 * servicio (SCRUM-545) lleva {@code tipo=SERVICIO}: identifica a un sistema del ERP, no a un usuario interno.
 */
@Service
public class JwtService {

    private static final int LONGITUD_MINIMA_SECRETO = 32;
    private static final String TIPO = "tipo";
    private static final String SERVICIO = "SERVICIO";

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
        return emitir(usuario, nombreCompleto, rol, permisos, false);
    }

    /** Token de un sistema del ERP (SCRUM-545), con la misma duración que el de un usuario. */
    public TokenEmitido emitirServicio(SesionToken cliente) {
        return emitir(cliente.usuario(), cliente.nombreCompleto(), cliente.rol(), cliente.permisos(), true);
    }

    public Optional<SesionToken> validar(String token) {
        return verificar(token).map(TokenVerificado::sesion);
    }

    /** @return los datos del token y si es de servicio; vacío si la firma no vale o ya venció */
    public Optional<TokenVerificado> verificar(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
            Object permisosEnToken = claims.getOrDefault("permisos", List.of());
            List<String> permisos = permisosEnToken instanceof List<?> lista
                    ? lista.stream().map(String::valueOf).toList()
                    : List.of();
            SesionToken sesion = new SesionToken(claims.getSubject(), claims.get("nombre", String.class),
                    claims.get("rol", String.class), permisos);
            return Optional.of(new TokenVerificado(sesion, SERVICIO.equals(claims.get(TIPO, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private TokenEmitido emitir(String sujeto, String nombre, String rol, Collection<String> permisos, boolean servicio) {
        Instant ahora = Instant.now();
        Instant expiraEn = ahora.plus(duracion);
        var constructor = Jwts.builder()
                .subject(sujeto)
                .claim("nombre", nombre)
                .claim("rol", rol)
                .claim("permisos", List.copyOf(permisos));
        if (servicio) {
            constructor.claim(TIPO, SERVICIO);
        }
        String token = constructor.issuedAt(Date.from(ahora)).expiration(Date.from(expiraEn)).signWith(clave).compact();
        return new TokenEmitido(token, expiraEn);
    }

    public record TokenEmitido(String token, Instant expiraEn) {
    }

    /** {@code servicio} distingue el token de un sistema del ERP del de un usuario interno. */
    public record TokenVerificado(SesionToken sesion, boolean servicio) {
    }
}
