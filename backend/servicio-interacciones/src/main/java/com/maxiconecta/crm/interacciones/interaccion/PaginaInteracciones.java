package com.maxiconecta.crm.interacciones.interaccion;

import java.util.List;

/** Una página del historial de un cliente, de la más reciente a la más antigua. */
public record PaginaInteracciones(List<Interaccion> interacciones, long total, int pagina, int tamanio) {
}
