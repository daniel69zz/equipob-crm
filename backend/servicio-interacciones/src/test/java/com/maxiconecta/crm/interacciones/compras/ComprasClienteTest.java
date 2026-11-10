package com.maxiconecta.crm.interacciones.compras;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * SCRUM-441 · Consulta de las compras del cliente en servicio-comportamiento. SCRUM-446 · Búsqueda de la compra que
 * se vincula a una interacción.
 */
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
    void lasElegiblesSeFiltranPorReferenciaSinDistinguirMayusculasYRespetanElLimite() {
        servidor.expect(requestTo(PAGINA_0)).andRespond(withSuccess(pagina(3, "V-1001", "X-2", "v-1002"),
                MediaType.APPLICATION_JSON));

        ComprasRecientes elegibles = compras.elegibles(42, List.of("CLI-5521"), "V-10", 1);

        assertThat(elegibles.disponible()).isTrue();
        assertThat(elegibles.compras()).extracting(Compra::referencia).containsExactly("V-1001");
        servidor.verify();
    }

    @Test
    void buscarRecorreLasPaginasHastaEncontrarLaCompra() {
        servidor.expect(requestTo(PAGINA_0)).andRespond(withSuccess(pagina(150, "V-1"), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(PAGINA_0.replace("pagina=0", "pagina=1")))
                .andRespond(withSuccess(pagina(150, "V-2", "V-3"), MediaType.APPLICATION_JSON));

        assertThat(compras.buscar(42, List.of("CLI-5521"), "V-3")).get().extracting(Compra::referencia)
                .isEqualTo("V-3");
        servidor.verify();
    }

    @Test
    void buscarUnaCompraQueNoEsDelClienteDaVacio() {
        servidor.expect(requestTo(PAGINA_0)).andRespond(withSuccess(pagina(1, "V-1"), MediaType.APPLICATION_JSON));

        assertThat(compras.buscar(42, List.of("CLI-5521"), "V-9")).isEmpty();
    }

    @Test
    void siComportamientoFallaBuscarResponde503YLasElegiblesNoEstanDisponibles() {
        servidor.expect(requestTo(PAGINA_0)).andRespond(withServerError());
        servidor.expect(requestTo(PAGINA_0)).andRespond(withServerError());

        assertThatThrownBy(() -> compras.buscar(42, List.of("CLI-5521"), "V-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        assertThat(compras.elegibles(42, List.of("CLI-5521"), null, 20).disponible()).isFalse();
    }

    private static final String PAGINA_0 = "http://comportamiento/api/comportamiento/clientes/42/compras"
            + "?pagina=0&tamanio=100&identificador=CLI-5521";

    private static String pagina(long total, String... referencias) {
        String contenido = String.join(",", Arrays.stream(referencias).map(r -> """
                {"fecha": "2026-11-05T10:15:00Z", "referencia": "%s", "montoTotal": 10, "estado": "CONFIRMADA",
                 "items": []}""".formatted(r)).toList());
        return "{\"content\": [" + contenido + "], \"total\": " + total + "}";
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
