package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.cliente.Origen;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Evento RIO-CRM-01 ya leído. Los datos del perfil llegan tal como vinieron: su validación
 * decide si el perfil queda completo o incompleto.
 */
public record EventoClienteRecibido(String idEvento, TipoEventoCliente tipo, Origen origen,
                                    OffsetDateTime fechaEmision, String responsable, DatosCliente cliente) {

    public static final String RESPONSABLE_POR_DEFECTO = "sincronizacion-automatica";

    /** Momento del cambio en el sistema de origen; ordena los eventos de un mismo cliente. */
    public OffsetDateTime fechaCambio() {
        return cliente.fechaActualizacion() != null ? cliente.fechaActualizacion() : fechaEmision;
    }

    public String responsableDelCambio() {
        return responsable != null && !responsable.isBlank() ? responsable : RESPONSABLE_POR_DEFECTO;
    }

    public record DatosCliente(String idCliente, OffsetDateTime fechaActualizacion, String nombres, String apellidos,
                               String tipoDocumento, String numeroDocumento, Contacto contacto,
                               List<DatosDireccion> direcciones) {
    }

    public record Contacto(String email, String telefono) {
        public static final Contacto VACIO = new Contacto(null, null);
    }

    public record DatosDireccion(String idDireccion, String tipo, String calle, String numero, String zona,
                                 String ciudad, String referencia, boolean principal) {
    }
}
