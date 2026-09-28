package com.maxiconecta.crm.perfil.cliente;

public enum EstadoVinculacion {
    /** Espera que un administrador decida. */
    PENDIENTE,
    /** Se vinculó a un perfil existente. */
    VINCULADO,
    /** Es otra persona: sus eventos crean un perfil nuevo. */
    NUEVO_PERFIL
}
