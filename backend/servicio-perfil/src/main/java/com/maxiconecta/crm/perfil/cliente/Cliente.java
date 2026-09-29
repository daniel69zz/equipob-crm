package com.maxiconecta.crm.perfil.cliente;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Perfil único del cliente en el CRM.
 */
@Entity
@Table(schema = "perfil", name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombres;

    private String apellidos;

    private String tipoDocumento;

    private String numeroDocumento;

    private String email;

    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPerfil estado = EstadoPerfil.COMPLETO;

    private String motivosIncidencia;

    @Column(nullable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    private Origen actualizadoPorOrigen;

    private String actualizadoPor;

    private Long idClienteConsolidado;

    @OneToMany(mappedBy = "cliente", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<Direccion> direcciones = new ArrayList<>();

    public Cliente() {
    }

    /** Aplica los datos de identificación y devuelve qué cambió. */
    public List<CambioCampo> identificar(String nombres, String apellidos, String tipoDocumento,
                                         String numeroDocumento) {
        List<CambioCampo> cambios = new ArrayList<>();
        CambioCampo.siCambio(cambios, "nombres", this.nombres, nombres);
        CambioCampo.siCambio(cambios, "apellidos", this.apellidos, apellidos);
        CambioCampo.siCambio(cambios, "tipoDocumento", this.tipoDocumento, tipoDocumento);
        CambioCampo.siCambio(cambios, "numeroDocumento", this.numeroDocumento, numeroDocumento);
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        return cambios;
    }

    /** Aplica los datos de contacto y devuelve qué cambió. */
    public List<CambioCampo> actualizarContacto(String email, String telefono) {
        List<CambioCampo> cambios = new ArrayList<>();
        CambioCampo.siCambio(cambios, "email", this.email, email);
        CambioCampo.siCambio(cambios, "telefono", this.telefono, telefono);
        this.email = email;
        this.telefono = telefono;
        return cambios;
    }

    /**
     * Sincroniza las direcciones informadas por un sistema de origen: agrega las nuevas, actualiza
     * las existentes y desactiva las que ese sistema ya no informa. Las de otros sistemas no se tocan.
     * Devuelve los cambios aplicados.
     */
    public List<CambioCampo> sincronizarDirecciones(Origen origen, List<Direccion.DatosDireccion> recibidas) {
        List<CambioCampo> cambios = new ArrayList<>();
        Set<String> informadas = new HashSet<>();
        boolean hayPrincipal = false;
        for (Direccion.DatosDireccion datos : recibidas) {
            if (!informadas.add(datos.idDireccionOrigen())) {
                continue;
            }
            boolean principal = datos.principal() && !hayPrincipal;
            hayPrincipal |= principal;
            Direccion.DatosDireccion normalizada = new Direccion.DatosDireccion(datos.idDireccionOrigen(), datos.tipo(),
                    datos.calle(), datos.numero(), datos.zona(), datos.ciudad(), datos.referencia(), principal);
            Direccion direccion = direcciones.stream()
                    .filter(d -> d.getOrigen() == origen && d.getIdDireccionOrigen().equals(datos.idDireccionOrigen()))
                    .findFirst()
                    .orElseGet(() -> {
                        Direccion nueva = new Direccion(this, origen, datos.idDireccionOrigen());
                        direcciones.add(nueva);
                        return nueva;
                    });
            cambios.addAll(direccion.actualizar(normalizada));
        }
        direcciones.stream()
                .filter(d -> d.getOrigen() == origen && !informadas.contains(d.getIdDireccionOrigen()))
                .forEach(d -> cambios.addAll(d.desactivar()));
        return cambios;
    }

    /**
     * Marca el perfil como completo o incompleto según los motivos de la validación de completitud
     * y devuelve el cambio.
     */
    public List<CambioCampo> marcarEstado(List<String> motivos) {
        return marcarEstado(motivos.isEmpty() ? EstadoPerfil.COMPLETO : EstadoPerfil.INCOMPLETO, motivos);
    }

    /**
     * Marca el perfil como inconsistente por las reglas de coherencia incumplidas
     * (ver docs/perfil/catalogo-reglas-validacion.md) y devuelve el cambio.
     */
    public List<CambioCampo> marcarInconsistente(List<String> motivos) {
        if (motivos.isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos un motivo para marcar el perfil como inconsistente");
        }
        return marcarEstado(EstadoPerfil.INCONSISTENTE, motivos);
    }

    private List<CambioCampo> marcarEstado(EstadoPerfil nuevoEstado, List<String> motivos) {
        String nuevosMotivos = motivos.isEmpty() ? null : recortar(String.join("; ", motivos), 1000);
        List<CambioCampo> cambios = new ArrayList<>();
        CambioCampo.siCambio(cambios, "estado", this.id == null ? null : this.estado, nuevoEstado);
        CambioCampo.siCambio(cambios, "motivosIncidencia", this.motivosIncidencia, nuevosMotivos);
        this.estado = nuevoEstado;
        this.motivosIncidencia = nuevosMotivos;
        return cambios;
    }

    private static String recortar(String texto, int largoMaximo) {
        return texto.length() > largoMaximo ? texto.substring(0, largoMaximo) : texto;
    }

    /** El perfil fue absorbido por otro en una unificación. */
    public void consolidarEn(Long idConservado) {
        this.idClienteConsolidado = idConservado;
    }

    public boolean fueConsolidado() {
        return idClienteConsolidado != null;
    }

    public void registrarActualizacion(Origen origen, String responsable) {
        this.actualizadoEn = OffsetDateTime.now();
        this.actualizadoPorOrigen = origen;
        this.actualizadoPor = responsable;
    }

    public Long getId() {
        return id;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroDocumento() {
        return numeroDocumento;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefono() {
        return telefono;
    }

    public Long getIdClienteConsolidado() {
        return idClienteConsolidado;
    }

    public List<Direccion> getDirecciones() {
        return direcciones;
    }

    public List<Direccion> getDireccionesActivas() {
        return direcciones.stream().filter(Direccion::isActiva).toList();
    }

    public EstadoPerfil getEstado() {
        return estado;
    }

    public String getMotivosIncidencia() {
        return motivosIncidencia;
    }

    public OffsetDateTime getCreadoEn() {
        return creadoEn;
    }

    public OffsetDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    public Origen getActualizadoPorOrigen() {
        return actualizadoPorOrigen;
    }

    public String getActualizadoPor() {
        return actualizadoPor;
    }
}
