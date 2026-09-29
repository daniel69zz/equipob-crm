package com.maxiconecta.crm.comportamiento.validacion;

import com.maxiconecta.crm.comportamiento.compra.TipoAnulacion;
import com.maxiconecta.crm.comportamiento.ingesta.EventoAnulacionCompra;
import com.maxiconecta.crm.comportamiento.ingesta.EventoCompraConfirmada;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.LARGO_MAXIMO_CATEGORIA;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.LARGO_MAXIMO_ID;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.exigir;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.exigirCantidadPositiva;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.exigirSumaIgual;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.exigirTexto;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.rechazar;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.validarMontoPositivo;
import static com.maxiconecta.crm.comportamiento.validacion.ReglasContrato.validarUuid;

/**
 * Valida el formato y la completitud de un evento RIO-CRM-05 con las mismas reglas que las compras
 * (docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md). Las reglas que dependen de la compra
 * registrada se aplican al procesarlo.
 */
@Component
public class ValidadorAnulaciones {

    static final int LARGO_MAXIMO_MOTIVO = 300;

    public void validar(EventoAnulacionCompra evento) {
        exigir(evento, "evento");

        String idEvento = exigirTexto(evento.idEvento(), "idEvento", LARGO_MAXIMO_ID);
        validarUuid(idEvento);

        String tipoEvento = exigir(evento.tipoEvento(), "tipoEvento");
        if (!EventoAnulacionCompra.TIPO.equals(tipoEvento)) {
            rechazar("El campo 'tipoEvento' debe ser " + EventoAnulacionCompra.TIPO);
        }

        exigir(evento.fechaEmision(), "fechaEmision");
        EventoAnulacionCompra.DatosAnulacion anulacion = exigir(evento.anulacion(), "anulacion");
        exigirTexto(anulacion.idAnulacion(), "anulacion.idAnulacion", LARGO_MAXIMO_ID);
        exigirTexto(anulacion.idCompra(), "anulacion.idCompra", LARGO_MAXIMO_ID);
        exigirTexto(anulacion.idCliente(), "anulacion.idCliente", LARGO_MAXIMO_ID);
        TipoAnulacion tipo = tipo(exigir(anulacion.tipo(), "anulacion.tipo"));
        exigir(anulacion.fecha(), "anulacion.fecha");
        validarMontoPositivo(anulacion.montoRevertido(), "anulacion.montoRevertido");
        exigirTexto(anulacion.motivo(), "anulacion.motivo", LARGO_MAXIMO_MOTIVO);

        if (tipo == TipoAnulacion.PARCIAL) {
            validarItems(anulacion);
        }
    }

    private static void validarItems(EventoAnulacionCompra.DatosAnulacion anulacion) {
        List<EventoCompraConfirmada.Item> items = exigir(anulacion.items(), "anulacion.items");
        if (items.isEmpty()) {
            rechazar("El campo 'anulacion.items' debe contener al menos un elemento en una devolución parcial");
        }
        for (int i = 0; i < items.size(); i++) {
            String ruta = "anulacion.items[" + i + "]";
            EventoCompraConfirmada.Item item = exigir(items.get(i), ruta);
            exigirTexto(item.categoria(), ruta + ".categoria", LARGO_MAXIMO_CATEGORIA);
            exigirCantidadPositiva(item.cantidad(), ruta + ".cantidad");
            validarMontoPositivo(item.monto(), ruta + ".monto");
        }
        exigirSumaIgual(items, EventoCompraConfirmada.Item::monto, anulacion.montoRevertido(),
                "anulacion.items[].monto", "anulacion.montoRevertido");
    }

    private static TipoAnulacion tipo(String valor) {
        try {
            return TipoAnulacion.valueOf(valor);
        } catch (IllegalArgumentException ex) {
            rechazar("El campo 'anulacion.tipo' debe ser TOTAL o PARCIAL");
            return null;
        }
    }
}
