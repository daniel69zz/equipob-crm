package com.maxiconecta.crm.comportamiento.validacion;

import com.maxiconecta.crm.comportamiento.ingesta.EventoCompraConfirmada;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SCRUM-131 · Validación del contrato RIO-CRM-02 antes de almacenar una compra.
 */
class ReglaContratoCompraConfirmadaTest {

    private final ReglaContratoCompraConfirmada regla = new ReglaContratoCompraConfirmada();

    private String idEvento = "5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c";
    private String tipoEvento = EventoCompraConfirmada.TIPO;
    private String origen = "VENTAS";
    private OffsetDateTime fechaEmision = OffsetDateTime.parse("2026-09-27T15:30:05-04:00");
    private String idCompra = "V-100234";
    private String idCliente = "CLI-5521";
    private OffsetDateTime fechaCompra = OffsetDateTime.parse("2026-09-27T15:28:10-04:00");
    private BigDecimal montoTotal = new BigDecimal("350.50");
    private List<EventoCompraConfirmada.Item> items = List.of(
            new EventoCompraConfirmada.Item("Electrónica", 1, new BigDecimal("300.00")),
            new EventoCompraConfirmada.Item("Accesorios", 2, new BigDecimal("50.50")));
    private boolean compraAusente;

    @Test
    void aceptaUnEventoCompletoDelContrato() {
        assertThatCode(() -> regla.validar(evento())).doesNotThrowAnyException();
    }

    @Test
    void rechazaIdEventoAusente() {
        idEvento = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("idEvento");
    }

    @Test
    void rechazaIdEventoSinFormatoUuid() {
        idEvento = "evento-123";
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("idEvento").hasMessageContaining("UUID");
    }

    @Test
    void rechazaIdEventoQueExcedeLaLongitudMaxima() {
        idEvento = "a".repeat(65);
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("idEvento").hasMessageContaining("64");
    }

    @Test
    void rechazaTipoEventoAusente() {
        tipoEvento = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("tipoEvento");
    }

    @Test
    void rechazaUnTipoDeEventoDistinto() {
        tipoEvento = "COMPRA_ANULADA";
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessageContaining("tipoEvento").hasMessageContaining(EventoCompraConfirmada.TIPO);
    }

    @Test
    void rechazaOrigenAusente() {
        origen = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("origen");
    }

    @Test
    void rechazaOrigenNoPermitido() {
        origen = "TIENDA";
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessageContaining("origen").hasMessageContaining("MARKETPLACE").hasMessageContaining("VENTAS");
    }

    @Test
    void rechazaFechaEmisionAusente() {
        fechaEmision = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("fechaEmision");
    }

    @Test
    void rechazaCompraAusente() {
        compraAusente = true;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra");
    }

    @Test
    void rechazaIdCompraAusente() {
        idCompra = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.idCompra");
    }

    @Test
    void rechazaIdCompraQueExcedeLaLongitudMaxima() {
        idCompra = "C".repeat(65);
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.idCompra").hasMessageContaining("64");
    }

    @Test
    void rechazaIdClienteAusente() {
        idCliente = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.idCliente");
    }

    @Test
    void rechazaIdClienteQueExcedeLaLongitudMaxima() {
        idCliente = "C".repeat(65);
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.idCliente").hasMessageContaining("64");
    }

    @Test
    void rechazaFechaCompraAusente() {
        fechaCompra = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.fecha");
    }

    @Test
    void rechazaMontoTotalAusente() {
        montoTotal = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.montoTotal");
    }

    @Test
    void rechazaMontoTotalConMasDeDiezEnteros() {
        montoTotal = new BigDecimal("10000000000.00");
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessageContaining("compra.montoTotal").hasMessageContaining("12,2");
    }

    @Test
    void rechazaMontoTotalConMasDeDosDecimales() {
        montoTotal = new BigDecimal("350.501");
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessageContaining("compra.montoTotal").hasMessageContaining("12,2");
    }

    @Test
    void rechazaMontoTotalNegativoOCero() {
        montoTotal = new BigDecimal("-10.00");
        items = List.of(new EventoCompraConfirmada.Item("Electrónica", 1, new BigDecimal("-10.00")));
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessage("El campo 'compra.montoTotal' debe ser mayor que cero");
        montoTotal = BigDecimal.ZERO;
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessage("El campo 'compra.montoTotal' debe ser mayor que cero");
    }

    @Test
    void rechazaCantidadCeroONegativa() {
        items = List.of(new EventoCompraConfirmada.Item("Electrónica", 0, new BigDecimal("350.50")));
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessage("El campo 'compra.items[0].cantidad' debe ser mayor que cero");
    }

    @Test
    void rechazaMontoDeItemNegativo() {
        items = List.of(new EventoCompraConfirmada.Item("Electrónica", 1, new BigDecimal("400.50")),
                new EventoCompraConfirmada.Item("Descuento", 1, new BigDecimal("-50.00")));
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessage("El campo 'compra.items[1].monto' debe ser mayor que cero");
    }

    @Test
    void rechazaQueLaSumaDeLosItemsNoCoincidaConElTotal() {
        montoTotal = new BigDecimal("999.99");
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessage("La suma de 'compra.items[].monto' (350.50) no coincide con 'compra.montoTotal' (999.99)");
    }

    @Test
    void rechazaItemsAusentes() {
        items = null;
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.items");
    }

    @Test
    void rechazaListaDeItemsVacia() {
        items = List.of();
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessageContaining("compra.items").hasMessageContaining("al menos un elemento");
    }

    @Test
    void rechazaCategoriaAusente() {
        items = List.of(new EventoCompraConfirmada.Item(null, 1, new BigDecimal("350.50")));
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.items[0].categoria");
    }

    @Test
    void rechazaCategoriaQueExcedeLaLongitudMaxima() {
        items = List.of(new EventoCompraConfirmada.Item("C".repeat(101), 1, new BigDecimal("350.50")));
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessageContaining("compra.items[0].categoria").hasMessageContaining("100");
    }

    @Test
    void rechazaCantidadAusente() {
        items = List.of(new EventoCompraConfirmada.Item("Electrónica", null, new BigDecimal("350.50")));
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.items[0].cantidad");
    }

    @Test
    void rechazaMontoDeItemAusente() {
        items = List.of(new EventoCompraConfirmada.Item("Electrónica", 1, null));
        assertThatThrownBy(() -> regla.validar(evento())).hasMessageContaining("compra.items[0].monto");
    }

    @Test
    void rechazaMontoDeItemFueraDelFormatoDecimal() {
        items = List.of(new EventoCompraConfirmada.Item("Electrónica", 1, new BigDecimal("350.501")));
        assertThatThrownBy(() -> regla.validar(evento()))
                .hasMessageContaining("compra.items[0].monto").hasMessageContaining("12,2");
    }

    private EventoCompraConfirmada evento() {
        EventoCompraConfirmada.DatosCompra compra = compraAusente ? null
                : new EventoCompraConfirmada.DatosCompra(idCompra, idCliente, fechaCompra, montoTotal, items);
        return new EventoCompraConfirmada(idEvento, tipoEvento, origen, fechaEmision, compra);
    }
}
