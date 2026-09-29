package com.maxiconecta.crm.comportamiento.ingesta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Trazabilidad de una solicitud manual de reproceso de un evento fallido. */
@Entity
@Table(schema = "comportamiento", name = "intento_reproceso")
public class IntentoReproceso {

    private static final int LARGO_MAXIMO_CAUSA = 500;
    private static final int LARGO_MAXIMO_USUARIO = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_evento_recibido", nullable = false)
    private EventoRecibido evento;

    @Column(name = "numero_intento", nullable = false)
    private int numero;

    @Column(name = "intentado_en", nullable = false)
    private OffsetDateTime intentadoEn = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultadoReproceso resultado = ResultadoReproceso.EN_PROCESO;

    private String causa;

    private String usuario;

    protected IntentoReproceso() {
    }

    public IntentoReproceso(EventoRecibido evento, int numero, String usuario) {
        this.evento = evento;
        this.numero = numero;
        this.usuario = limitar(usuario, LARGO_MAXIMO_USUARIO);
    }

    public void completar(ResultadoReproceso nuevoResultado, String nuevaCausa) {
        if (resultado != ResultadoReproceso.EN_PROCESO) {
            throw new IllegalStateException("El intento " + id + " ya terminó con estado " + resultado);
        }
        if (nuevoResultado == ResultadoReproceso.EN_PROCESO) {
            throw new IllegalArgumentException("El resultado final no puede ser EN_PROCESO");
        }
        resultado = nuevoResultado;
        causa = limitar(nuevaCausa, LARGO_MAXIMO_CAUSA);
    }

    private static String limitar(String valor, int largoMaximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.length() > largoMaximo ? limpio.substring(0, largoMaximo) : limpio;
    }

    public Long getId() {
        return id;
    }

    public Long getIdEvento() {
        return evento.getId();
    }

    public int getNumero() {
        return numero;
    }

    public OffsetDateTime getIntentadoEn() {
        return intentadoEn;
    }

    public ResultadoReproceso getResultado() {
        return resultado;
    }

    public String getCausa() {
        return causa;
    }

    public String getUsuario() {
        return usuario;
    }
}
