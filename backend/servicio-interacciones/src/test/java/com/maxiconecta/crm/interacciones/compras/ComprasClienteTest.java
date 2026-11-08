package com.maxiconecta.crm.interacciones.compras;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** SCRUM-441 · Consulta de las compras del cliente en servicio-comportamiento. */
class ComprasClienteTest {

    private MockRestServiceServer servidor;
    private ComprasCliente compras;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://comportamiento");
        servidor = MockRestServiceServer.bindTo(builder).build();
        compras = new ComprasCliente(builder.build());
    }

    @Test
    void pideLasComprasConLosIdentificadoresDeVentasYLasLee() {
        servidor.expect(requestTo("http://comportamiento/api/comportamiento/clientes/42/compras"
                        + "?pagina=0&tamanio=50&identificador=CLI-5521&identificador=CLI-9"))
                .andRespond(withSuccess("""
                        {"content": [{"fecha": "2026-11-05T10:15:00Z", "referencia": "V-100234",
                          "montoTotal": 350.50, "estado": "CONFIRMADA",
                          "items": [{"categoria": "Electrónica", "cantidad": 1, "monto": 350.50}]}],
                         "pagina": 0, "tamanio": 50, "total": 12}
                        """, MediaType.APPLICATION_JSON));

        ComprasRecientes recientes = compras.recientes(42, List.of("CLI-5521", "CLI-9"), 50);

        assertThat(recientes.disponible()).isTrue();
        assertThat(recientes.total()).isEqualTo(12);
        assertThat(recientes.compras()).singleElement().satisfies(compra -> {
            assertThat(compra.referencia()).isEqualTo("V-100234");
            assertThat(compra.montoTotal()).isEqualByComparingTo(new BigDecimal("350.50"));
            assertThat(compra.items()).singleElement().extracting(Compra.Item::categoria).isEqualTo("Electrónica");
        });
        servidor.verify();
    }

    @Test
    void siComportamientoFallaLasComprasNoEstanDisponibles() {
        servidor.expect(requestTo("http://comportamiento/api/comportamiento/clientes/42/compras"
                + "?pagina=0&tamanio=10&identificador=CLI-5521")).andRespond(withServerError());

        ComprasRecientes recientes = compras.recientes(42, List.of("CLI-5521"), 10);

        assertThat(recientes.disponible()).isFalse();
        assertThat(recientes.compras()).isEmpty();
    }
}
