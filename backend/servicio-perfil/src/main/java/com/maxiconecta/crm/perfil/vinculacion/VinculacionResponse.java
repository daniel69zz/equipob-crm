package com.maxiconecta.crm.perfil.vinculacion;

import com.maxiconecta.crm.perfil.cliente.EstadoVinculacion;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.VinculacionPendiente;

import java.time.OffsetDateTime;

public record VinculacionResponse(Origen origen, String idCliente, Long idClienteSugerido, String motivo,
                                  EstadoVinculacion estado, OffsetDateTime detectadaEn, String resueltaPor,
                                  OffsetDateTime resueltaEn, int eventosPendientes) {

    static VinculacionResponse de(VinculacionPendiente vinculacion, int eventosPendientes) {
        return new VinculacionResponse(vinculacion.getOrigen(), vinculacion.getIdClienteOrigen(),
                vinculacion.getIdClienteSugerido(), vinculacion.getMotivo(), vinculacion.getEstado(),
                vinculacion.getDetectadaEn(), vinculacion.getResueltaPor(), vinculacion.getResueltaEn(),
                eventosPendientes);
    }
}
