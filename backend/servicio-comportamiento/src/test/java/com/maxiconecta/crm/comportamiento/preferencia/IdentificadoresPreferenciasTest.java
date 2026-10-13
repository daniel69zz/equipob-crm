package com.maxiconecta.crm.comportamiento.preferencia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class IdentificadoresPreferenciasTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://perfil");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final IdentificadoresPreferencias identificadores = new IdentificadoresPreferencias(builder.build());
    private static final String URL = "http://perfil/api/perfil/clientes/42";

    @AfterEach
    void verificar() { servidor.verify(); }

    @Test
    void obtieneTodosLosIdentificadoresDelPerfilSinDuplicarlos() {
        respuesta("""
                {"id":42,"nombres":"Ana","identificadoresOrigen":[
                    {"idCliente":" CLI-A "},{"idCliente":"CLI-A"},{"idCliente":"CLI-B"}]}
                """);
        assertThat(identificadores.obtener(42)).extracting(i -> i.idClienteOrigen()).containsExactly("CLI-A", "CLI-B");
    }

    @Test
    void clienteExistenteSinIdentificadoresEsValido() {
        respuesta("{\"id\":42,\"identificadoresOrigen\":[]}");
        assertThat(identificadores.obtener(42)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "no es json", "{\"id\":42}",
            "{\"id\":99,\"identificadoresOrigen\":[]}",
            "{\"id\":42,\"identificadoresOrigen\":[null]}",
            "{\"id\":42,\"identificadoresOrigen\":[{\"idCliente\":\" \"}]}"})
    void respuestaIncompletaODeOtroClienteNoSeConvierteEnPerfilVacio(String json) {
        respuesta(json);
        fallaCon(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void clienteInexistenteDevuelve404() {
        servidor.expect(requestTo(URL)).andRespond(withResourceNotFound());
        fallaCon(HttpStatus.NOT_FOUND);
    }

    @Test
    void perfilConsolidadoExigeUsarElClienteDestino() {
        respuesta("{\"id\":42,\"idClienteConsolidado\":99,\"identificadoresOrigen\":[]}");
        fallaCon(HttpStatus.CONFLICT);
    }

    @Test
    void servicioCaidoDevuelve503() {
        servidor.expect(requestTo(URL)).andRespond(withServerError());
        fallaCon(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void timeoutDevuelve503() {
        servidor.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException()));
        fallaCon(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void idInvalidoSeRechazaAntesDeConsultarLaRed() {
        assertThatThrownBy(() -> identificadores.obtener(0)).isInstanceOfSatisfying(PreferenciasException.class,
                ex -> assertThat(ex.estado()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private void respuesta(String json) {
        servidor.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private void fallaCon(HttpStatus estado) {
        assertThatThrownBy(() -> identificadores.obtener(42)).isInstanceOfSatisfying(PreferenciasException.class,
                ex -> assertThat(ex.estado()).isEqualTo(estado));
    }
}
