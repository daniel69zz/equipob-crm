package com.maxiconecta.crm.perfil.sincronizacion;

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
 * Mensaje de datos del cliente recibido de Marketplace y Ventas, con su contenido original y
 * el resultado de su sincronización.
 */
@Entity
@Table(schema = "perfil", name = "evento_cliente")
public class EventoCliente {

    private static final int LARGO_MAXIMO_CAUSA = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String idEventoOrigen;

    private String tipoEvento;

    private String origen;

    private String idClienteOrigen;

    private Long idCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEventoCliente estado = EstadoEventoCliente.RECIBIDO;

    private String causa;

    @Column(nullable = false)
    private String contenido;

    @Column(nullable = false)
    private OffsetDateTime recibidoEn = OffsetDateTime.now();

    private OffsetDateTime procesadoEn;

    @Column(nullable = false)
    private int intentos = 1;

    protected EventoCliente() {
    }

    public EventoCliente(String contenido, String idEventoOrigen, String tipoEvento, String origen,
                         String idClienteOrigen) {
        this.contenido = contenido;
        this.idEventoOrigen = idEventoOrigen;
        this.tipoEvento = tipoEvento;
        this.origen = origen;
        this.idClienteOrigen = idClienteOrigen;
    }

    public void cerrar(EstadoEventoCliente nuevoEstado, Long cliente, String nuevaCausa, int intentosRealizados) {
        this.estado = nuevoEstado;
        this.intentos = intentosRealizados;
        if (cliente != null) {
            this.idCliente = cliente;
        }
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

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public EstadoEventoCliente getEstado() {
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

    public int getIntentos() {
        return intentos;
    }

    public OffsetDateTime getProcesadoEn() {
        return procesadoEn;
    }
}
