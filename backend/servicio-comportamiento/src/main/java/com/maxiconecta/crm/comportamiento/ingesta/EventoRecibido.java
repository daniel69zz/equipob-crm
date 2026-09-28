package com.maxiconecta.crm.comportamiento.ingesta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Mensaje recibido de Marketplace y Ventas, con su contenido original y su estado de procesamiento.
 */
@Entity
@Table(schema = "comportamiento", name = "evento_recibido")
public class EventoRecibido {

    private static final int LARGO_MAXIMO_CAUSA = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String idEventoOrigen;

    private String tipoEvento;

    private String origen;

    private String idTransaccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEvento estado = EstadoEvento.RECIBIDO;

    private String causa;

    @Column(nullable = false)
    private String contenido;

    @Column(nullable = false)
    private OffsetDateTime recibidoEn = OffsetDateTime.now();

    private OffsetDateTime procesadoEn;

    protected EventoRecibido() {
    }

    public EventoRecibido(String contenido, String idEventoOrigen, String tipoEvento, String origen,
                          String idTransaccion) {
        this.contenido = contenido;
        this.idEventoOrigen = idEventoOrigen;
        this.tipoEvento = tipoEvento;
        this.origen = origen;
        this.idTransaccion = idTransaccion;
    }

    public void marcarProcesado() {
        cerrar(EstadoEvento.PROCESADO, null);
    }

    public void marcarFallido(String causa) {
        cerrar(EstadoEvento.FALLIDO, causa);
    }

    public void marcarDescartado(String causa) {
        cerrar(EstadoEvento.DESCARTADO, causa);
    }

    private void cerrar(EstadoEvento nuevoEstado, String nuevaCausa) {
        this.estado = nuevoEstado;
        this.causa = nuevaCausa != null && nuevaCausa.length() > LARGO_MAXIMO_CAUSA
                ? nuevaCausa.substring(0, LARGO_MAXIMO_CAUSA)
                : nuevaCausa;
        this.procesadoEn = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdEventoOrigen() {
        return idEventoOrigen;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getOrigen() {
        return origen;
    }

    public String getIdTransaccion() {
        return idTransaccion;
    }

    public EstadoEvento getEstado() {
        return estado;
    }

    public String getCausa() {
        return causa;
    }

    public String getContenido() {
        return contenido;
    }

    public OffsetDateTime getRecibidoEn() {
        return recibidoEn;
    }

    public OffsetDateTime getProcesadoEn() {
        return procesadoEn;
    }
}
