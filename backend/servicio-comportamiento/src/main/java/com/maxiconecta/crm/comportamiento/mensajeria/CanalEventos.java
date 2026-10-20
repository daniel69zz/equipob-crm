package com.maxiconecta.crm.comportamiento.mensajeria;

/**
 * Publica un evento del CRM en el broker configurado. {@code destino} es el exchange de RabbitMQ o el
 * tópico de Pub/Sub (tienen el mismo nombre) y {@code ruta}, la routing key o el atributo
 * {@link Mensajeria#ATRIBUTO_RUTA}.
 */
public interface CanalEventos {

    void publicar(String destino, String ruta, byte[] cuerpo, String idMensaje);
}
