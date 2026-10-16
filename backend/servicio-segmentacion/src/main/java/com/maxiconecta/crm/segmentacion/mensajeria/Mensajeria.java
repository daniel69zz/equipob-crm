package com.maxiconecta.crm.segmentacion.mensajeria;

/**
 * Broker de mensajería elegido por configuración: RabbitMQ (predeterminado) o Google Cloud Pub/Sub
 * (perfil {@code pubsub}). Ver docs/mensajeria/pubsub.md.
 * <p>
 * En Pub/Sub se usan los mismos nombres que en RabbitMQ: cada exchange es un tópico, cada cola es una
 * suscripción y la routing key viaja en el atributo {@link #ATRIBUTO_RUTA}, que filtra la suscripción.
 */
public final class Mensajeria {

    public static final String PROPIEDAD_BROKER = "crm.mensajeria.broker";
    public static final String RABBIT = "rabbit";
    public static final String PUBSUB = "pubsub";
    /** Atributo del mensaje con la routing key de RabbitMQ (por ejemplo, {@code compra.confirmada}). */
    public static final String ATRIBUTO_RUTA = "ruta";
    /** Atributo que se agrega al pasar un mensaje rechazado al tópico de respaldo. */
    public static final String ATRIBUTO_CAUSA = "causa";
    public static final String SUFIJO_RESPALDO = ".respaldo";

    private Mensajeria() {
    }
}
