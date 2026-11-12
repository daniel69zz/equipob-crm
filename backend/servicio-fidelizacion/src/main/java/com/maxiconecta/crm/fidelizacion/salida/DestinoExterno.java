package com.maxiconecta.crm.fidelizacion.salida;

import com.google.cloud.spring.pubsub.core.publisher.PubSubPublisherOperations;

/**
 * Publicador hacia el tópico del curso cuando está en otro proyecto de Google Cloud que los eventos
 * internos (por ejemplo, eventos internos en el emulador y saldo en el proyecto real del docente).
 */
public record DestinoExterno(PubSubPublisherOperations publicador) { }
