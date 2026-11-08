package com.maxiconecta.crm.interacciones.compras;

import java.util.List;

/**
 * Las compras más recientes del cliente y cuántas tiene en total. {@code disponible} es falso si
 * servicio-comportamiento no respondió: el historial se muestra igual, sin compras (SCRUM-441).
 */
public record ComprasRecientes(List<Compra> compras, long total, boolean disponible) {

    public static ComprasRecientes ninguna() {
        return new ComprasRecientes(List.of(), 0, true);
    }

    public static ComprasRecientes noDisponibles() {
        return new ComprasRecientes(List.of(), 0, false);
    }
}
