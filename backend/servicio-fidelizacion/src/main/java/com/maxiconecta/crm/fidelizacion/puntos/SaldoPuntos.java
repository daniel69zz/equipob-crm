package com.maxiconecta.crm.fidelizacion.puntos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Saldo y nivel de puntos vigentes de un cliente (SCRUM-154), que consolida la ficha integral
 * (SCRUM-10, contrato RIO-CRM-04). El saldo lo acumulan los movimientos RIO-CRM-03 (SCRUM-45,
 * {@code movimiento.ProcesadorMovimientos}); el nivel lo asigna SCRUM-49 ({@code nivel.AsignacionNiveles})
 * con los puntos del periodo de evaluación, y falta si no alcanzan el umbral más bajo.
 */
@Entity
@Table(schema = "fidelizacion", name = "saldo_puntos")
public class SaldoPuntos {

    @Id
    private Long clienteId;

    @Column(nullable = false)
    private long saldo;

    private String nivel;

    @Column(nullable = false)
    private OffsetDateTime actualizadoEn;

    @Column(nullable = false)
    private long puntosPeriodo;

    private OffsetDateTime nivelActualizadoEn;

    protected SaldoPuntos() {
    }

    public SaldoPuntos(Long clienteId, long saldo, String nivel, OffsetDateTime actualizadoEn) {
        this(clienteId, saldo, nivel, actualizadoEn, 0, null);
    }

    public SaldoPuntos(Long clienteId, long saldo, String nivel, OffsetDateTime actualizadoEn, long puntosPeriodo,
                       OffsetDateTime nivelActualizadoEn) {
        this.clienteId = clienteId;
        this.saldo = saldo;
        this.nivel = nivel;
        this.actualizadoEn = actualizadoEn;
        this.puntosPeriodo = puntosPeriodo;
        this.nivelActualizadoEn = nivelActualizadoEn;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public long getSaldo() {
        return saldo;
    }

    public String getNivel() {
        return nivel;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public long getPuntosPeriodo() {
        return puntosPeriodo;
    }

    public OffsetDateTime getNivelActualizadoEn() {
        return nivelActualizadoEn;
    }
}
