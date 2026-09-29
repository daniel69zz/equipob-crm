package com.maxiconecta.crm.comportamiento.validacion;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Reglas comunes a los contratos de eventos de venta (RIO-CRM-02 y RIO-CRM-05), con los mismos
 * mensajes para todos. Cada método lanza {@link EventoInvalidoException} con el campo y la regla
 * incumplida.
 */
final class ReglasContrato {

    static final int LARGO_MAXIMO_ID = 64;
    static final int LARGO_MAXIMO_CATEGORIA = 100;
    private static final int ENTEROS_MAXIMOS_MONTO = 10;
    private static final int DECIMALES_MAXIMOS_MONTO = 2;

    private ReglasContrato() {
    }

    static <T> T exigir(T valor, String campo) {
        if (valor == null || valor instanceof String texto && texto.isBlank()) {
            rechazar("Falta el campo obligatorio '" + campo + "'");
        }
        return valor;
    }

    static String exigirTexto(String valor, String campo, int largoMaximo) {
        exigir(valor, campo);
        if (valor.codePointCount(0, valor.length()) > largoMaximo) {
            rechazar("El campo '" + campo + "' no debe exceder " + largoMaximo + " caracteres");
        }
        return valor;
    }

    static void validarUuid(String valor) {
        try {
            UUID uuid = UUID.fromString(valor);
            if (!uuid.toString().equalsIgnoreCase(valor)) {
                rechazar("El campo 'idEvento' debe tener formato UUID");
            }
        } catch (IllegalArgumentException ex) {
            rechazar("El campo 'idEvento' debe tener formato UUID");
        }
    }

    /** Monto obligatorio, con formato decimal (12,2) y mayor que cero. */
    static void validarMontoPositivo(BigDecimal valor, String campo) {
        exigir(valor, campo);
        int decimales = Math.max(valor.scale(), 0);
        int enteros = Math.max(valor.precision() - valor.scale(), 0);
        if (decimales > DECIMALES_MAXIMOS_MONTO || enteros > ENTEROS_MAXIMOS_MONTO) {
            rechazar("El campo '" + campo + "' debe respetar el formato decimal (12,2)");
        }
        if (valor.signum() <= 0) {
            rechazar("El campo '" + campo + "' debe ser mayor que cero");
        }
    }

    static void exigirCantidadPositiva(Integer cantidad, String campo) {
        exigir(cantidad, campo);
        if (cantidad <= 0) {
            rechazar("El campo '" + campo + "' debe ser mayor que cero");
        }
    }

    /** La suma de los montos de los ítems debe ser igual al total que declara el evento. */
    static <T> void exigirSumaIgual(List<T> items, Function<T, BigDecimal> monto, BigDecimal total,
                                    String campoItems, String campoTotal) {
        BigDecimal suma = items.stream().map(monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(total) != 0) {
            rechazar("La suma de '" + campoItems + "' (" + suma.toPlainString() + ") no coincide con '"
                    + campoTotal + "' (" + total.toPlainString() + ")");
        }
    }

    static void rechazar(String mensaje) {
        throw new EventoInvalidoException(mensaje);
    }
}
