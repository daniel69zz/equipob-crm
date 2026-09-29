package com.maxiconecta.crm.comportamiento.compra;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ClientePerfilesTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://perfil");
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
    private final ClientePerfiles perfiles = new ClientePerfiles(builder.build());
    private static final String CONSULTA = "http://perfil/api/perfil/clientes?idClienteOrigen=CLI-5521";

    @AfterEach
    void verificarSolicitudes() {
        servidor.verify();
    }

    @Test
    void buscaPorIdentificadorYLeeElContratoDePerfil() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess("""
                {"clientes":[{"id":42,"nombres":"Ana","estado":"COMPLETO"}],
                 "pagina":0,"tamanio":50,"total":1}
                """, MediaType.APPLICATION_JSON));

        assertThat(perfiles.buscar("CLI-5521")).contains(42L);
    }

    @Test
    void codificaElIdentificadorSinInterpretarloComoParametros() {
        servidor.expect(requestTo("http://perfil/api/perfil/clientes?idClienteOrigen=A%2BB%26C%2F1"))
                .andRespond(withSuccess("{\"clientes\":[],\"total\":0}", MediaType.APPLICATION_JSON));

        assertThat(perfiles.buscar("A+B&C/1")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"clientes\":[],\"total\":0}",
            "{\"clientes\":[{\"id\":42},{\"id\":43}],\"total\":2}",
            "{\"clientes\":[{\"id\":0}],\"total\":1}",
            "{\"clientes\":[{}],\"total\":1}",
            "{\"clientes\":[null],\"total\":1}",
            "{}", "null", "no es json"
    })
    void sinUnPerfilInequivocoLaVinculacionQuedaPendiente(String respuesta) {
        servidor.expect(requestTo(CONSULTA)).andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        assertThat(perfiles.buscar("CLI-5521")).isEmpty();
    }

    @Test
    void unErrorDelServicioNoImpideGuardarLaCompra() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withServerError());

        assertThat(perfiles.buscar("CLI-5521")).isEmpty();
    }

    @Test
    void unTiempoDeEsperaAgotadoDejaLaVinculacionPendiente() {
        servidor.expect(requestTo(CONSULTA)).andRespond(withException(new SocketTimeoutException("tiempo agotado")));

        assertThat(perfiles.buscar("CLI-5521")).isEmpty();
    }
}
