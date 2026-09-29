package com.maxiconecta.crm.perfil.consentimiento;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Estado actual del consentimiento de tratamiento de datos de un cliente, con su alcance y
 * vigencia (docs/perfil/consentimiento-datos.md). Cada cambio se registra además en
 * {@link RegistroConsentimiento}.
 */
@Entity
@Table(schema = "perfil", name = "consentimiento")
public class Consentimiento {

    @Id
    private Long idCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoConsentimiento estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalConsentimiento canal;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(schema = "perfil", name = "consentimiento_alcance", joinColumns = @JoinColumn(name = "id_cliente"))
    @Enumerated(EnumType.STRING)
    @Column(name = "alcance", nullable = false)
    private Set<AlcanceConsentimiento> alcances = new HashSet<>();

    @Column(nullable = false)
    private OffsetDateTime fechaOtorgamiento;

    @Column(nullable = false)
    private LocalDate vigenciaDesde;

    private LocalDate vigenciaHasta;

    private OffsetDateTime fechaRevocacion;

    private String motivoRevocacion;

    @Column(nullable = false)
    private OffsetDateTime actualizadoEn;

    @Column(nullable = false)
    private String actualizadoPor;

    protected Consentimiento() {
    }

    private Consentimiento(Long idCliente) {
        this.idCliente = idCliente;
    }

    /** Primer consentimiento del cliente. */
    public static Consentimiento otorgar(Long idCliente, DatosConsentimiento datos, String responsable) {
        Consentimiento consentimiento = new Consentimiento(idCliente);
        consentimiento.asignar(datos, responsable);
        return consentimiento;
    }

    /**
     * Aplica un otorgamiento o una actualización sobre el consentimiento existente.
     *
     * @return {@code OTORGAMIENTO} si estaba revocado, {@code ACTUALIZACION} si cambió algún dato de
     * uno otorgado, o null si llegaron los mismos datos que ya tenía
     */
    public OperacionConsentimiento aplicar(DatosConsentimiento datos, String responsable) {
        if (estado == EstadoConsentimiento.REVOCADO) {
            asignar(datos, responsable);
            return OperacionConsentimiento.OTORGAMIENTO;
        }
        if (tieneLosMismosDatos(datos)) {
            return null;
        }
        asignar(datos, responsable);
        return OperacionConsentimiento.ACTUALIZACION;
    }

    /** Retira el consentimiento. Solo se llama sobre uno otorgado. */
    public void revocar(String motivo, String responsable) {
        if (estado != EstadoConsentimiento.OTORGADO) {
            throw new IllegalStateException("Solo se puede revocar un consentimiento otorgado");
        }
        this.estado = EstadoConsentimiento.REVOCADO;
        this.fechaRevocacion = OffsetDateTime.now();
        this.motivoRevocacion = motivo;
        registrarCambio(responsable);
    }

    /** Otorgado y con la fecha {@code hoy} dentro de la vigencia, ambos extremos incluidos. */
    public boolean estaVigente(LocalDate hoy) {
        return estado == EstadoConsentimiento.OTORGADO && !hoy.isBefore(vigenciaDesde)
                && (vigenciaHasta == null || !hoy.isAfter(vigenciaHasta));
    }

    /** Si el cliente autoriza hoy el uso de sus datos para esa finalidad. */
    public boolean autoriza(AlcanceConsentimiento alcance, LocalDate hoy) {
        return estaVigente(hoy) && alcances.contains(alcance);
    }

    private void asignar(DatosConsentimiento datos, String responsable) {
        this.estado = EstadoConsentimiento.OTORGADO;
        this.canal = datos.canal();
        this.alcances.clear();
        this.alcances.addAll(datos.alcances());
        this.fechaOtorgamiento = datos.fechaOtorgamiento();
        this.vigenciaDesde = datos.vigenciaDesde();
        this.vigenciaHasta = datos.vigenciaHasta();
        this.fechaRevocacion = null;
        this.motivoRevocacion = null;
        registrarCambio(responsable);
    }

    private boolean tieneLosMismosDatos(DatosConsentimiento datos) {
        return canal == datos.canal() && alcances.equals(datos.alcances())
                && fechaOtorgamiento.isEqual(datos.fechaOtorgamiento())
                && vigenciaDesde.equals(datos.vigenciaDesde()) && Objects.equals(vigenciaHasta, datos.vigenciaHasta());
    }

    private void registrarCambio(String responsable) {
        this.actualizadoEn = OffsetDateTime.now();
        this.actualizadoPor = responsable;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public EstadoConsentimiento getEstado() {
        return estado;
    }

    public CanalConsentimiento getCanal() {
        return canal;
    }

    /** Las finalidades autorizadas, en el orden en que están declaradas. */
    public Set<AlcanceConsentimiento> getAlcances() {
        return alcances.isEmpty() ? EnumSet.noneOf(AlcanceConsentimiento.class) : EnumSet.copyOf(alcances);
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

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public String getActualizadoPor() {
        return actualizadoPor;
    }
}
