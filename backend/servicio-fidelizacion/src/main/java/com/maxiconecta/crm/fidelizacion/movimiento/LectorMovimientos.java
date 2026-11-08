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
 * <p>
 * También lee la anulación o devolución de compra RIO-CRM-05 (SCRUM-524) como un movimiento
 * {@code REVERSA}: la transacción es {@code idAnulacion}, la transacción original es {@code idCompra} y el
 * valor es {@code montoRevertido}. Así las dos fuentes de una misma reversa comparten su clave y no se
 * aplican dos veces, y la bitácora conserva el mensaje original para reprocesarlo.
 */
@Component
public class LectorMovimientos {

    static final int LARGO_MAXIMO_ID = 64;
    static final int DIGITOS_MAXIMOS_PUNTOS = 12;

    private final ObjectMapper json;

    public LectorMovimientos(ObjectMapper json) {
        this.json = json;
    }

    /** Lo que se puede saber del mensaje aunque sea inválido, para la bitácora; campos nulos si no se leen. */
    public Cabecera cabecera(String contenido) {
        try {
            JsonNode raiz = json.readTree(contenido);
            if (esAnulacion(raiz)) {
                return new Cabecera(texto(raiz.get("idEvento")), texto(raiz.path("anulacion").get("idAnulacion")),
                        TipoMovimiento.REVERSA.name());
            }
            JsonNode movimiento = raiz.path("movimiento");
            return new Cabecera(texto(raiz.get("idEvento")), texto(movimiento.get("idTransaccion")),
                    texto(movimiento.get("tipoMovimiento")));
        } catch (IOException | RuntimeException ex) {
            return new Cabecera(null, null, null);
        }
    }

    public EventoMovimientoPuntos leer(String contenido) {
        if (esAnulacion(contenido)) {
            return leerAnulacion(contenido);
        }
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
        TipoMovimiento tipo = tipo(movimiento.tipoMovimiento());
        if (tipo == TipoMovimiento.REDENCION) {
            // SCRUM-405: la solicitud de redención trae los puntos redimidos; el valor en bolivianos es opcional.
            puntosRedimidos(movimiento.puntos());
            if (movimiento.valorConsumido() != null) {
                valorConsumido(movimiento.valorConsumido());
            }
        } else {
            valorConsumido(movimiento.valorConsumido());
        }
        if (tipo == TipoMovimiento.REVERSA) {
            // SCRUM-540: la reversa apunta a la compra que revierte; su valor es el monto revertido.
            identificador("movimiento.idTransaccionOriginal", movimiento.idTransaccionOriginal());
        }
        if (movimiento.fecha() == null) {
            throw new EventoInvalidoException("movimiento.fecha es obligatoria");
        }
    }

    private boolean esAnulacion(String contenido) {
        try {
            return esAnulacion(json.readTree(contenido));
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    private static boolean esAnulacion(JsonNode raiz) {
        return raiz != null && EventoAnulacionCompra.TIPO.equals(raiz.path("tipoEvento").asText(null));
    }

    /** RIO-CRM-05 como movimiento REVERSA (SCRUM-524), con las reglas de su contrato que usa Fidelización. */
    private EventoMovimientoPuntos leerAnulacion(String contenido) {
        EventoAnulacionCompra evento;
        try {
            evento = json.readValue(contenido, EventoAnulacionCompra.class);
        } catch (IOException ex) {
            throw new EventoInvalidoException("El mensaje no es un JSON válido de RIO-CRM-05: " + ex.getMessage(), ex);
        }
        identificador("idEvento", evento.idEvento());
        try {
            UUID.fromString(evento.idEvento());
        } catch (IllegalArgumentException ex) {
            throw new EventoInvalidoException("idEvento debe tener formato UUID");
        }
        if (evento.fechaEmision() == null) {
            throw new EventoInvalidoException("fechaEmision es obligatoria");
        }
        EventoAnulacionCompra.Anulacion anulacion = evento.anulacion();
        if (anulacion == null) {
            throw new EventoInvalidoException("anulacion es obligatoria");
        }
        identificador("anulacion.idAnulacion", anulacion.idAnulacion());
        identificador("anulacion.idCompra", anulacion.idCompra());
        identificador("anulacion.idCliente", anulacion.idCliente());
        if (!"TOTAL".equals(anulacion.tipo()) && !"PARCIAL".equals(anulacion.tipo())) {
            throw new EventoInvalidoException("anulacion.tipo debe ser TOTAL o PARCIAL");
        }
        decimal("anulacion.montoRevertido", anulacion.montoRevertido());
        if (anulacion.fecha() == null) {
            throw new EventoInvalidoException("anulacion.fecha es obligatoria");
        }
        return new EventoMovimientoPuntos(evento.idEvento(), evento.tipoEvento(), evento.fechaEmision(),
                new EventoMovimientoPuntos.Movimiento(anulacion.idAnulacion(), anulacion.idCliente(),
                        TipoMovimiento.REVERSA.name(), anulacion.montoRevertido(), null, anulacion.idCompra(),
                        anulacion.fecha()));
    }

    private static void valorConsumido(BigDecimal valor) {
        decimal("movimiento.valorConsumido", valor);
    }

    private static void decimal(String campo, BigDecimal valor) {
        if (valor == null || valor.signum() <= 0) {
            throw new EventoInvalidoException(campo + " es obligatorio y mayor que cero");
        }
        if (valor.stripTrailingZeros().scale() > 2 || valor.precision() - valor.scale() > 12) {
            throw new EventoInvalidoException(campo + " debe tener formato decimal (12,2)");
        }
    }

    /** Puntos redimidos: entero mayor que cero, de hasta {@value #DIGITOS_MAXIMOS_PUNTOS} dígitos. */
    private static void puntosRedimidos(BigDecimal puntos) {
        if (puntos == null || puntos.signum() <= 0) {
            throw new EventoInvalidoException("movimiento.puntos es obligatorio en una REDENCION y mayor que cero");
        }
        BigDecimal entero = puntos.stripTrailingZeros();
        if (entero.scale() > 0) {
            throw new EventoInvalidoException("movimiento.puntos debe ser un número entero de puntos");
        }
        if (entero.precision() - entero.scale() > DIGITOS_MAXIMOS_PUNTOS) {
            throw new EventoInvalidoException("movimiento.puntos admite hasta " + DIGITOS_MAXIMOS_PUNTOS + " dígitos");
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
