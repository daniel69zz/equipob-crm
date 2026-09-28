package com.maxiconecta.crm.gateway.seguridad;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres-crm";

    private final JwtService jwtService = new JwtService(new JwtProperties(SECRETO, 60));

    @Test
    void emiteUnTokenQueContieneElRolYLosPermisos() {
        String token = jwtService.emitir("ana", "Ana Perez", "AGENTE_ATENCION",
                List.of("CLIENTE_CONSULTAR", "INTERACCIONES_REGISTRAR")).token();

        Optional<SesionToken> sesion = jwtService.validar(token);

        assertThat(sesion).isPresent();
        assertThat(sesion.get().usuario()).isEqualTo("ana");
        assertThat(sesion.get().nombreCompleto()).isEqualTo("Ana Perez");
        assertThat(sesion.get().rol()).isEqualTo("AGENTE_ATENCION");
        assertThat(sesion.get().permisos()).containsExactly("CLIENTE_CONSULTAR", "INTERACCIONES_REGISTRAR");
    }

    @Test
    void rechazaUnTokenAlterado() {
        String token = jwtService.emitir("ana", "Ana Perez", "AGENTE_ATENCION", List.of()).token();

        assertThat(jwtService.validar(token + "alterado")).isEmpty();
    }

    @Test
    void rechazaUnTokenFirmadoConOtraClave() {
        JwtService otroEmisor = new JwtService(new JwtProperties("otro-secreto-distinto-con-mas-de-32-caracteres", 60));
        String token = otroEmisor.emitir("ana", "Ana Perez", "ADMINISTRADOR_CRM", List.of()).token();

        assertThat(jwtService.validar(token)).isEmpty();
    }

    @Test
    void rechazaUnTokenVencido() {
        String vencido = Jwts.builder()
                .subject("ana")
                .claim("rol", "ADMINISTRADOR_CRM")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRETO.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtService.validar(vencido)).isEmpty();
    }

    @Test
    void noArrancaConUnSecretoCorto() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("corto", 60)))
                .isInstanceOf(IllegalStateException.class);
    }
}
