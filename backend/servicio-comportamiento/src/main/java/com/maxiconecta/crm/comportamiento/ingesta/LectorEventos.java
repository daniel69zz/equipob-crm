package com.maxiconecta.crm.comportamiento.ingesta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Convierte el contenido de un mensaje en un evento de compra confirmada (RIO-CRM-02) o de
 * anulación de compra (RIO-CRM-05).
 */
@Component
public class LectorEventos {

    private final ObjectMapper objectMapper;

    public LectorEventos(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();
        this.objectMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        this.objectMapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        this.objectMapper.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
    }

    /**
     * Datos de identificación del mensaje, para la bitácora. Si el contenido no es JSON, quedan vacíos.
     */
    public Cabecera cabecera(String contenido) {
        try {
            JsonNode raiz = objectMapper.readTree(contenido);
            return raiz == null || !raiz.isObject()
                    ? Cabecera.VACIA
                    : new Cabecera(texto(raiz, "idEvento", 64), texto(raiz, "tipoEvento", 40), texto(raiz, "origen", 15),
                    idTransaccion(raiz));
        } catch (JsonProcessingException ex) {
            return Cabecera.VACIA;
        }
    }

    /**
     * Lee el evento completo. Las reglas del contrato se aplican después mediante ValidadorEventos.
     */
    public EventoCompraConfirmada leer(String contenido) {
        return leer(contenido, EventoCompraConfirmada.class, "compra", "compra confirmada");
    }

    /**
     * Lee un evento de anulación o devolución. Las reglas del contrato se aplican después mediante
     * ValidadorAnulaciones.
     */
    public EventoAnulacionCompra leerAnulacion(String contenido) {
        return leer(contenido, EventoAnulacionCompra.class, "anulacion", "anulación de compra");
    }

    /**
     * @param detalle objeto del evento que trae la fecha de la transacción ({@code compra} o {@code anulacion})
     */
    private <T> T leer(String contenido, Class<T> tipo, String detalle, String descripcion) {
        try {
            JsonNode raiz = objectMapper.readTree(contenido);
            if (raiz == null || raiz.isNull() || raiz.isMissingNode()) {
                throw new EventoIlegibleException("El mensaje está vacío");
            }
            validarFechaTextual(raiz, "fechaEmision", "fechaEmision");
            if (raiz.path(detalle).isObject()) {
                validarFechaTextual(raiz.path(detalle), "fecha", detalle + ".fecha");
            }
            return objectMapper.treeToValue(raiz, tipo);
        } catch (JsonProcessingException ex) {
            throw new EventoIlegibleException("El mensaje no es un JSON válido de " + descripcion + ": "
                    + ex.getOriginalMessage(), ex);
        }
    }

    /** La compra en RIO-CRM-02; la anulación o devolución en RIO-CRM-05. */
    private static String idTransaccion(JsonNode raiz) {
        String idCompra = texto(raiz.path("compra"), "idCompra", 64);
        return idCompra != null ? idCompra : texto(raiz.path("anulacion"), "idAnulacion", 64);
    }

    private static void validarFechaTextual(JsonNode objeto, String campo, String ruta) {
        if (objeto == null || !objeto.isObject()) {
            return;
        }
        JsonNode valor = objeto.get(campo);
        if (valor == null || valor.isNull()) {
            return;
        }
        if (!valor.isTextual()) {
            throw new EventoIlegibleException("El campo '" + ruta + "' debe ser una fecha y hora ISO-8601 con zona");
        }
        try {
            OffsetDateTime.parse(valor.asText());
        } catch (DateTimeParseException ex) {
            throw new EventoIlegibleException("El campo '" + ruta + "' debe ser una fecha y hora ISO-8601 con zona"
                    + " (llegó \"" + valor.asText() + "\")");
        }
    }

    private static String texto(JsonNode raiz, String campo, int largoMaximo) {
        JsonNode valor = raiz.isObject() ? raiz.get(campo) : null;
        if (valor == null || !valor.isValueNode()) {
            return null;
        }
        String texto = valor.asText();
        return texto.length() > largoMaximo ? texto.substring(0, largoMaximo) : texto;
    }

    public record Cabecera(String idEvento, String tipoEvento, String origen, String idTransaccion) {
        static final Cabecera VACIA = new Cabecera(null, null, null, null);
    }
}
