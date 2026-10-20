package com.maxiconecta.crm.comportamiento.riesgo;

import com.maxiconecta.crm.comportamiento.consumo.NivelConsumo;
import com.maxiconecta.crm.comportamiento.frecuencia.ClasificacionFrecuencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Riesgo de abandono vigente de un cliente del CRM (SCRUM-571, docs/compra/riesgo-abandono.md). Se
 * sobrescribe con cada recálculo y se elimina cuando el cliente se queda sin compras vigentes.
 * {@code enSeguimientoDesde} marca desde cuándo está en riesgo ALTO para el seguimiento comercial
 * (SCRUM-326): se fija al entrar en ALTO, se conserva mientras siga ahí y se borra al bajar de nivel.
 */
@Entity
@Table(schema = "comportamiento", name = "riesgo_abandono_cliente")
public class RiesgoAbandonoCliente {

    @Id
    private Long clienteId;

    @Column(nullable = false)
    private BigDecimal puntaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NivelRiesgo nivel;

    @Column(nullable = false)
    private int diasSinComprar;

    @Column(nullable = false)
    private OffsetDateTime ultimaCompra;

    @Column(nullable = false)
    private long compras;

    @Column(nullable = false)
    private BigDecimal valorAcumulado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClasificacionFrecuencia clasificacionFrecuencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NivelConsumo nivelConsumo;

    @Column(nullable = false)
    private boolean inactivo;

    @Column(nullable = false)
    private boolean cambioDetectado;

    @Column(nullable = false)
    private BigDecimal puntosRecencia;

    @Column(nullable = false)
    private BigDecimal puntosFrecuencia;

    @Column(nullable = false)
    private BigDecimal puntosValor;

    @Column(nullable = false)
    private BigDecimal puntosCambio;

    private OffsetDateTime enSeguimientoDesde;

    @Column(nullable = false)
    private OffsetDateTime calculadoEn;

    protected RiesgoAbandonoCliente() {
    }

    public RiesgoAbandonoCliente(Long clienteId, EvaluacionRiesgo evaluacion, OffsetDateTime calculadoEn) {
        this.clienteId = clienteId;
        aplicar(evaluacion, calculadoEn);
    }

    /**
     * Aplica una nueva evaluación. Devuelve {@code true} si con ella el cliente entra en riesgo ALTO
     * (no lo estaba): es el momento en que queda identificado para seguimiento comercial.
     */
    public boolean aplicar(EvaluacionRiesgo evaluacion, OffsetDateTime calculadoEn) {
        boolean entraEnAlto = evaluacion.nivel() == NivelRiesgo.ALTO && this.nivel != NivelRiesgo.ALTO;
        if (evaluacion.nivel() != NivelRiesgo.ALTO) {
            this.enSeguimientoDesde = null;
        } else if (entraEnAlto) {
            this.enSeguimientoDesde = calculadoEn;
        }
        this.puntaje = evaluacion.puntaje();
        this.nivel = evaluacion.nivel();
        this.diasSinComprar = Math.toIntExact(evaluacion.diasSinComprar());
        this.ultimaCompra = evaluacion.ultimaCompra();
        this.compras = evaluacion.compras();
        this.valorAcumulado = evaluacion.valorAcumulado();
        this.clasificacionFrecuencia = evaluacion.clasificacionFrecuencia();
        this.nivelConsumo = evaluacion.nivelConsumo();
        this.inactivo = evaluacion.inactivo();
        this.cambioDetectado = evaluacion.cambioDetectado();
        this.puntosRecencia = evaluacion.puntosRecencia();
        this.puntosFrecuencia = evaluacion.puntosFrecuencia();
        this.puntosValor = evaluacion.puntosValor();
        this.puntosCambio = evaluacion.puntosCambio();
        this.calculadoEn = calculadoEn;
        return entraEnAlto;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public BigDecimal getPuntaje() {
        return puntaje;
    }

    public NivelRiesgo getNivel() {
        return nivel;
    }

    public int getDiasSinComprar() {
        return diasSinComprar;
    }

    public OffsetDateTime getUltimaCompra() {
        return ultimaCompra;
    }

    public long getCompras() {
        return compras;
    }

    public BigDecimal getValorAcumulado() {
        return valorAcumulado;
    }

    public ClasificacionFrecuencia getClasificacionFrecuencia() {
        return clasificacionFrecuencia;
    }

    public NivelConsumo getNivelConsumo() {
        return nivelConsumo;
    }

    public boolean isInactivo() {
        return inactivo;
    }

    public boolean isCambioDetectado() {
        return cambioDetectado;
    }

    public BigDecimal getPuntosRecencia() {
        return puntosRecencia;
    }

    public BigDecimal getPuntosFrecuencia() {
        return puntosFrecuencia;
    }

    public BigDecimal getPuntosValor() {
        return puntosValor;
    }

    public BigDecimal getPuntosCambio() {
        return puntosCambio;
    }

    public OffsetDateTime getEnSeguimientoDesde() {
        return enSeguimientoDesde;
    }

    public OffsetDateTime getCalculadoEn() {
        return calculadoEn;
    }
}
