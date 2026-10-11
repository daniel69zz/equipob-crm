package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
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
 * SCRUM-567 · Alertas de cambio de comportamiento del cliente, consultadas en servicio-comportamiento
 * para la ficha integral (SCRUM-34).
 */
class CambiosComportamientoDelClienteTest {

    private static final String RUTA = "http://comportamiento.test/api/comportamiento/clientes/42/cambios-comportamiento"
            + "?identificador=mp-user-1";
    private static final List<ClienteOrigen> UN_IDENTIFICADOR = List.of(
            new ClienteOrigen("mp-user-1", 42L, OffsetDateTime.now()));

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento.test");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final CambiosComportamientoDelCliente cambios = new CambiosComportamientoDelCliente(builder.build());

    @Test
    void devuelveElEstadoYLasAlertasQueDetectoComportamiento() {
        servidor.expect(requestTo(RUTA))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"estado":"CON_VARIACION","evaluadoEn":"2026-10-10T04:00:00Z","alertas":[
                          {"id":18,"idClienteOrigen":"mp-user-1","tipo":"FRECUENCIA","direccion":"DISMINUCION",
                           "valorActual":1.00,"valorHabitual":4.00,"variacion":-0.75,"umbral":0.5,
                           "periodoDesde":"2026-07-12T04:00:00Z","periodoHasta":"2026-10-10T04:00:00Z",
                           "detectadoEn":"2026-10-10T04:00:00Z",
                           "mensaje":"La frecuencia de compra bajó un 75 % respecto a lo habitual"}]}
                        """, MediaType.APPLICATION_JSON));

        CambiosComportamientoResponse respuesta = cambios.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isFalse();
        assertThat(respuesta.estado()).isEqualTo("CON_VARIACION");
        assertThat(respuesta.evaluadoEn()).isEqualTo(OffsetDateTime.parse("2026-10-10T04:00:00Z"));
        assertThat(respuesta.alertas()).singleElement().satisfies(alerta -> {
            assertThat(alerta.tipo()).isEqualTo("FRECUENCIA");
            assertThat(alerta.direccion()).isEqualTo("DISMINUCION");
            assertThat(alerta.valorActual()).isEqualByComparingTo("1");
            assertThat(alerta.valorHabitual()).isEqualByComparingTo("4");
            assertThat(alerta.detectadoEn()).isEqualTo(OffsetDateTime.parse("2026-10-10T04:00:00Z"));
        });
    }

    @Test
    void unClienteSinLineaBaseLlegaSinAlertasPeroConSuEstado() {
        servidor.expect(requestTo(RUTA))
                .andRespond(withSuccess("""
                        {"estado":"SIN_LINEA_BASE","evaluadoEn":"2026-10-10T04:00:00Z","alertas":[]}
                        """, MediaType.APPLICATION_JSON));

        CambiosComportamientoResponse respuesta = cambios.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isFalse();
        assertThat(respuesta.estado()).isEqualTo("SIN_LINEA_BASE");
        assertThat(respuesta.alertas()).isEmpty();
    }

    @Test
    void sinIdentificadoresDeOrigenNoConsulta() {
        CambiosComportamientoResponse respuesta = cambios.buscar(42L, List.of());

        assertThat(respuesta.sinDatos()).isTrue();
        servidor.verify();
    }

    @Test
    void siComportamientoNoRespondeLaFichaSigueCargandoSinEseBloque() {
        servidor.expect(requestTo(RUTA)).andRespond(withServerError());

        CambiosComportamientoResponse respuesta = cambios.buscar(42L, UN_IDENTIFICADOR);

        assertThat(respuesta.sinDatos()).isTrue();
        assertThat(respuesta.alertas()).isEmpty();
    }
}
