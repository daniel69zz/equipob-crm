package com.maxiconecta.crm.perfil.sincronizacion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxiconecta.crm.perfil.cliente.Origen;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte el contenido de un mensaje en un evento de datos del cliente.
 * <p>
 * Solo exige lo necesario para saber a qué cliente y a qué sistema corresponde (tipo, origen,
 * idCliente y fechas). Los datos del perfil se leen tal como vienen; si faltan o están mal
 * formados, lo decide la validación del perfil y el perfil queda incompleto.
 */
@Component
public class LectorEventosCliente {

    static final int LARGO_MAXIMO_ID = 64;

    private final ObjectMapper objectMapper;

    public LectorEventosCliente(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Datos de identificación del mensaje, para la bitácora. Si el contenido no es JSON, quedan vacíos.
     */
    public Cabecera cabecera(String contenido) {
        try {
            JsonNode raiz = objectMapper.readTree(contenido);
            if (raiz == null || !raiz.isObject()) {
                return Cabecera.VACIA;
            }
            return new Cabecera(recortar(escalar(raiz, "idEvento"), 64), recortar(escalar(raiz, "tipoEvento"), 40),
                    recortar(escalar(raiz, "origen"), 15), recortar(escalar(raiz.path("cliente"), "idCliente"), 64));
        } catch (JsonProcessingException | EventoIlegibleException ex) {
            return Cabecera.VACIA;
        }
    }

    public EventoClienteRecibido leer(String contenido) {
        JsonNode raiz;
        try {
            raiz = objectMapper.readTree(contenido);
        } catch (JsonProcessingException ex) {
            throw new EventoIlegibleException("El mensaje no es un JSON válido de datos del cliente: "
                    + ex.getOriginalMessage(), ex);
        }
        if (raiz == null || !raiz.isObject()) {
            throw new EventoIlegibleException("El mensaje no es un objeto JSON de datos del cliente");
        }
        TipoEventoCliente tipo = valorDe(TipoEventoCliente.class, escalar(raiz, "tipoEvento"), "tipoEvento",
                "CLIENTE_REGISTRADO o CLIENTE_ACTUALIZADO");
        Origen origen = valorDe(Origen.class, escalar(raiz, "origen"), "origen", "MARKETPLACE o VENTAS");
        if (!origen.esSistemaExterno()) {
            throw new EventoIlegibleException("El campo 'origen' debe ser MARKETPLACE o VENTAS (llegó " + origen + ")");
        }
        OffsetDateTime fechaEmision = fecha(raiz, "fechaEmision", "fechaEmision");
        if (fechaEmision == null) {
            throw new EventoIlegibleException("Falta el campo obligatorio 'fechaEmision'");
        }

        JsonNode cliente = raiz.get("cliente");
        if (cliente == null || !cliente.isObject()) {
            throw new EventoIlegibleException("Falta el campo obligatorio 'cliente'");
        }
        String idCliente = escalar(cliente, "idCliente");
        if (idCliente == null || idCliente.isBlank()) {
            throw new EventoIlegibleException("Falta el campo obligatorio 'cliente.idCliente'");
        }
        if (idCliente.length() > LARGO_MAXIMO_ID) {
            throw new EventoIlegibleException("El campo 'cliente.idCliente' no debe exceder " + LARGO_MAXIMO_ID
                    + " caracteres");
        }

        EventoClienteRecibido.DatosCliente datos = new EventoClienteRecibido.DatosCliente(
                idCliente.trim(), fecha(cliente, "fechaActualizacion", "cliente.fechaActualizacion"),
                escalar(cliente, "nombres"), escalar(cliente, "apellidos"),
                escalar(cliente, "tipoDocumento"), escalar(cliente, "numeroDocumento"),
                contacto(cliente), direcciones(cliente));
        return new EventoClienteRecibido(escalar(raiz, "idEvento"), tipo, origen, fechaEmision,
                recortar(escalar(raiz, "responsable"), 100), datos);
    }

    private EventoClienteRecibido.Contacto contacto(JsonNode cliente) {
        JsonNode contacto = cliente.get("contacto");
        if (contacto == null || contacto.isNull()) {
            return EventoClienteRecibido.Contacto.VACIO;
        }
        if (!contacto.isObject()) {
            throw new EventoIlegibleException("El campo 'cliente.contacto' debe ser un objeto");
        }
        return new EventoClienteRecibido.Contacto(escalar(contacto, "email"), escalar(contacto, "telefono"));
    }

    private List<EventoClienteRecibido.DatosDireccion> direcciones(JsonNode cliente) {
        JsonNode lista = cliente.get("direcciones");
        if (lista == null || lista.isNull()) {
            return List.of();
        }
        if (!lista.isArray()) {
            throw new EventoIlegibleException("El campo 'cliente.direcciones' debe ser una lista");
        }
        List<EventoClienteRecibido.DatosDireccion> direcciones = new ArrayList<>();
        for (int i = 0; i < lista.size(); i++) {
            JsonNode direccion = lista.get(i);
            if (!direccion.isObject()) {
                throw new EventoIlegibleException("El campo 'cliente.direcciones[" + i + "]' debe ser un objeto");
            }
            direcciones.add(new EventoClienteRecibido.DatosDireccion(escalar(direccion, "idDireccion"),
                    escalar(direccion, "tipo"), escalar(direccion, "calle"), escalar(direccion, "numero"),
                    escalar(direccion, "zona"), escalar(direccion, "ciudad"), escalar(direccion, "referencia"),
                    direccion.path("principal").asBoolean(false)));
        }
        return direcciones;
    }

    /**
     * Valor de un campo simple como texto (los números se aceptan: un documento puede llegar como número).
     * Un objeto o una lista donde se espera un valor simple no respeta el contrato.
     */
    private static String escalar(JsonNode objeto, String campo) {
        JsonNode valor = objeto.isObject() ? objeto.get(campo) : null;
        if (valor == null || valor.isNull()) {
            return null;
        }
        if (!valor.isValueNode()) {
            throw new EventoIlegibleException("El campo '" + campo + "' debe ser un valor simple");
        }
        return valor.asText();
    }

    private static OffsetDateTime fecha(JsonNode objeto, String campo, String ruta) {
        String texto = escalar(objeto, campo);
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(texto);
        } catch (DateTimeParseException ex) {
            throw new EventoIlegibleException("El campo '" + ruta + "' debe ser una fecha y hora ISO-8601 con zona");
        }
    }

    private static <E extends Enum<E>> E valorDe(Class<E> tipo, String valor, String campo, String esperado) {
        if (valor == null || valor.isBlank()) {
            throw new EventoIlegibleException("Falta el campo obligatorio '" + campo + "'");
        }
        try {
            return Enum.valueOf(tipo, valor);
        } catch (IllegalArgumentException ex) {
            throw new EventoIlegibleException("El campo '" + campo + "' debe ser " + esperado + " (llegó " + valor + ")");
        }
    }

    private static String recortar(String texto, int largoMaximo) {
        return texto != null && texto.length() > largoMaximo ? texto.substring(0, largoMaximo) : texto;
    }

    public record Cabecera(String idEvento, String tipoEvento, String origen, String idClienteOrigen) {
        static final Cabecera VACIA = new Cabecera(null, null, null, null);
    }
}
