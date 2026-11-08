package com.maxiconecta.crm.interacciones.historial;

import java.util.List;

/**
 * Historial del cliente para atenderlo (SCRUM-53, docs/interacciones/historial-cliente.md): los eventos más
 * recientes, del más reciente al más antiguo. Un cliente sin interacciones no es un error: lo indican
 * {@code sinInteracciones} y un aviso (criterio de aceptación 2).
 */
public record HistorialCliente(long clienteId, List<EventoHistorial> eventos, long totalInteracciones,
                               boolean sinInteracciones, boolean hayMas, List<String> avisos) {

    static final String SIN_INTERACCIONES = "El cliente no tiene interacciones registradas";
}
