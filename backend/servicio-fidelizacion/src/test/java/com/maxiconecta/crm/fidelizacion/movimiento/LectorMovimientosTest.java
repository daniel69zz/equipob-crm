package com.maxiconecta.crm.fidelizacion.movimiento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SCRUM-592 · Lectura y validación del contrato RIO-CRM-03 con los eventos de ejemplo del simulador. */
public class LectorMovimientosTest {

    static final Path EJEMPLOS = Path.of("../../herramientas/simulador-eventos/eventos");

    private final LectorMovimientos lector = new LectorMovimientos(new ObjectMapper().registerModule(new JavaTimeModule()));

    public static String ejemplo(String archivo) {
        try {
            return Files.readString(EJEMPLOS.resolve(archivo), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void leeUnaAcumulacionDelSimulador() {
        EventoMovimientoPuntos evento = lector.leer(ejemplo("movimiento-acumulacion-ana.json"));

        assertThat(evento.movimiento().idTransaccion()).isEqualTo("V-100234");
        assertThat(evento.movimiento().idCliente()).isEqualTo("CLI-5521");
        assertThat(evento.movimiento().tipoMovimiento()).isEqualTo("ACUMULACION");
        assertThat(evento.movimiento().valorConsumido()).isEqualByComparingTo(new BigDecimal("350.50"));
    }

    @Test
    void rechazaUnValorNoPositivoConElCampoYLaRegla() {
        assertThatThrownBy(() -> lector.leer(ejemplo("movimiento-invalido.json")))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("movimiento.valorConsumido");
    }

    @Test
    void rechazaCamposFaltantesTiposDesconocidosYJsonIlegible() {
        String valido = ejemplo("movimiento-acumulacion-ana.json");
        assertThatThrownBy(() -> lector.leer(valido.replace("\"idCliente\": \"CLI-5521\",", "")))
                .hasMessageContaining("movimiento.idCliente");
        assertThatThrownBy(() -> lector.leer(valido.replace("ACUMULACION", "REGALO")))
                .hasMessageContaining("tipoMovimiento");
        assertThatThrownBy(() -> lector.leer(valido.replace("7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f", "no-uuid")))
                .hasMessageContaining("UUID");
        assertThatThrownBy(() -> lector.leer(valido.replace("350.50", "350.505")))
                .hasMessageContaining("(12,2)");
        assertThatThrownBy(() -> lector.leer("{ no es json")).isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void leeUnaRedencionConSusPuntosRedimidos() {
        EventoMovimientoPuntos evento = lector.leer(ejemplo("movimiento-redencion.json"));

        assertThat(evento.movimiento().idTransaccion()).isEqualTo("R-5001");
        assertThat(evento.movimiento().tipoMovimiento()).isEqualTo("REDENCION");
        assertThat(evento.movimiento().puntos()).isEqualByComparingTo("20");
        assertThat(evento.movimiento().valorConsumido()).isEqualByComparingTo("20.00");
    }

    @Test
    void unaRedencionNoNecesitaValorConsumido() {
        EventoMovimientoPuntos evento = lector.leer(ejemplo("movimiento-redencion-saldo-insuficiente.json"));

        assertThat(evento.movimiento().puntos()).isEqualByComparingTo("500");
        assertThat(evento.movimiento().valorConsumido()).isNull();
    }

    @Test
    void rechazaUnaRedencionSinPuntosConDecimalesONoPositivos() {
        String valida = ejemplo("movimiento-redencion.json");
        assertThatThrownBy(() -> lector.leer(valida.replace("\"puntos\": 20,", "")))
                .isInstanceOf(EventoInvalidoException.class).hasMessageContaining("movimiento.puntos es obligatorio");
        assertThatThrownBy(() -> lector.leer(valida.replace("\"puntos\": 20", "\"puntos\": 0")))
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> lector.leer(valida.replace("\"puntos\": 20", "\"puntos\": -5")))
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> lector.leer(valida.replace("\"puntos\": 20", "\"puntos\": 20.5")))
                .hasMessageContaining("número entero");
        assertThatThrownBy(() -> lector.leer(valida.replace("\"puntos\": 20", "\"puntos\": 1234567890123")))
                .hasMessageContaining("12 dígitos");
        assertThatThrownBy(() -> lector.leer(valida.replace("20.00", "-1")))
                .hasMessageContaining("movimiento.valorConsumido");
    }

    @Test
    void unaRedencionConPuntosEnteroEscritosConDecimalesCerosEsValida() {
        String valida = ejemplo("movimiento-redencion.json").replace("\"puntos\": 20", "\"puntos\": 20.0");

        assertThat(lector.leer(valida).movimiento().puntos().longValueExact()).isEqualTo(20);
    }

    @Test
    void laAcumulacionSigueExigiendoElValorConsumido() {
        String sinValor = ejemplo("movimiento-acumulacion-ana.json").replace("\"valorConsumido\": 350.50,", "");

        assertThatThrownBy(() -> lector.leer(sinValor)).hasMessageContaining("movimiento.valorConsumido");
    }

    @Test
    void laCabeceraSeLeeAunqueElMensajeSeaInvalido() {
        assertThat(lector.cabecera(ejemplo("movimiento-invalido.json")))
                .isEqualTo(new LectorMovimientos.Cabecera("2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e", "V-100999", "ACUMULACION"));
        assertThat(lector.cabecera("texto")).isEqualTo(new LectorMovimientos.Cabecera(null, null, null));
    }

    // --- SCRUM-524: reversa por RIO-CRM-03 y por RIO-CRM-05 ---

    @Test
    void leeUnaReversaDelSimuladorConLaCompraQueRevierte() {
        EventoMovimientoPuntos.Movimiento reversa = lector.leer(ejemplo("movimiento-reversa-parcial.json")).movimiento();

        assertThat(reversa.tipoMovimiento()).isEqualTo("REVERSA");
        assertThat(reversa.idTransaccion()).isEqualTo("V-100234-D1");
        assertThat(reversa.idTransaccionOriginal()).isEqualTo("V-100234");
        assertThat(reversa.valorConsumido()).isEqualByComparingTo(new BigDecimal("50.50"));
    }

    @Test
    void unaReversaExigeLaTransaccionOriginalYElMontoRevertido() {
        String valida = ejemplo("movimiento-reversa-parcial.json");

        assertThatThrownBy(() -> lector.leer(valida.replace("\"idTransaccionOriginal\": \"V-100234\",", "")))
                .hasMessage("movimiento.idTransaccionOriginal es obligatorio");
        assertThatThrownBy(() -> lector.leer(valida.replace("\"valorConsumido\": 50.50,", "")))
                .hasMessageContaining("movimiento.valorConsumido");
    }

    @Test
    void leeUnaAnulacionRioCrm05ComoLaMismaReversa() {
        EventoMovimientoPuntos.Movimiento porAnulacion = lector.leer(ejemplo("anulacion-parcial.json")).movimiento();
        EventoMovimientoPuntos.Movimiento porMovimiento = lector.leer(ejemplo("movimiento-reversa-parcial.json")).movimiento();

        assertThat(porAnulacion.tipoMovimiento()).isEqualTo("REVERSA");
        assertThat(porAnulacion.idTransaccion()).isEqualTo(porMovimiento.idTransaccion());
        assertThat(porAnulacion.idTransaccionOriginal()).isEqualTo(porMovimiento.idTransaccionOriginal());
        assertThat(porAnulacion.idCliente()).isEqualTo(porMovimiento.idCliente());
        assertThat(porAnulacion.valorConsumido()).isEqualByComparingTo(porMovimiento.valorConsumido());
    }

    @Test
    void unaAnulacionRioCrm05InvalidaIndicaElCampoDeSuContrato() {
        String valida = ejemplo("anulacion-total.json");

        assertThatThrownBy(() -> lector.leer(valida.replace("\"TOTAL\"", "\"OTRA\"")))
                .hasMessage("anulacion.tipo debe ser TOTAL o PARCIAL");
        assertThatThrownBy(() -> lector.leer(valida.replace("300.00", "0")))
                .hasMessage("anulacion.montoRevertido es obligatorio y mayor que cero");
        assertThatThrownBy(() -> lector.leer(valida.replace("\"idCompra\": \"V-100234\",", "")))
                .hasMessage("anulacion.idCompra es obligatorio");
    }

    @Test
    void laCabeceraDeUnaAnulacionEsLaDeSuReversa() {
        assertThat(lector.cabecera(ejemplo("anulacion-total.json")))
                .isEqualTo(new LectorMovimientos.Cabecera("3a9c7e1f-5d2b-4f8a-b6c4-7e8f9a0b1c2d", "V-100234-A1", "REVERSA"));
    }
}
