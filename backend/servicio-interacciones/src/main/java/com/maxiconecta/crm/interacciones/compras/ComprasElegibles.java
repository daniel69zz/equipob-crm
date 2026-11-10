package com.maxiconecta.crm.interacciones.compras;

import java.util.List;

/**
 * Compras del cliente que el agente puede vincular a una interacción (SCRUM-446), de la más reciente a la más
 * antigua. {@code disponible} es falso si servicio-comportamiento no respondió.
 */
public record ComprasElegibles(List<Compra> compras, boolean disponible, List<String> avisos) {

    static final String SIN_COMPRAS = "El cliente no tiene compras registradas";
    static final String NO_DISPONIBLES = "No se pudieron consultar las compras del cliente: intente de nuevo en unos minutos";
}
