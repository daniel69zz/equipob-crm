package com.maxiconecta.crm.perfil.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

/**
 * Traza de un conflicto entre sistemas sobre un campo del perfil y de la decisión tomada.
 */
@Entity
@Immutable
@Table(schema = "perfil", name = "conflicto_perfil")
public class ConflictoPerfil {

    public enum Decision { APLICADO, CONSERVADO }

    public enum Regla { PRIORIDAD_SISTEMA, MAS_RECIENTE }

    private static final int LARGO_MAXIMO_VALOR = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    @Column(nullable = false)
    private String campo;

    private String valorActual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origenActual;

    private String valorRecibido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origenRecibido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Regla regla;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    private Long idEvento;

    protected ConflictoPerfil() {
    }

    public ConflictoPerfil(Long idCliente, String campo, String valorActual, Origen origenActual, String valorRecibido,
                           Origen origenRecibido, Decision decision, Regla regla, Long idEvento) {
        this.idCliente = idCliente;
        this.campo = campo;
        this.valorActual = recortar(valorActual);
        this.origenActual = origenActual;
        this.valorRecibido = recortar(valorRecibido);
        this.origenRecibido = origenRecibido;
        this.decision = decision;
        this.regla = regla;
        this.idEvento = idEvento;
    }

    private static String recortar(String valor) {
        return valor != null && valor.length() > LARGO_MAXIMO_VALOR ? valor.substring(0, LARGO_MAXIMO_VALOR) : valor;
    }

    /** "email: CONSERVADO (MAS_RECIENTE)" */
    public String resumen() {
        return campo + ": " + decision + " (" + regla + ")";
    }

    public Long getId() {
        return id;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorActual() {
        return valorActual;
    }

    public Origen getOrigenActual() {
        return origenActual;
    }

    public String getValorRecibido() {
        return valorRecibido;
    }

    public Origen getOrigenRecibido() {
        return origenRecibido;
    }

    public Decision getDecision() {
        return decision;
    }

    public Regla getRegla() {
        return regla;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public Long getIdEvento() {
        return idEvento;
    }
}
