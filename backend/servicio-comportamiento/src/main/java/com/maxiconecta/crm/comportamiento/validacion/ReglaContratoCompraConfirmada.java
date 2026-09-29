package com.maxiconecta.crm.comportamiento.validacion;

import com.maxiconecta.crm.comportamiento.ingesta.EventoCompraConfirmada;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

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
 * Valida los campos definidos por el contrato RIO-CRM-02 antes de almacenar la compra.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReglaContratoCompraConfirmada implements ReglaValidacionEvento {

    static final Set<String> ORIGENES_PERMITIDOS = Set.of("MARKETPLACE", "VENTAS");

    @Override
    public void validar(EventoCompraConfirmada evento) {
        exigir(evento, "evento");

        String idEvento = exigirTexto(evento.idEvento(), "idEvento", LARGO_MAXIMO_ID);
        validarUuid(idEvento);

        String tipoEvento = exigir(evento.tipoEvento(), "tipoEvento");
        if (!EventoCompraConfirmada.TIPO.equals(tipoEvento)) {
            rechazar("El campo 'tipoEvento' debe ser " + EventoCompraConfirmada.TIPO);
        }

        String origen = exigir(evento.origen(), "origen");
        if (!ORIGENES_PERMITIDOS.contains(origen)) {
            rechazar("El campo 'origen' debe ser MARKETPLACE o VENTAS");
        }

        exigir(evento.fechaEmision(), "fechaEmision");
        EventoCompraConfirmada.DatosCompra compra = exigir(evento.compra(), "compra");
        exigirTexto(compra.idCompra(), "compra.idCompra", LARGO_MAXIMO_ID);
        exigirTexto(compra.idCliente(), "compra.idCliente", LARGO_MAXIMO_ID);
        exigir(compra.fecha(), "compra.fecha");
        validarMontoPositivo(compra.montoTotal(), "compra.montoTotal");

        List<EventoCompraConfirmada.Item> items = exigir(compra.items(), "compra.items");
        if (items.isEmpty()) {
            rechazar("El campo 'compra.items' debe contener al menos un elemento");
        }

        for (int i = 0; i < items.size(); i++) {
            String ruta = "compra.items[" + i + "]";
            EventoCompraConfirmada.Item item = exigir(items.get(i), ruta);
            exigirTexto(item.categoria(), ruta + ".categoria", LARGO_MAXIMO_CATEGORIA);
            exigirCantidadPositiva(item.cantidad(), ruta + ".cantidad");
            validarMontoPositivo(item.monto(), ruta + ".monto");
        }

        exigirSumaIgual(items, EventoCompraConfirmada.Item::monto, compra.montoTotal(), "compra.items[].monto",
                "compra.montoTotal");
    }
}
