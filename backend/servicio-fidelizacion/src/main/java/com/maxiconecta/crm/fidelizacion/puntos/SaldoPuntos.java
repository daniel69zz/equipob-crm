package com.maxiconecta.crm.fidelizacion.puntos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Saldo y nivel de puntos vigentes de un cliente (SCRUM-154). Las reglas de acumulación, canje y
 * vigencia no son parte de esta historia: este servicio solo responde la consulta síncrona del
 * saldo ya calculado, que consolida la ficha integral (SCRUM-10, contrato RIO-CRM-04).
 */
@Entity
@Table(schema = "fidelizacion", name = "saldo_puntos")
public class SaldoPuntos {

    @Id
    private Long clienteId;

    @Column(nullable = false)
    private int saldo;

    @Column(nullable = false)
    private String nivel;

    @Column(nullable = false)
    private OffsetDateTime actualizadoEn;

    protected SaldoPuntos() {
    }

    public SaldoPuntos(Long clienteId, int saldo, String nivel, OffsetDateTime actualizadoEn) {
        this.clienteId = clienteId;
        this.saldo = saldo;
        this.nivel = nivel;
        this.actualizadoEn = actualizadoEn;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public int getSaldo() {
        return saldo;
    }

    public String getNivel() {
        return nivel;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }
}
