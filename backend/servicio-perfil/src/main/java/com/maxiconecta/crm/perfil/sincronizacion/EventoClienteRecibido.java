package com.maxiconecta.crm.perfil.sincronizacion;

import com.maxiconecta.crm.perfil.cliente.Origen;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

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

    /**
     * @param camposInformados campos que trae el mensaje (ver {@link Campos}). En un alta es la foto
     *                         completa: todos. En una actualización, solo los presentes en el JSON.
     */
    public record DatosCliente(String idCliente, OffsetDateTime fechaActualizacion, String nombres, String apellidos,
                               String tipoDocumento, String numeroDocumento, Contacto contacto,
                               List<DatosDireccion> direcciones, Set<String> camposInformados) {

        public boolean informa(String campo) {
            return camposInformados.contains(campo);
        }
    }

    /** Nombres de los campos del perfil que puede traer una notificación. */
    public static final class Campos {
        public static final String NOMBRES = "nombres";
        public static final String APELLIDOS = "apellidos";
        public static final String TIPO_DOCUMENTO = "tipoDocumento";
        public static final String NUMERO_DOCUMENTO = "numeroDocumento";
        public static final String EMAIL = "email";
        public static final String TELEFONO = "telefono";
        public static final String DIRECCIONES = "direcciones";

        public static final Set<String> TODOS = Set.of(NOMBRES, APELLIDOS, TIPO_DOCUMENTO, NUMERO_DOCUMENTO, EMAIL,
                TELEFONO, DIRECCIONES);

        private Campos() {
        }
    }

    public record Contacto(String email, String telefono) {
        public static final Contacto VACIO = new Contacto(null, null);
    }

    public record DatosDireccion(String idDireccion, String tipo, String calle, String numero, String zona,
                                 String ciudad, String referencia, boolean principal) {
    }
}
