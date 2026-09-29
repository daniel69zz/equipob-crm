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
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una operación del histórico del perfil: fecha, tipo, origen (Marketplace y Ventas o CRM), responsable y los campos
 * cambiados, uno por fila en {@link CambioPerfilDetalle} (el JSON de {@code cambios} es su resumen).
 * Es inmutable: la base rechaza UPDATE y DELETE. Ver docs/perfil/historico-cambios.md.
 */
@Entity
@Immutable
@Table(schema = "perfil", name = "cambio_perfil")
public class CambioPerfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long idCliente;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCambio tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(nullable = false)
    private String responsable;

    @Column(nullable = false)
    private String cambios;

    private Long idEvento;

    @OneToMany(mappedBy = "cambio", cascade = CascadeType.PERSIST)
    @OrderBy("orden")
    private List<CambioPerfilDetalle> detalles = new ArrayList<>();

    protected CambioPerfil() {
    }

    /**
     * @param cambiosComoJson resumen en JSON de {@code campos}, que se guardan además uno por fila
     */
    public CambioPerfil(Long idCliente, TipoCambio tipo, Origen origen, String responsable, List<CambioCampo> campos,
                        String cambiosComoJson, Long idEvento) {
        this.idCliente = idCliente;
        this.tipo = tipo;
        this.origen = origen;
        this.responsable = responsable;
        this.cambios = cambiosComoJson;
        this.idEvento = idEvento;
        for (int i = 0; i < campos.size(); i++) {
            detalles.add(new CambioPerfilDetalle(this, i + 1, campos.get(i)));
        }
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

    public TipoCambio getTipo() {
        return tipo;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getResponsable() {
        return responsable;
    }

    public String getCambios() {
        return cambios;
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public List<CambioPerfilDetalle> getDetalles() {
        return detalles;
    }
}
