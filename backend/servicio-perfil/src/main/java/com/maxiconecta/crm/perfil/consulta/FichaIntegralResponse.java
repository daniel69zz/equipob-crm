package com.maxiconecta.crm.perfil.consulta;

import java.util.List;

/**
 * Ficha integral del cliente (SCRUM-10, SCRUM-154): datos personales, historial de compras,
 * segmento y puntos de fidelización en una sola respuesta. Cada bloque es un campo propio para
 * que los que aún no respondan (CA2) no impidan cargar el resto.
 */
public record FichaIntegralResponse(DatosPersonalesResponse datosPersonales, List<CompraResponse> historialCompras,
                                    SegmentoResponse segmento, PuntosResponse puntos) {
}
