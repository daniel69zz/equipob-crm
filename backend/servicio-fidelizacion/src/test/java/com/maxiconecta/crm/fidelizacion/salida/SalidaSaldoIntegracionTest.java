package com.maxiconecta.crm.fidelizacion.salida;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.pubsub.v1.PubsubMessage;
import com.maxiconecta.crm.fidelizacion.ajuste.ServicioAjustes;
import com.maxiconecta.crm.fidelizacion.ajuste.SolicitudAjuste;
import com.maxiconecta.crm.fidelizacion.caducidad.ProcesoCaducidad;
import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import com.maxiconecta.crm.fidelizacion.movimiento.IngestaMovimientos;
import com.maxiconecta.crm.fidelizacion.nivel.ServicioNiveles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SCRUM-622: PostgreSQL real, transporte controlado para reproducir caidas y reintentos. */
@SpringBootTest(properties = {"fidelizacion.salida.enabled=true", "fidelizacion.salida.intervalo-ms=3600000",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
@Testcontainers(disabledWithoutDocker = true)
class SalidaSaldoIntegracionTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean ClientesPerfil perfiles;
    @MockBean PubSubTemplate pubsub;
    @Autowired JdbcTemplate jdbc;
    @Autowired IngestaMovimientos ingesta;
    @Autowired PublicadorSaldoPubSub publicador;
    @Autowired ServicioAjustes ajustes;
    @Autowired ProcesoCaducidad caducidad;
    @Autowired ServicioNiveles niveles;
    @Autowired PlatformTransactionManager gestor;
    @Autowired ObjectMapper json;

    @BeforeAll
    static void auditoriaCompartida() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration")
                .load().migrate();
    }

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE fidelizacion.salida_saldo_puntos, fidelizacion.movimiento_puntos, "
                + "fidelizacion.evento_movimiento, fidelizacion.saldo_puntos, fidelizacion.ajuste_puntos, "
                + "fidelizacion.cambio_nivel");
        when(perfiles.buscar(anyString())).thenReturn(Optional.of(42L));
        doReturn("CLI-5521").when(perfiles).identificador(42);
        when(pubsub.publish(anyString(), any(PubsubMessage.class)))
                .thenReturn(CompletableFuture.completedFuture("mensaje-1"));
    }

    @Test
    void guardaInstantaneasDeAcumulacionRedencionReversaYAjuste() throws Exception {
        assertThat(ingesta.recibir(evento("A", "ACUMULACION", "100", "0", null, OffsetDateTime.now())))
                .isEqualTo(PROCESADO);
        assertThat(ingesta.recibir(evento("R", "REDENCION", "10", "2", null, OffsetDateTime.now())))
                .isEqualTo(PROCESADO);
        assertThat(ingesta.recibir(evento("V", "REVERSA", "20", "0", "A", OffsetDateTime.now())))
                .isEqualTo(PROCESADO);
        ajustes.ajustar(42, new SolicitudAjuste(UUID.randomUUID(), 4L, "Correccion autorizada", null, 6L),
                "administrador", "ADMINISTRADOR_CRM");
        assertThat(jdbc.queryForList("SELECT contenido->>'saldo' FROM fidelizacion.salida_saldo_puntos ORDER BY secuencia",
                String.class)).containsExactly("10", "8", "6", "10");
        assertThat(jdbc.queryForList("SELECT tipo_movimiento FROM fidelizacion.salida_saldo_puntos ORDER BY secuencia",
                String.class)).containsExactly("ACUMULACION", "REDENCION", "REVERSA", "AJUSTE");
        verifyNoInteractions(pubsub);
        publicador.publicarPendientes();
        ArgumentCaptor<PubsubMessage> mensajes = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(pubsub, times(4)).publish(eq("ucb-sis323-sub-b"), mensajes.capture());
        for (PubsubMessage mensaje : mensajes.getAllValues()) {
            var cuerpo = json.readTree(mensaje.getData().toStringUtf8());
            assertThat(cuerpo.get("idCliente").asText()).isEqualTo("CLI-5521");
            assertThat(cuerpo.has("nivel")).isTrue();
            assertThat(UUID.fromString(cuerpo.get("idEvento").asText())).isNotNull();
            assertThat(mensaje.getAttributesOrThrow("ruta")).isEqualTo(PublicadorSaldoPubSub.RUTA);
        }
        assertThat(contar("publicado_en IS NOT NULL")).isEqualTo(4);
    }

    @Test
    void duplicadosYRechazadosNoGeneranSalida() {
        String compra = evento("A", "ACUMULACION", "100", "0", null, OffsetDateTime.now());
        assertThat(ingesta.recibir(compra)).isEqualTo(PROCESADO);
        assertThat(ingesta.recibir(compra)).isEqualTo(DESCARTADO);
        assertThat(ingesta.recibir(evento("R", "REDENCION", "1", "999", null, OffsetDateTime.now())))
                .isEqualTo(FALLIDO);
        assertThat(contar("true")).isEqualTo(1);
    }

    @Test
    void caidaDelBrokerNoRevierteSaldoYReintentaConElMismoId() {
        when(pubsub.publish(anyString(), any(PubsubMessage.class))).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("Pub/Sub no disponible")),
                CompletableFuture.completedFuture("recuperado"));
        ingesta.recibir(evento("A", "ACUMULACION", "100", "0", null, OffsetDateTime.now()));
        ingesta.recibir(evento("B", "ACUMULACION", "50", "0", null, OffsetDateTime.now()));
        publicador.publicarPendientes();
        assertThat(jdbc.queryForObject("SELECT saldo FROM fidelizacion.saldo_puntos WHERE cliente_id=42", Long.class))
                .isEqualTo(15);
        assertThat(contar("intentos=1 AND publicado_en IS NULL AND ultimo_error IS NOT NULL")).isEqualTo(1);
        verify(pubsub, times(1)).publish(anyString(), any(PubsubMessage.class));
        jdbc.update("UPDATE fidelizacion.salida_saldo_puntos SET proximo_intento=now()");
        publicador.publicarPendientes();
        ArgumentCaptor<PubsubMessage> enviados = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(pubsub, times(3)).publish(anyString(), enviados.capture());
        assertThat(enviados.getAllValues().get(0)).isEqualTo(enviados.getAllValues().get(1));
        assertThat(contar("publicado_en IS NOT NULL")).isEqualTo(2);
    }

    @Test
    void rollbackDeshaceMovimientoSaldoYEvento() {
        new TransactionTemplate(gestor).executeWithoutResult(estado -> {
            ingesta.recibir(evento("A", "ACUMULACION", "100", "0", null, OffsetDateTime.now()));
            assertThat(contar("true")).isEqualTo(1);
            estado.setRollbackOnly();
        });
        assertThat(contar("true")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fidelizacion.saldo_puntos", Long.class)).isZero();
        publicador.publicarPendientes();
        verifyNoInteractions(pubsub);
    }

    @Test
    void caducidadYCambioDeNivelTambienGeneranEventos() {
        ingesta.recibir(evento("ANTIGUA", "ACUMULACION", "100", "0", null, OffsetDateTime.now().minusYears(2)));
        assertThat(caducidad.caducarVencidos().clientesConPuntosCaducados()).isEqualTo(1);
        assertThat(contar("tipo_movimiento='CADUCIDAD' AND contenido->>'saldo'='0'")).isEqualTo(1);
        jdbc.update("UPDATE fidelizacion.saldo_puntos SET nivel='ORO' WHERE cliente_id=42");
        assertThat(niveles.recalcularTodos()).isEqualTo(1);
        assertThat(contar("tipo_movimiento='RECALCULO_NIVEL'")).isEqualTo(1);
        assertThat(niveles.recalcularTodos()).isZero();
        assertThat(contar("tipo_movimiento='RECALCULO_NIVEL'")).isEqualTo(1);
    }

    @Test
    void dosDespachadoresNoPublicanLaMismaFila() throws Exception {
        ingesta.recibir(evento("A", "ACUMULACION", "100", "0", null, OffsetDateTime.now()));
        var ejecutor = Executors.newFixedThreadPool(2);
        try {
            var primero = ejecutor.submit(publicador::publicarPendientes);
            var segundo = ejecutor.submit(publicador::publicarPendientes);
            primero.get(10, TimeUnit.SECONDS);
            segundo.get(10, TimeUnit.SECONDS);
        } finally {
            ejecutor.shutdownNow();
        }
        verify(pubsub, times(1)).publish(anyString(), any(PubsubMessage.class));
    }

    @Test
    void perfilNoDisponibleDejaElAjusteConfirmadoYElEventoPendiente() {
        ajustes.ajustar(42, new SolicitudAjuste(UUID.randomUUID(), 4L, "Correccion autorizada", null, 0L),
                "administrador", "ADMINISTRADOR_CRM");
        when(perfiles.identificador(42)).thenThrow(new IllegalStateException("Perfil no disponible"));
        publicador.publicarPendientes();
        assertThat(contar("publicado_en IS NULL AND intentos=1")).isEqualTo(1);
        verifyNoInteractions(pubsub);
        doReturn("CLI-5521").when(perfiles).identificador(42);
        jdbc.update("UPDATE fidelizacion.salida_saldo_puntos SET proximo_intento=now()");
        publicador.publicarPendientes();
        assertThat(contar("publicado_en IS NOT NULL")).isEqualTo(1);
    }

    private long contar(String condicion) {
        return jdbc.queryForObject("SELECT count(*) FROM fidelizacion.salida_saldo_puntos WHERE " + condicion, Long.class);
    }

    static String evento(String id, String tipo, String monto, String puntos, String original, OffsetDateTime fecha) {
        return """
                {"idEvento":"%s","tipoEvento":"MOVIMIENTO_PUNTOS","fechaEmision":"%s",
                 "movimiento":{"idTransaccion":"%s","idCliente":"CLI-5521","tipoMovimiento":"%s",
                 "valorConsumido":%s,"puntos":%s,"idTransaccionOriginal":%s,"fecha":"%s"}}
                """.formatted(UUID.randomUUID(), fecha, id, tipo, monto, puntos,
                original == null ? "null" : "\"" + original + "\"", fecha);
    }
}
