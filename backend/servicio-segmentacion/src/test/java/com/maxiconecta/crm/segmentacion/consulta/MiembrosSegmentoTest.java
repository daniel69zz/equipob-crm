package com.maxiconecta.crm.segmentacion.consulta;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SCRUM-378 / SCRUM-384 · Miembros de un segmento sobre PostgreSQL real (consulta e índice de V5). */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers
class MiembrosSegmentoTest {
    @Container static PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void base(DynamicPropertyRegistry r) {
        Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas("seguridad").locations("filesystem:../api-gateway/src/main/resources/db/migration").load().migrate();
        r.add("spring.datasource.url", db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void preparar() {
        jdbc.update("DELETE FROM segmentacion.segmento_cliente");
        jdbc.update("""
                INSERT INTO segmentacion.segmento_cliente (cliente_id, segmento) VALUES
                    (42, 'Frecuentes'), (31, 'Frecuentes'), (7, 'Frecuentes'), (15, 'Ocasionales')
                """);
    }

    @Test void devuelveLosDemasClientesDelMismoSegmentoOrdenados() throws Exception {
        mvc.perform(get("/api/segmentacion/clientes/42/segmento/miembros")).andExpect(status().isOk())
                .andExpect(jsonPath("$.segmento").value("Frecuentes"))
                .andExpect(jsonPath("$.asignadoEn").isNotEmpty())
                .andExpect(jsonPath("$.clientes", contains(7, 31)));
    }

    @Test void unSegmentoConUnSoloClienteNoTieneCompaneros() throws Exception {
        mvc.perform(get("/api/segmentacion/clientes/15/segmento/miembros")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(false))
                .andExpect(jsonPath("$.clientes").isEmpty());
    }

    @Test void clienteSinSegmentoRespondeSinDatos() throws Exception {
        mvc.perform(get("/api/segmentacion/clientes/99/segmento/miembros")).andExpect(status().isOk())
                .andExpect(jsonPath("$.sinDatos").value(true))
                .andExpect(jsonPath("$.clientes").isEmpty());
    }
}
