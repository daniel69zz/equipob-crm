package com.maxiconecta.crm.interacciones.historial;

import java.util.List;

/**
 * Historial del cliente para atenderlo (SCRUM-53, docs/interacciones/historial-cliente.md): sus interacciones y sus
 * compras más recientes, en una sola línea de tiempo del evento más reciente al más antiguo (criterio de
 * aceptación 1). Un cliente sin interacciones no es un error: lo indican {@code sinInteracciones} y un aviso
 * (criterio de aceptación 2).
 */
public record HistorialCliente(long clienteId, List<EventoHistorial> eventos, long totalInteracciones,
                               long totalCompras, boolean sinInteracciones, boolean comprasDisponibles,
                               boolean hayMas, List<String> avisos) {

    static final String SIN_INTERACCIONES = "El cliente no tiene interacciones registradas";
    static final String COMPRAS_NO_DISPONIBLES =
            "No se pudieron consultar las compras del cliente: se muestran solo sus interacciones";
}
