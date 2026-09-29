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
public class LectorEventosTest {

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
        String conCampoNuevo = ejemplo("compra-ventas.json")
                .replaceFirst("\\{", "{\"campoFuturo\": 1,")
                .replace("\"idCompra\":", "\"campoCompra\": true, \"idCompra\":")
                .replace("{ \"categoria\":", "{ \"campoItem\": true, \"categoria\":");

        assertThat(lector.leer(conCampoNuevo).compra().idCompra()).isEqualTo("V-100234");
    }

    @Test
    void leeUnEventoIncompletoParaQueLasReglasLoValiden() {
        assertThat(lector.leer(ejemplo("compra-sin-cliente.json")).compra().idCliente()).isNull();
    }

    @Test
    void rechazaUnMensajeQueNoEsJson() {
        assertThatThrownBy(() -> lector.leer(ejemplo("mensaje-ilegible.txt")))
                .isInstanceOf(EventoIlegibleException.class)
                .hasMessageContaining("no es un JSON válido");
    }

    @Test
    void rechazaUnMensajeVacio() {
        assertThatThrownBy(() -> lector.leer(""))
                .isInstanceOf(EventoIlegibleException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void leeOtroTipoParaQueLasReglasLoValiden() {
        String otroTipo = ejemplo("compra-ventas.json").replace("COMPRA_CONFIRMADA", "ANULACION");

        assertThat(lector.leer(otroTipo).tipoEvento()).isEqualTo("ANULACION");
    }

    @Test
    void rechazaCantidadDecimalPorqueElContratoExigeUnEntero() {
        String cantidadDecimal = ejemplo("compra-ventas.json").replace("\"cantidad\": 1", "\"cantidad\": 1.5");

        assertThatThrownBy(() -> lector.leer(cantidadDecimal)).isInstanceOf(EventoIlegibleException.class);
    }

    @Test
    void rechazaCantidadExpresadaComoTextoPorqueElContratoExigeUnEntero() {
        String cantidadComoTexto = ejemplo("compra-ventas.json").replace("\"cantidad\": 1",
                "\"cantidad\": \"1\"");

        assertThatThrownBy(() -> lector.leer(cantidadComoTexto)).isInstanceOf(EventoIlegibleException.class);
    }

    @Test
    void rechazaMontoExpresadoComoTextoPorqueElContratoExigeUnDecimal() {
        String montoComoTexto = ejemplo("compra-ventas.json").replace("\"montoTotal\": 350.50",
                "\"montoTotal\": \"350.50\"");

        assertThatThrownBy(() -> lector.leer(montoComoTexto)).isInstanceOf(EventoIlegibleException.class);
    }

    @Test
    void rechazaIdentificadorNumericoPorqueElContratoExigeTexto() {
        String identificadorNumerico = ejemplo("compra-ventas.json")
                .replace("\"idCompra\": \"V-100234\"", "\"idCompra\": 100234");

        assertThatThrownBy(() -> lector.leer(identificadorNumerico)).isInstanceOf(EventoIlegibleException.class);
    }

    @Test
    void rechazaFechaSinZona() {
        String fechaSinZona = ejemplo("compra-ventas.json").replace("2026-09-27T15:30:05-04:00",
                "2026-09-27T15:30:05");

        assertThatThrownBy(() -> lector.leer(fechaSinZona)).isInstanceOf(EventoIlegibleException.class);
    }

    @Test
    void unaFechaMalEscritaIndicaElCampoYLaRegla() {
        String fechaMalEscrita = ejemplo("compra-ventas.json").replace("2026-09-27T15:28:10-04:00", "27/09/2026");

        assertThatThrownBy(() -> lector.leer(fechaMalEscrita))
                .isInstanceOf(EventoIlegibleException.class)
                .hasMessage("El campo 'compra.fecha' debe ser una fecha y hora ISO-8601 con zona (llegó \"27/09/2026\")");
    }

    @Test
    void rechazaFechaNumericaPorqueElContratoExigeIso8601ConZona() {
        String fechaNumerica = ejemplo("compra-ventas.json")
                .replace("\"fechaEmision\": \"2026-09-27T15:30:05-04:00\"", "\"fechaEmision\": 1790548205");

        assertThatThrownBy(() -> lector.leer(fechaNumerica)).isInstanceOf(EventoIlegibleException.class);
    }

    @Test
    void laCabeceraSeObtieneAunqueElEventoEsteIncompleto() {
        LectorEventos.Cabecera cabecera = lector.cabecera(ejemplo("compra-sin-cliente.json"));

        assertThat(cabecera.idEvento()).isEqualTo("0e1f2a3b-4c5d-4e6f-8a9b-0c1d2e3f4a5b");
        assertThat(cabecera.origen()).isEqualTo("VENTAS");
        assertThat(lector.cabecera(ejemplo("mensaje-ilegible.txt"))).isEqualTo(LectorEventos.Cabecera.VACIA);
    }

    public static String ejemplo(String archivo) {
        try {
            return Files.readString(EJEMPLOS.resolve(archivo));
        } catch (IOException ex) {
            throw new IllegalStateException("No se encontró el ejemplo " + archivo, ex);
        }
    }
}
