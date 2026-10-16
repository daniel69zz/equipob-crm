package com.maxiconecta.crm.comportamiento.preferencia;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Perfil de preferencias persistido (SCRUM-41). {@code construidoEn} es la fecha de la última
 * actualización; {@code origen} e {@code idEventoOrigen} dicen qué la provocó: la construcción manual
 * o el evento de venta que la disparó (SCRUM-42).
 */
public record PerfilPreferencias(long clienteId, boolean construido, boolean sinPreferencias,
                                 OffsetDateTime construidoEn, long version, String criterio,
                                 List<CategoriaPreferida> categorias, String origen, String idEventoOrigen) {
    public static final String CRITERIO = "MONTO_DESC_COMPRAS_DESC_NOMBRE_ASC_V1";
    public static final String CONSTRUCCION_MANUAL = "CONSTRUCCION_MANUAL";

    public PerfilPreferencias { categorias = List.copyOf(categorias); }

    public static PerfilPreferencias pendiente(long clienteId) {
        return new PerfilPreferencias(clienteId, false, true, null, 0, CRITERIO, List.of(), null, null);
    }

    public record CategoriaPreferida(int posicion, String categoria, long compras, long unidades, BigDecimal monto) { }
}
