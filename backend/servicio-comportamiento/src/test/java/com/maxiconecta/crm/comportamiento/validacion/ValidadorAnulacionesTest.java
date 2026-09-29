package com.maxiconecta.crm.comportamiento.validacion;

import com.maxiconecta.crm.comportamiento.ingesta.EventoAnulacionCompra;
import com.maxiconecta.crm.comportamiento.ingesta.EventoCompraConfirmada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-529 · Formato y completitud del evento de anulación, con las mismas reglas que las compras.
 */
class ValidadorAnulacionesTest {

    private static final OffsetDateTime FECHA = OffsetDateTime.parse("2026-09-28T11:02:30-04:00");

    private final ValidadorAnulaciones validador = new ValidadorAnulaciones();

    @Test
    void aceptaUnaDevolucionParcialYUnaAnulacionTotal() {
        assertThatCode(() -> validador.validar(parcial(new BigDecimal("50.50"), item("Accesorios", 2, "50.50"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validador.validar(total(new BigDecimal("300.00")))).doesNotThrowAnyException();
    }

    @Test
    void enUnaAnulacionTotalNoExigeItems() {
        assertThatCode(() -> validador.validar(evento("TOTAL", new BigDecimal("300.00"), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void exigeLosCamposObligatorios() {
        assertThatThrownBy(() -> validador.validar(new EventoAnulacionCompra(
                "8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f", "COMPRA_ANULADA", FECHA, null)))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessage("Falta el campo obligatorio 'anulacion'");
        assertThatThrownBy(() -> validador.validar(conDatos(new EventoAnulacionCompra.DatosAnulacion(
                "V-1-A1", "V-1", "CLI-1", "TOTAL", FECHA, new BigDecimal("10.00"), " ", null))))
                .hasMessage("Falta el campo obligatorio 'anulacion.motivo'");
        assertThatThrownBy(() -> validador.validar(conDatos(new EventoAnulacionCompra.DatosAnulacion(
                "V-1-A1", null, "CLI-1", "TOTAL", FECHA, new BigDecimal("10.00"), "motivo", null))))
                .hasMessage("Falta el campo obligatorio 'anulacion.idCompra'");
    }

    @Test
    void aplicaLasReglasComunesDeIdentificadorYTipo() {
        assertThatThrownBy(() -> validador.validar(new EventoAnulacionCompra("no-es-uuid", "COMPRA_ANULADA",
                FECHA, total(BigDecimal.TEN).anulacion())))
                .hasMessage("El campo 'idEvento' debe tener formato UUID");
        assertThatThrownBy(() -> validador.validar(new EventoAnulacionCompra("8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f",
                "COMPRA_CONFIRMADA", FECHA, total(BigDecimal.TEN).anulacion())))
                .hasMessage("El campo 'tipoEvento' debe ser COMPRA_ANULADA");
    }

    @Test
    void rechazaUnTipoDesconocido() {
        assertThatThrownBy(() -> validador.validar(evento("CAMBIO", BigDecimal.TEN, null)))
                .hasMessage("El campo 'anulacion.tipo' debe ser TOTAL o PARCIAL");
    }

    @Test
    void validaElMontoRevertido() {
        assertThatThrownBy(() -> validador.validar(total(BigDecimal.ZERO)))
                .hasMessage("El campo 'anulacion.montoRevertido' debe ser mayor que cero");
        assertThatThrownBy(() -> validador.validar(total(new BigDecimal("10.555"))))
                .hasMessage("El campo 'anulacion.montoRevertido' debe respetar el formato decimal (12,2)");
    }

    @Test
    void unaDevolucionParcialNecesitaItemsQueSumenElMontoRevertido() {
        assertThatThrownBy(() -> validador.validar(evento("PARCIAL", BigDecimal.TEN, List.of())))
                .hasMessage("El campo 'anulacion.items' debe contener al menos un elemento en una devolución parcial");
        assertThatThrownBy(() -> validador.validar(parcial(new BigDecimal("80.00"), item("Accesorios", 1, "25.25"))))
                .hasMessage("La suma de 'anulacion.items[].monto' (25.25) no coincide con 'anulacion.montoRevertido' (80.00)");
        assertThatThrownBy(() -> validador.validar(parcial(new BigDecimal("10.00"), item("Accesorios", 0, "10.00"))))
                .hasMessage("El campo 'anulacion.items[0].cantidad' debe ser mayor que cero");
    }

    @Test
    void elMotivoNoPuedeExcederTrescientosCaracteres() {
        assertThatThrownBy(() -> validador.validar(conDatos(new EventoAnulacionCompra.DatosAnulacion(
                "V-1-A1", "V-1", "CLI-1", "TOTAL", FECHA, BigDecimal.TEN, "x".repeat(301), null))))
                .hasMessage("El campo 'anulacion.motivo' no debe exceder 300 caracteres");
    }

    private static EventoAnulacionCompra total(BigDecimal monto) {
        return evento("TOTAL", monto, null);
    }

    private static EventoAnulacionCompra parcial(BigDecimal monto, EventoCompraConfirmada.Item... items) {
        return evento("PARCIAL", monto, List.of(items));
    }

    private static EventoAnulacionCompra evento(String tipo, BigDecimal monto, List<EventoCompraConfirmada.Item> items) {
        return conDatos(new EventoAnulacionCompra.DatosAnulacion("V-100234-D1", "V-100234", "CLI-5521", tipo, FECHA,
                monto, "Accesorios con falla", items));
    }

    private static EventoAnulacionCompra conDatos(EventoAnulacionCompra.DatosAnulacion datos) {
        return new EventoAnulacionCompra("8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f", "COMPRA_ANULADA", FECHA,
                datos);
    }

    private static EventoCompraConfirmada.Item item(String categoria, int cantidad, String monto) {
        return new EventoCompraConfirmada.Item(categoria, cantidad, new BigDecimal(monto));
    }
}
