package com.maxiconecta.crm.comportamiento.preferencia;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record PerfilPreferencias(long clienteId, boolean construido, boolean sinPreferencias,
                                 OffsetDateTime construidoEn, long version, String criterio,
                                 List<CategoriaPreferida> categorias) {
    public static final String CRITERIO = "MONTO_DESC_COMPRAS_DESC_NOMBRE_ASC_V1";

    public PerfilPreferencias { categorias = List.copyOf(categorias); }

    public static PerfilPreferencias pendiente(long clienteId) {
        return new PerfilPreferencias(clienteId, false, true, null, 0, CRITERIO, List.of());
    }

    public record CategoriaPreferida(int posicion, String categoria, long compras, long unidades, BigDecimal monto) { }
}
