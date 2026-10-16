package com.maxiconecta.crm.comportamiento.mensajeria;

import java.util.function.Consumer;

/**
 * Una suscripción de Pub/Sub equivalente a una cola de RabbitMQ: recibe del {@code topico} los
 * mensajes cuya {@code ruta} coincide (exacta, o por prefijo si termina en {@code .*}, como en un
 * exchange topic) y los entrega al {@code manejador}. Su tópico y suscripción de respaldo se llaman
 * como ella, con el sufijo {@code .respaldo}.
 */
public record SuscripcionPubSub(String nombre, String topico, String ruta, Consumer<byte[]> manejador) {

    public String respaldo() {
        return nombre + Mensajeria.SUFIJO_RESPALDO;
    }

    /** Filtro de la suscripción en Pub/Sub. */
    public String filtro() {
        return porPrefijo()
                ? "hasPrefix(attributes." + Mensajeria.ATRIBUTO_RUTA + ", \"" + prefijo() + "\")"
                : "attributes." + Mensajeria.ATRIBUTO_RUTA + " = \"" + ruta + "\"";
    }

    /** Misma regla que el filtro, por si la suscripción se creó sin él. */
    public boolean acepta(String rutaMensaje) {
        return rutaMensaje != null && (porPrefijo() ? rutaMensaje.startsWith(prefijo()) : rutaMensaje.equals(ruta));
    }

    private boolean porPrefijo() {
        return ruta.endsWith(".*");
    }

    private String prefijo() {
        return ruta.substring(0, ruta.length() - 1);
    }
}
