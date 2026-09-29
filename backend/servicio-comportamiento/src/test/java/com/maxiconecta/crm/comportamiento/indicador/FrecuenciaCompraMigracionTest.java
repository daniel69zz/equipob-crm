package com.maxiconecta.crm.comportamiento.indicador;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-37 · La migración inicializa el contador desde las compras vigentes ya almacenadas. */
@Testcontainers(disabledWithoutDocker = true)
class FrecuenciaCompraMigracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void confirmadaYDevolucionParcialCuentanPeroAnuladaNo() {
        Flyway hastaVersionSeis = flyway().target(MigrationVersion.fromVersion("6")).load();
        hastaVersionSeis.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        Long evento = jdbc.queryForObject("""
                INSERT INTO comportamiento.evento_recibido (tipo_evento, estado, contenido)
                VALUES ('COMPRA_CONFIRMADA', 'PROCESADO', '{}') RETURNING id
                """, Long.class);
        insertarCompra(jdbc, evento, "COMPRA-CONFIRMADA", "CONFIRMADA", "100.00", "0.00");
        insertarCompra(jdbc, evento, "COMPRA-PARCIAL", "DEVOLUCION_PARCIAL", "100.00", "30.00");
        insertarCompra(jdbc, evento, "COMPRA-ANULADA", "ANULADA", "100.00", "100.00");

        flyway().load().migrate();

        assertThat(jdbc.queryForObject("""
                SELECT cantidad FROM comportamiento.frecuencia_compra
                WHERE id_cliente_origen = 'CLI-5521'
                """, Long.class)).isEqualTo(2L);
    }

    private static void insertarCompra(JdbcTemplate jdbc, Long evento, String idCompra,
                                       String estado, String montoTotal, String montoRevertido) {
        jdbc.update("""
                INSERT INTO comportamiento.compra
                    (id_compra_origen, id_cliente_origen, fecha, monto_total, estado,
                     monto_revertido, id_evento)
                VALUES (?, 'CLI-5521', now(), ?::numeric, ?, ?::numeric, ?)
                """, idCompra, montoTotal, estado, montoRevertido, evento);
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("comportamiento")
                .locations("classpath:db/migration");
    }
}
