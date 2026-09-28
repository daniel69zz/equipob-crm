package com.maxiconecta.crm.comportamiento.ingesta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.stereotype.Component;

/**
 * Convierte el contenido de un mensaje en un evento de compra confirmada.
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
                    : new Cabecera(texto(raiz, "idEvento", 64), texto(raiz, "tipoEvento", 40), texto(raiz, "origen", 15));
        } catch (JsonProcessingException ex) {
            return Cabecera.VACIA;
        }
    }

    /**
     * Lee el evento completo. Las reglas del contrato se aplican después mediante ValidadorEventos.
     */
    public EventoCompraConfirmada leer(String contenido) {
        EventoCompraConfirmada evento;
        try {
            JsonNode raiz = objectMapper.readTree(contenido);
            if (raiz == null || raiz.isNull() || raiz.isMissingNode()) {
                throw new EventoIlegibleException("El mensaje está vacío");
            }
            validarFechaTextual(raiz, "fechaEmision", "fechaEmision");
            if (raiz != null && raiz.path("compra").isObject()) {
                validarFechaTextual(raiz.path("compra"), "fecha", "compra.fecha");
            }
            evento = objectMapper.treeToValue(raiz, EventoCompraConfirmada.class);
        } catch (JsonProcessingException ex) {
            throw new EventoIlegibleException("El mensaje no es un JSON válido de compra confirmada: "
                    + ex.getOriginalMessage(), ex);
        }
        return evento;
    }

    private static void validarFechaTextual(JsonNode objeto, String campo, String ruta) {
        if (objeto == null || !objeto.isObject()) {
            return;
        }
        JsonNode valor = objeto.get(campo);
        if (valor != null && !valor.isNull() && !valor.isTextual()) {
            throw new EventoIlegibleException("El campo '" + ruta + "' debe ser una fecha y hora ISO-8601 con zona");
        }
    }

    private static String texto(JsonNode raiz, String campo, int largoMaximo) {
        JsonNode valor = raiz.get(campo);
        if (valor == null || !valor.isValueNode()) {
            return null;
        }
        String texto = valor.asText();
        return texto.length() > largoMaximo ? texto.substring(0, largoMaximo) : texto;
    }

    public record Cabecera(String idEvento, String tipoEvento, String origen) {
        static final Cabecera VACIA = new Cabecera(null, null, null);
    }
}
