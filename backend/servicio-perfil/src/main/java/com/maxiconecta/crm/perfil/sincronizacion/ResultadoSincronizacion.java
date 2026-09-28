package com.maxiconecta.crm.perfil.sincronizacion;

/**
 * Resultado de aplicar un evento al perfil: el perfil afectado y cómo queda el evento en la bitácora.
 */
public record ResultadoSincronizacion(Long idCliente, EstadoEventoCliente estado, String causa) {
}
