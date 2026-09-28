package com.maxiconecta.crm.comportamiento.ingesta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-558 · Lectura de los eventos de ejemplo del simulador según el contrato RIO-CRM-02.
 */
class LectorEventosTest {

    static final Path EJEMPLOS = Path.of("../../herramientas/simulador-eventos/eventos");

    private final LectorEventos lector = new LectorEventos(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void leeElEjemploDeVentasConTodosSusDatos() {
        EventoCompraConfirmada evento = lector.leer(ejemplo("compra-ventas.json"));

        assertThat(evento.origen()).isEqualTo("VENTAS");
        assertThat(evento.compra().idCompra()).isEqualTo("V-100234");
        assertThat(evento.compra().idCliente()).isEqualTo("CLI-5521");
        assertThat(evento.compra().montoTotal()).isEqualByComparingTo(new BigDecimal("350.50"));
        assertThat(evento.compra().items()).extracting(EventoCompraConfirmada.Item::categoria)
                .containsExactly("Electrónica", "Accesorios");
    }

    @Test
    void leeElEjemploDeMarketplace() {
        EventoCompraConfirmada evento = lector.leer(ejemplo("compra-marketplace.json"));

        assertThat(evento.origen()).isEqualTo("MARKETPLACE");
        assertThat(evento.compra().items()).hasSize(2);
    }

    @Test
    void ignoraLosCamposQueNoConoce() {
        String conCampoNuevo = ejemplo("compra-ventas.json").replaceFirst("\\{", "{\"campoFuturo\": 1,");

        assertThat(lector.leer(conCampoNuevo).compra().idCompra()).isEqualTo("V-100234");
    }

    @Test
    void rechazaUnEventoSinCliente() {
        assertThatThrownBy(() -> lector.leer(ejemplo("compra-sin-cliente.json")))
                .isInstanceOf(EventoIlegibleException.class)
                .hasMessageContaining("compra.idCliente");
    }

    @Test
    void rechazaUnMensajeQueNoEsJson() {
        assertThatThrownBy(() -> lector.leer(ejemplo("mensaje-ilegible.txt")))
                .isInstanceOf(EventoIlegibleException.class)
                .hasMessageContaining("no es un JSON válido");
    }

    @Test
    void rechazaUnTipoDeEventoDistinto() {
        String otroTipo = ejemplo("compra-ventas.json").replace("COMPRA_CONFIRMADA", "ANULACION");

        assertThatThrownBy(() -> lector.leer(otroTipo)).hasMessageContaining("Tipo de evento no esperado");
    }

    @Test
    void laCabeceraSeObtieneAunqueElEventoEsteIncompleto() {
        LectorEventos.Cabecera cabecera = lector.cabecera(ejemplo("compra-sin-cliente.json"));

        assertThat(cabecera.idEvento()).isEqualTo("0e1f2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b");
        assertThat(cabecera.origen()).isEqualTo("VENTAS");
        assertThat(lector.cabecera(ejemplo("mensaje-ilegible.txt"))).isEqualTo(LectorEventos.Cabecera.VACIA);
    }

    static String ejemplo(String archivo) {
        try {
            return Files.readString(EJEMPLOS.resolve(archivo));
        } catch (IOException ex) {
            throw new IllegalStateException("No se encontró el ejemplo " + archivo, ex);
        }
    }
}
