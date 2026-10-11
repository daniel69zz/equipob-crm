package com.maxiconecta.crm.comportamiento.cambio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Variación relevante detectada en la frecuencia o el monto de compra de un cliente (SCRUM-319,
 * docs/compra/cambios-comportamiento.md). Es inmutable: queda como registro auditable de la
 * detección y es la alerta que se muestra en la ficha del cliente (SCRUM-315).
 */
@Entity
@Table(schema = "comportamiento", name = "deteccion_cambio_comportamiento")
public class DeteccionCambioComportamiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String idClienteOrigen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoVariacion tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DireccionVariacion direccion;

    @Column(nullable = false)
    private BigDecimal valorActual;

    @Column(nullable = false)
    private BigDecimal valorHabitual;

    @Column(nullable = false)
    private BigDecimal variacion;

    @Column(nullable = false)
    private BigDecimal umbral;

    @Column(nullable = false)
    private OffsetDateTime periodoDesde;

    @Column(nullable = false)
    private OffsetDateTime periodoHasta;

    @Column(nullable = false)
    private OffsetDateTime detectadoEn;

    protected DeteccionCambioComportamiento() {
    }

    public DeteccionCambioComportamiento(String idClienteOrigen, Variacion variacion, BigDecimal umbral,
                                         Ventanas ventanas) {
        this.idClienteOrigen = idClienteOrigen;
        this.tipo = variacion.tipo();
        this.direccion = variacion.direccion();
        this.valorActual = variacion.actual();
        this.valorHabitual = variacion.habitual();
        this.variacion = variacion.relativa();
        this.umbral = umbral;
        this.periodoDesde = ventanas.inicioActual();
        this.periodoHasta = ventanas.fin();
        this.detectadoEn = ventanas.fin();
    }

    public Long getId() {
        return id;
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public TipoVariacion getTipo() {
        return tipo;
    }

    public DireccionVariacion getDireccion() {
        return direccion;
    }

    public BigDecimal getValorActual() {
        return valorActual;
    }

    public BigDecimal getValorHabitual() {
        return valorHabitual;
    }

    public BigDecimal getVariacion() {
        return variacion;
    }

    public BigDecimal getUmbral() {
        return umbral;
    }

    public OffsetDateTime getPeriodoDesde() {
        return periodoDesde;
    }

    public OffsetDateTime getPeriodoHasta() {
        return periodoHasta;
    }

    public OffsetDateTime getDetectadoEn() {
        return detectadoEn;
    }
}
