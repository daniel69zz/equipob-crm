package com.maxiconecta.crm.perfil.consentimiento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Una entrada del historial del consentimiento: cómo quedó después de un otorgamiento, una
 * actualización o una revocación. Es inmutable: la base rechaza UPDATE, DELETE y TRUNCATE.
 */
@Entity
@Immutable
@Table(schema = "perfil", name = "consentimiento_historial")
public class RegistroConsentimiento {

    private static final String SEPARADOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OperacionConsentimiento operacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConsentimiento estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalConsentimiento canal;

    @Column(nullable = false)
    private String alcances;

    @Column(nullable = false)
    private OffsetDateTime fechaOtorgamiento;

    @Column(nullable = false)
    private LocalDate vigenciaDesde;

    private LocalDate vigenciaHasta;

    private OffsetDateTime fechaRevocacion;

    private String motivoRevocacion;

    @Column(nullable = false)
    private String responsable;

    protected RegistroConsentimiento() {
    }

    public RegistroConsentimiento(Consentimiento consentimiento, OperacionConsentimiento operacion, String responsable) {
        this.idCliente = consentimiento.getIdCliente();
        this.operacion = operacion;
        this.estado = consentimiento.getEstado();
        this.canal = consentimiento.getCanal();
        this.alcances = consentimiento.getAlcances().stream().map(Enum::name).sorted()
                .collect(Collectors.joining(SEPARADOR));
        this.fechaOtorgamiento = consentimiento.getFechaOtorgamiento();
        this.vigenciaDesde = consentimiento.getVigenciaDesde();
        this.vigenciaHasta = consentimiento.getVigenciaHasta();
        this.fechaRevocacion = consentimiento.getFechaRevocacion();
        this.motivoRevocacion = consentimiento.getMotivoRevocacion();
        this.responsable = responsable;
    }

    public Long getId() {
        return id;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public OperacionConsentimiento getOperacion() {
        return operacion;
    }

    public EstadoConsentimiento getEstado() {
        return estado;
    }

    public CanalConsentimiento getCanal() {
        return canal;
    }

    public Set<AlcanceConsentimiento> getAlcances() {
        Set<AlcanceConsentimiento> resultado = EnumSet.noneOf(AlcanceConsentimiento.class);
        Arrays.stream(alcances.split(SEPARADOR)).filter(a -> !a.isBlank())
                .map(AlcanceConsentimiento::valueOf).forEach(resultado::add);
        return resultado;
    }

    public OffsetDateTime getFechaOtorgamiento() {
        return fechaOtorgamiento;
    }

    public LocalDate getVigenciaDesde() {
        return vigenciaDesde;
    }

    public LocalDate getVigenciaHasta() {
        return vigenciaHasta;
    }

    public OffsetDateTime getFechaRevocacion() {
        return fechaRevocacion;
    }

    public String getMotivoRevocacion() {
        return motivoRevocacion;
    }

    public String getResponsable() {
        return responsable;
    }
}
