package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * SCRUM-339 · Clasificación por frecuencia del cliente, consultada en servicio-comportamiento para la
 * ficha integral (SCRUM-38).
 */
class ClasificacionFrecuenciaDelClienteTest {

    private static final String RUTA = "http://comportamiento.test/api/comportamiento/clientes/42/indicadores"
            + "?identificador=mp-user-1";
    private static final List<ClienteOrigen> UN_IDENTIFICADOR = List.of(
            new ClienteOrigen("mp-user-1", 42L, OffsetDateTime.now()));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento.test");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ClasificacionFrecuenciaDelCliente clasificacion = new ClasificacionFrecuenciaDelCliente(builder.build());

    @Test
    void devuelveLaClasificacionQueCalculaComportamientoConSuFrecuencia() {
        servidor.expect(requestTo(RUTA))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"frecuencia":12,"nivelConsumo":{"nivel":"ALTO","valorAcumulado":5230.00,"sinNivel":false},
                        "clasificacionFrecuencia":{"clasificacion":"FRECUENTE","frecuencia":12,"sinClasificacion":false}}
                        """, MediaType.APPLICATION_JSON));

        ClasificacionFrecuenciaResponse respuesta = clasificacion.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta).isEqualTo(new ClasificacionFrecuenciaResponse("FRECUENTE", 12L, false));
    }

    @Test
    void unClienteSinComprasVigentesQuedaSinClasificacion() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withSuccess("""
                        {"clasificacionFrecuencia":{"clasificacion":null,"frecuencia":0,"sinClasificacion":true}}
                        """, MediaType.APPLICATION_JSON));

        ClasificacionFrecuenciaResponse respuesta = clasificacion.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isTrue();
        assertThat(respuesta.clasificacion()).isNull();
    }

    @Test
    void unaVersionDeComportamientoSinElBloqueQuedaSinClasificacion() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withSuccess("{\"frecuencia\":3}", MediaType.APPLICATION_JSON));

        assertThat(clasificacion.buscar(42L, UN_IDENTIFICADOR).sinDatos()).isTrue();
    }

    @Test
    void sinIdentificadoresDeOrigenNoConsultaYQuedaSinClasificacion() {
        ClasificacionFrecuenciaResponse respuesta = clasificacion.buscar(42L, List.of());

        assertThat(respuesta.sinDatos()).isTrue();
        servidor.verify();
    }

    @Test
    void siComportamientoNoRespondeLaFichaSigueCargandoSinEseBloque() {
        servidor.expect(requestTo(RUTA)).andRespond(withServerError());

        ClasificacionFrecuenciaResponse respuesta = clasificacion.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isTrue();
    }
}
