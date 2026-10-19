package com.maxiconecta.crm.comportamiento.sugerencia;

import com.maxiconecta.crm.comportamiento.preferencia.PreferenciasException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** SCRUM-384: lectura de los compañeros de segmento desde servicio-segmentacion. */
class MiembrosSegmentoTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://segmentacion");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final MiembrosSegmento miembros = new MiembrosSegmento(builder.build());
    private static final String URL = "http://segmentacion/api/segmentacion/clientes/42/segmento/miembros";

    @AfterEach
    void verificar() { servidor.verify(); }

    @Test
    void devuelveElSegmentoYLosCompanerosSinElPropioClienteNiRepetidos() {
        respuesta("{\"segmento\":\"Frecuentes\",\"sinDatos\":false,\"clientes\":[7,42,15,7]}");
        var segmento = miembros.obtener(42);
        assertThat(segmento.asignado()).isTrue();
        assertThat(segmento.nombre()).isEqualTo("Frecuentes");
        assertThat(segmento.companeros()).containsExactly(7L, 15L);
    }

    @Test
    void sinSegmentoNoEsUnError() {
        respuesta("{\"segmento\":null,\"sinDatos\":true,\"clientes\":[]}");
        assertThat(miembros.obtener(42).asignado()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"segmento\":\"\",\"sinDatos\":false,\"clientes\":[]}",
            "{\"segmento\":\"S\",\"sinDatos\":false,\"clientes\":[0]}"})
    void respuestaIncompletaNoSeConfundeConSinSegmento(String json) {
        respuesta(json);
        fallaCon503();
    }

    @Test
    void servicioCaidoDevuelve503() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        fallaCon503();
    }

    private void respuesta(String json) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void fallaCon503() {
        assertThatThrownBy(() -> miembros.obtener(42)).isInstanceOfSatisfying(PreferenciasException.class,
                ex -> assertThat(ex.estado()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
