package com.maxiconecta.crm.segmentacion.historial;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** Prueba una base previa a SCRUM-30; no parte de una tabla de historial ya creada. */
@Testcontainers
class MigracionHistorialSegmentosTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void importaAuditoriaPreviaSinInventarVersionYNoDuplicaAlVolverAMigrar() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load().migrate();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("segmentacion").locations("classpath:db/migration").target("3").load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.update("""
                INSERT INTO auditoria.evento(ocurrido_en,usuario,operacion,entidad,entidad_id,detalle)
                VALUES ('2026-10-10T12:00:00Z','sistema','SEGMENTO_CAMBIADO','SEGMENTO_CLIENTE','42',
                    '{"anterior":{"segmento":"Inicial"},"nuevo":{"segmento":"Habitual","criterioId":77,"indicador":"FRECUENCIA"},
                      "metricas":{"frecuencia":3,"valor":100,"categorias":[]},"idEvento":"evento-legado","tipoEventoOrigen":"COMPRA_CONFIRMADA"}')
                """);
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("segmentacion").locations("classpath:db/migration").load();
        flyway.migrate();
        var registro = jdbc.queryForMap("""
                SELECT segmento_anterior, segmento_nuevo, regla_completa, regla->>'version' AS version,
                       regla->>'criterioId' AS criterio, fecha::text, id_evento
                FROM segmentacion.historial_segmento WHERE cliente_id=42
                """);
        assertThat(registro).containsEntry("segmento_anterior", "Inicial").containsEntry("segmento_nuevo", "Habitual")
                .containsEntry("regla_completa", false).containsEntry("version", null).containsEntry("criterio", "77")
                .containsEntry("id_evento", "evento-legado");
        assertThat(jdbc.queryForObject("SELECT fecha FROM segmentacion.historial_segmento WHERE cliente_id=42",
                java.time.OffsetDateTime.class).toInstant()).isEqualTo(java.time.Instant.parse("2026-10-10T12:00:00Z"));
        flyway.migrate();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmentacion.historial_segmento", Long.class)).isEqualTo(1);
    }
}
