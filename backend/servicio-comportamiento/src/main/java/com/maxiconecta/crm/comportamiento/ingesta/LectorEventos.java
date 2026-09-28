package com.maxiconecta.crm.comportamiento.ingesta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte el contenido de un mensaje en un evento de compra confirmada.
 */
@Component
public class LectorEventos {

    private final ObjectMapper objectMapper;

    public LectorEventos(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
     * Lee el evento completo y comprueba que traiga los datos necesarios para registrar la compra.
     */
    public EventoCompraConfirmada leer(String contenido) {
        EventoCompraConfirmada evento;
        try {
            evento = objectMapper.readValue(contenido, EventoCompraConfirmada.class);
        } catch (JsonProcessingException ex) {
            throw new EventoIlegibleException("El mensaje no es un JSON válido de compra confirmada: "
                    + ex.getOriginalMessage(), ex);
        }
        if (evento == null) {
            throw new EventoIlegibleException("El mensaje está vacío");
        }
        if (!EventoCompraConfirmada.TIPO.equals(evento.tipoEvento())) {
            throw new EventoIlegibleException("Tipo de evento no esperado: " + evento.tipoEvento());
        }
        exigir(evento.origen(), "origen");
        exigir(evento.compra(), "compra");
        EventoCompraConfirmada.DatosCompra compra = evento.compra();
        exigir(compra.idCompra(), "compra.idCompra");
        exigir(compra.idCliente(), "compra.idCliente");
        exigir(compra.fecha(), "compra.fecha");
        exigir(compra.montoTotal(), "compra.montoTotal");
        if (compra.items() == null || compra.items().isEmpty()) {
            throw new EventoIlegibleException("Falta el campo obligatorio 'compra.items'");
        }
        List<EventoCompraConfirmada.Item> items = compra.items();
        for (int i = 0; i < items.size(); i++) {
            EventoCompraConfirmada.Item item = items.get(i);
            exigir(item, "compra.items[" + i + "]");
            exigir(item.categoria(), "compra.items[" + i + "].categoria");
            exigir(item.cantidad(), "compra.items[" + i + "].cantidad");
            exigir(item.monto(), "compra.items[" + i + "].monto");
        }
        return evento;
    }

    private static void exigir(Object valor, String campo) {
        if (valor == null || valor instanceof String texto && texto.isBlank()) {
            throw new EventoIlegibleException("Falta el campo obligatorio '" + campo + "'");
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
