package com.maxiconecta.crm.fidelizacion.salida;

import com.maxiconecta.crm.fidelizacion.movimiento.ClientesPerfil;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ClientesPerfilSalidaTest {
    @Test
    void usaElIdentificadorExternoDelContratoDelPerfil() {
        var builder = RestClient.builder().baseUrl("http://perfil");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://perfil/api/perfil/clientes/42")).andRespond(withSuccess(
                """
                {"id":42,"nombres":"Prueba","identificadoresOrigen":[{"idCliente":"CLI-5521"}]}
                """, MediaType.APPLICATION_JSON));
        assertThat(new ClientesPerfil(builder.build()).identificador(42)).isEqualTo("CLI-5521");
        servidor.verify();
    }

    @Test
    void noSustituyeUnVinculoAmbiguoConElIdInterno() {
        var builder = RestClient.builder().baseUrl("http://perfil");
        var servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo("http://perfil/api/perfil/clientes/42")).andRespond(withSuccess(
                """
                {"id":42,"identificadoresOrigen":[{"idCliente":"A"},{"idCliente":"B"}]}
                """, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new ClientesPerfil(builder.build()).identificador(42))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("identificador unico");
        servidor.verify();
    }
}
