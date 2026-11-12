package com.maxiconecta.crm.fidelizacion.parametro;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-519 · Persistencia de los parámetros generales con PostgreSQL real: el valor inicial sale de la
 * configuración, un cambio rige en la lectura siguiente, se rechaza lo que está fuera de rango y un cambio no pisa
 * otro hecho al mismo tiempo. Usa parámetros de prueba para no depender de los del servicio.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers(disabledWithoutDocker = true)
class ParametrosGeneralesIntegracionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final DefinicionParametro DIAS = DefinicionParametro.entero("PRUEBA_DIAS", "Días de prueba",
            "Un entero", "días", 1, 100, 10);
    private static final DefinicionParametro MONTO = new DefinicionParametro("PRUEBA_MONTO", "Monto de prueba",
            "Un importe", "Bs", new BigDecimal("0.01"), new BigDecimal("1000"), 2, new BigDecimal("50"));

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager gestor;

    private final RelojMovil reloj = new RelojMovil(Instant.parse("2026-11-12T12:00:00Z"));
    private ParametrosGenerales parametros;
    private final List<ParametrosGenerales.Modificacion> registrados = new java.util.ArrayList<>();

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM fidelizacion.parametro_general WHERE codigo LIKE 'PRUEBA_%'");
        parametros = nuevos();
        parametros.inicializar();
    }

    private ParametrosGenerales nuevos() {
        // El monto debe cubrir al menos un boliviano por día: una regla entre dos parámetros.
        return new ParametrosGenerales(jdbc, gestor, reloj, List.of(DIAS, MONTO), valores ->
                valores.get("PRUEBA_MONTO").compareTo(valores.get("PRUEBA_DIAS")) < 0
                        ? Map.of("PRUEBA_MONTO", "no puede ser menor que PRUEBA_DIAS") : Map.of(), registrados::add, evento -> { });
    }

    @Test
    void creaLosParametrosConElValorDeLaConfiguracionYNoPisaLosExistentes() {
        assertThat(parametros.entero("PRUEBA_DIAS")).isEqualTo(10);
        assertThat(parametros.listar()).extracting(Parametro::codigo, Parametro::valor, Parametro::version,
                Parametro::modificadoPor).containsExactly(
                org.assertj.core.groups.Tuple.tuple("PRUEBA_DIAS", new BigDecimal("10"), 1L,
                        ParametrosGenerales.USUARIO_INICIAL),
                org.assertj.core.groups.Tuple.tuple("PRUEBA_MONTO", new BigDecimal("50.00"), 1L,
                        ParametrosGenerales.USUARIO_INICIAL));

        parametros.actualizar(List.of(new CambioParametro("PRUEBA_DIAS", new BigDecimal("20"), 1L)), "admin");
        nuevos().inicializar();
        assertThat(nuevos().entero("PRUEBA_DIAS")).isEqualTo(20);
    }

    @Test
    void unCambioValidoRigeEnLaLecturaSiguienteYSubeLaVersion() {
        assertThat(parametros.entero("PRUEBA_DIAS")).isEqualTo(10);

        var aplicadas = parametros.actualizar(List.of(
                new CambioParametro("PRUEBA_DIAS", new BigDecimal("30"), 1L),
                new CambioParametro("PRUEBA_MONTO", new BigDecimal("50.00"), 1L)), "admin");

        assertThat(aplicadas).singleElement().satisfies(m -> {
            assertThat(m.definicion().codigo()).isEqualTo("PRUEBA_DIAS");
            assertThat(m.anterior()).isEqualByComparingTo("10");
            assertThat(m.nuevo()).isEqualByComparingTo("30");
            assertThat(m.usuario()).isEqualTo("admin");
        });
        assertThat(parametros.entero("PRUEBA_DIAS")).isEqualTo(30);
        assertThat(registrados).as("se registra solo el parámetro que cambió (SCRUM-521)")
                .extracting(m -> m.definicion().codigo()).containsExactly("PRUEBA_DIAS");
        Parametro dias = parametros.listar().get(0);
        assertThat(dias.version()).isEqualTo(2);
        assertThat(dias.modificadoPor()).isEqualTo("admin");
        assertThat(parametros.listar().get(1).version()).isEqualTo(1);
    }

    @Test
    void rechazaValoresFueraDeRangoConElMotivoPorParametro() {
        assertThatThrownBy(() -> parametros.actualizar(List.of(
                new CambioParametro("PRUEBA_DIAS", new BigDecimal("101"), 1L),
                new CambioParametro("PRUEBA_MONTO", new BigDecimal("10.555"), 1L)), "admin"))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campos())
                        .containsEntry("PRUEBA_DIAS", "debe estar entre 1 y 100 días")
                        .containsEntry("PRUEBA_MONTO", "admite hasta 2 decimales"));
        assertThatThrownBy(() -> parametros.actualizar(List.of(
                new CambioParametro("PRUEBA_DIAS", new BigDecimal("2.5"), 1L),
                new CambioParametro("OTRO", BigDecimal.ONE, 1L)), "admin"))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campos())
                        .containsEntry("PRUEBA_DIAS", "debe ser un número entero")
                        .containsEntry("OTRO", "no es un parámetro de este servicio"));
        assertThat(parametros.entero("PRUEBA_DIAS")).isEqualTo(10);
    }

    @Test
    void unaReglaEntreParametrosRechazaElConjuntoSinGuardarNada() {
        assertThatThrownBy(() -> parametros.actualizar(List.of(
                new CambioParametro("PRUEBA_DIAS", new BigDecimal("60"), 1L)), "admin"))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campos())
                        .containsEntry("PRUEBA_MONTO", "no puede ser menor que PRUEBA_DIAS"));
        assertThat(parametros.listar()).extracting(Parametro::version).containsExactly(1L, 1L);
    }

    @Test
    void unaVersionViejaNoPisaElCambioDeOtroUsuarioYNoSeAplicaNadaDeLaSolicitud() {
        parametros.actualizar(List.of(new CambioParametro("PRUEBA_MONTO", new BigDecimal("80"), 1L)), "ana");

        assertThatThrownBy(() -> parametros.actualizar(List.of(
                new CambioParametro("PRUEBA_DIAS", new BigDecimal("40"), 1L),
                new CambioParametro("PRUEBA_MONTO", new BigDecimal("90"), 1L)), "luis"))
                .isInstanceOfSatisfying(ParametroDesactualizadoException.class, e -> assertThat(e.campos())
                        .containsOnlyKeys("PRUEBA_MONTO"));
        assertThat(parametros.entero("PRUEBA_DIAS")).isEqualTo(10);
        assertThat(parametros.valor("PRUEBA_MONTO")).isEqualByComparingTo("80");
    }

    @Test
    void unaSolicitudSinCambiosOVaciaSeRechaza() {
        assertThatThrownBy(() -> parametros.actualizar(List.of(
                new CambioParametro("PRUEBA_DIAS", new BigDecimal("10"), 1L)), "admin"))
                .isInstanceOf(ParametroInvalidoException.class).hasMessageContaining("no hay cambios");
        assertThatThrownBy(() -> parametros.actualizar(List.of(), "admin"))
                .isInstanceOf(ParametroInvalidoException.class);
        assertThatThrownBy(() -> parametros.actualizar(List.of(
                new CambioParametro("PRUEBA_DIAS", new BigDecimal("11"), null)), "admin"))
                .isInstanceOfSatisfying(ParametroInvalidoException.class, e -> assertThat(e.campos())
                        .containsEntry("PRUEBA_DIAS", "falta la versión que se modificó"));
    }

    @Test
    void otraInstanciaVeElCambioAlVencerSuCopiaEnMemoria() {
        ParametrosGenerales otraInstancia = nuevos();
        assertThat(otraInstancia.entero("PRUEBA_DIAS")).isEqualTo(10);

        parametros.actualizar(List.of(new CambioParametro("PRUEBA_DIAS", new BigDecimal("15"), 1L)), "admin");

        assertThat(otraInstancia.entero("PRUEBA_DIAS")).isEqualTo(10);
        reloj.avanzar(ParametrosGenerales.VIGENCIA_COPIA.getSeconds());
        assertThat(otraInstancia.entero("PRUEBA_DIAS")).isEqualTo(15);
    }

    /** Reloj que la prueba adelanta a mano. */
    static final class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant inicio) {
            ahora = inicio;
        }

        void avanzar(long segundos) {
            ahora = ahora.plusSeconds(segundos);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
