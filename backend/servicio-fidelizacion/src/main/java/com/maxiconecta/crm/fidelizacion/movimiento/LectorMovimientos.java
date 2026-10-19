package com.maxiconecta.crm.fidelizacion.movimiento;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lee y valida un evento RIO-CRM-03 según su contrato. Cualquier incumplimiento se informa con el
 * campo y la regla, para que la causa quede clara en la bitácora (criterio de aceptación 3).
 */
@Component
public class LectorMovimientos {

    static final int LARGO_MAXIMO_ID = 64;

    private final ObjectMapper json;

    public LectorMovimientos(ObjectMapper json) {
        this.json = json;
    }

    /** Lo que se puede saber del mensaje aunque sea inválido, para la bitácora; campos nulos si no se leen. */
    public Cabecera cabecera(String contenido) {
        try {
            JsonNode raiz = json.readTree(contenido);
            JsonNode movimiento = raiz.path("movimiento");
            return new Cabecera(texto(raiz.get("idEvento")), texto(movimiento.get("idTransaccion")),
                    texto(movimiento.get("tipoMovimiento")));
        } catch (IOException | RuntimeException ex) {
            return new Cabecera(null, null, null);
        }
    }

    public EventoMovimientoPuntos leer(String contenido) {
        EventoMovimientoPuntos evento;
        try {
            evento = json.readValue(contenido, EventoMovimientoPuntos.class);
        } catch (JsonProcessingException ex) {
            throw new EventoInvalidoException("El mensaje no es un JSON válido de RIO-CRM-03: " + ex.getOriginalMessage(), ex);
        } catch (IOException ex) {
            throw new EventoInvalidoException("No se pudo leer el mensaje", ex);
        }
        validar(evento);
        return evento;
    }

    private static void validar(EventoMovimientoPuntos evento) {
        if (evento == null) {
            throw new EventoInvalidoException("El mensaje está vacío");
        }
        identificador("idEvento", evento.idEvento());
        try {
            UUID.fromString(evento.idEvento());
        } catch (IllegalArgumentException ex) {
            throw new EventoInvalidoException("idEvento debe tener formato UUID");
        }
        if (!EventoMovimientoPuntos.TIPO.equals(evento.tipoEvento())) {
            throw new EventoInvalidoException("tipoEvento debe ser " + EventoMovimientoPuntos.TIPO);
        }
        if (evento.fechaEmision() == null) {
            throw new EventoInvalidoException("fechaEmision es obligatoria");
        }
        EventoMovimientoPuntos.Movimiento movimiento = evento.movimiento();
        if (movimiento == null) {
            throw new EventoInvalidoException("movimiento es obligatorio");
        }
        identificador("movimiento.idTransaccion", movimiento.idTransaccion());
        identificador("movimiento.idCliente", movimiento.idCliente());
        tipo(movimiento.tipoMovimiento());
        BigDecimal valor = movimiento.valorConsumido();
        if (valor == null || valor.signum() <= 0) {
            throw new EventoInvalidoException("movimiento.valorConsumido es obligatorio y mayor que cero");
        }
        if (valor.stripTrailingZeros().scale() > 2 || valor.precision() - valor.scale() > 12) {
            throw new EventoInvalidoException("movimiento.valorConsumido debe tener formato decimal (12,2)");
        }
        if (movimiento.fecha() == null) {
            throw new EventoInvalidoException("movimiento.fecha es obligatoria");
        }
    }

    static TipoMovimiento tipo(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new EventoInvalidoException("movimiento.tipoMovimiento es obligatorio");
        }
        try {
            return TipoMovimiento.valueOf(texto);
        } catch (IllegalArgumentException ex) {
            throw new EventoInvalidoException("movimiento.tipoMovimiento debe ser ACUMULACION, REDENCION o REVERSA");
        }
    }

    private static void identificador(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new EventoInvalidoException(campo + " es obligatorio");
        }
        if (valor.length() > LARGO_MAXIMO_ID) {
            throw new EventoInvalidoException(campo + " admite hasta " + LARGO_MAXIMO_ID + " caracteres");
        }
    }

    private static String texto(JsonNode nodo) {
        if (nodo == null || !nodo.isTextual()) {
            return null;
        }
        String texto = nodo.asText();
        return texto.length() > LARGO_MAXIMO_ID ? texto.substring(0, LARGO_MAXIMO_ID) : texto;
    }

    public record Cabecera(String idEvento, String idTransaccion, String tipoMovimiento) {
    }
}
