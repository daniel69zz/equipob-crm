package com.maxiconecta.crm.perfil.cliente;

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
import java.util.ArrayList;
import java.util.List;

/**
 * Dirección del cliente, identificada por su código en el sistema de origen.
 */
@Entity
@Table(schema = "perfil", name = "direccion")
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(nullable = false)
    private String idDireccionOrigen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDireccion tipo;

    @Column(nullable = false)
    private String calle;

    private String numero;

    private String zona;

    @Column(nullable = false)
    private String ciudad;

    private String referencia;

    @Column(nullable = false)
    private boolean principal;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(nullable = false)
    private OffsetDateTime actualizadaEn = OffsetDateTime.now();

    protected Direccion() {
    }

    Direccion(Cliente cliente, Origen origen, String idDireccionOrigen) {
        this.cliente = cliente;
        this.origen = origen;
        this.idDireccionOrigen = idDireccionOrigen;
    }

    /**
     * Aplica los datos recibidos y devuelve qué cambió. Una dirección desactivada vuelve a activarse.
     */
    List<CambioCampo> actualizar(DatosDireccion datos) {
        String prefijo = "direcciones[" + idDireccionOrigen + "].";
        List<CambioCampo> cambios = new ArrayList<>();
        CambioCampo.siCambio(cambios, prefijo + "tipo", tipo, datos.tipo());
        CambioCampo.siCambio(cambios, prefijo + "calle", calle, datos.calle());
        CambioCampo.siCambio(cambios, prefijo + "numero", numero, datos.numero());
        CambioCampo.siCambio(cambios, prefijo + "zona", zona, datos.zona());
        CambioCampo.siCambio(cambios, prefijo + "ciudad", ciudad, datos.ciudad());
        CambioCampo.siCambio(cambios, prefijo + "referencia", referencia, datos.referencia());
        CambioCampo.siCambio(cambios, prefijo + "principal", principal, datos.principal());
        CambioCampo.siCambio(cambios, prefijo + "activa", activa, true);
        tipo = datos.tipo();
        calle = datos.calle();
        numero = datos.numero();
        zona = datos.zona();
        ciudad = datos.ciudad();
        referencia = datos.referencia();
        principal = datos.principal();
        activa = true;
        if (!cambios.isEmpty()) {
            actualizadaEn = OffsetDateTime.now();
        }
        return cambios;
    }

    /** El sistema de origen dejó de informarla: se desactiva, no se borra. Si era la principal, deja de serlo. */
    List<CambioCampo> desactivar() {
        if (!activa) {
            return List.of();
        }
        String prefijo = "direcciones[" + idDireccionOrigen + "].";
        List<CambioCampo> cambios = new ArrayList<>();
        if (principal) {
            cambios.add(new CambioCampo(prefijo + "principal", "true", "false"));
        }
        cambios.add(new CambioCampo(prefijo + "activa", "true", "false"));
        activa = false;
        principal = false;
        actualizadaEn = OffsetDateTime.now();
        return cambios;
    }

    public Long getId() {
        return id;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getIdDireccionOrigen() {
        return idDireccionOrigen;
    }

    public TipoDireccion getTipo() {
        return tipo;
    }

    public String getCalle() {
        return calle;
    }

    public String getNumero() {
        return numero;
    }

    public String getZona() {
        return zona;
    }

    public String getCiudad() {
        return ciudad;
    }

    public String getReferencia() {
        return referencia;
    }

    public boolean isPrincipal() {
        return principal;
    }

    public boolean isActiva() {
        return activa;
    }

    public OffsetDateTime getActualizadaEn() {
        return actualizadaEn;
    }

    /** Datos válidos de una dirección, listos para guardarse. */
    public record DatosDireccion(String idDireccionOrigen, TipoDireccion tipo, String calle, String numero,
                                 String zona, String ciudad, String referencia, boolean principal) {
    }
}
