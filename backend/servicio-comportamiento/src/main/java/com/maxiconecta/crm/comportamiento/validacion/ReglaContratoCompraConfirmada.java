package com.maxiconecta.crm.comportamiento.validacion;

import com.maxiconecta.crm.comportamiento.ingesta.EventoCompraConfirmada;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Valida los campos definidos por el contrato RIO-CRM-02 antes de almacenar la compra.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReglaContratoCompraConfirmada implements ReglaValidacionEvento {

    private static final int LARGO_MAXIMO_ID = 64;
    private static final int LARGO_MAXIMO_CATEGORIA = 100;
    private static final int ENTEROS_MAXIMOS_MONTO = 10;
    private static final int DECIMALES_MAXIMOS_MONTO = 2;
    private static final Set<String> ORIGENES_PERMITIDOS = Set.of("MARKETPLACE", "VENTAS");

    @Override
    public void validar(EventoCompraConfirmada evento) {
        exigir(evento, "evento");

        String idEvento = exigirTexto(evento.idEvento(), "idEvento", LARGO_MAXIMO_ID);
        validarUuid(idEvento);

        String tipoEvento = exigirTexto(evento.tipoEvento(), "tipoEvento");
        if (!EventoCompraConfirmada.TIPO.equals(tipoEvento)) {
            rechazar("El campo 'tipoEvento' debe ser " + EventoCompraConfirmada.TIPO);
        }

        String origen = exigirTexto(evento.origen(), "origen");
        if (!ORIGENES_PERMITIDOS.contains(origen)) {
            rechazar("El campo 'origen' debe ser MARKETPLACE o VENTAS");
        }

        exigir(evento.fechaEmision(), "fechaEmision");
        EventoCompraConfirmada.DatosCompra compra = exigir(evento.compra(), "compra");
        exigirTexto(compra.idCompra(), "compra.idCompra", LARGO_MAXIMO_ID);
        exigirTexto(compra.idCliente(), "compra.idCliente", LARGO_MAXIMO_ID);
        exigir(compra.fecha(), "compra.fecha");
        validarDecimal(compra.montoTotal(), "compra.montoTotal");

        List<EventoCompraConfirmada.Item> items = exigir(compra.items(), "compra.items");
        if (items.isEmpty()) {
            rechazar("El campo 'compra.items' debe contener al menos un elemento");
        }

        for (int i = 0; i < items.size(); i++) {
            String ruta = "compra.items[" + i + "]";
            EventoCompraConfirmada.Item item = exigir(items.get(i), ruta);
            exigirTexto(item.categoria(), ruta + ".categoria", LARGO_MAXIMO_CATEGORIA);
            exigir(item.cantidad(), ruta + ".cantidad");
            validarDecimal(item.monto(), ruta + ".monto");
        }
    }

    private static void validarUuid(String valor) {
        try {
            UUID uuid = UUID.fromString(valor);
            if (!uuid.toString().equalsIgnoreCase(valor)) {
                rechazar("El campo 'idEvento' debe tener formato UUID");
            }
        } catch (IllegalArgumentException ex) {
            rechazar("El campo 'idEvento' debe tener formato UUID");
        }
    }

    private static void validarDecimal(BigDecimal valor, String campo) {
        exigir(valor, campo);
        int decimales = Math.max(valor.scale(), 0);
        int enteros = Math.max(valor.precision() - valor.scale(), 0);
        if (decimales > DECIMALES_MAXIMOS_MONTO || enteros > ENTEROS_MAXIMOS_MONTO) {
            rechazar("El campo '" + campo + "' debe respetar el formato decimal (12,2)");
        }
    }

    private static String exigirTexto(String valor, String campo) {
        exigir(valor, campo);
        return valor;
    }

    private static String exigirTexto(String valor, String campo, int largoMaximo) {
        exigir(valor, campo);
        if (valor.codePointCount(0, valor.length()) > largoMaximo) {
            rechazar("El campo '" + campo + "' no debe exceder " + largoMaximo + " caracteres");
        }
        return valor;
    }

    private static <T> T exigir(T valor, String campo) {
        if (valor == null || valor instanceof String texto && texto.isBlank()) {
            rechazar("Falta el campo obligatorio '" + campo + "'");
        }
        return valor;
    }

    private static void rechazar(String mensaje) {
        throw new EventoInvalidoException(mensaje);
    }
}
