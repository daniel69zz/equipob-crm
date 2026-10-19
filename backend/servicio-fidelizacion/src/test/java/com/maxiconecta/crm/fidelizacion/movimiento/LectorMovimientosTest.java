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
    void laCabeceraSeLeeAunqueElMensajeSeaInvalido() {
        assertThat(lector.cabecera(ejemplo("movimiento-invalido.json")))
                .isEqualTo(new LectorMovimientos.Cabecera("2b3c4d5e-6f7a-4b8c-9d0e-1f2a3b4c5d6e", "V-100999", "ACUMULACION"));
        assertThat(lector.cabecera("texto")).isEqualTo(new LectorMovimientos.Cabecera(null, null, null));
    }
}
