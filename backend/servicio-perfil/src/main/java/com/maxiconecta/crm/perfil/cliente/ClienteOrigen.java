package com.maxiconecta.crm.perfil.cliente;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * Identificador del cliente en el módulo Marketplace y Ventas, vinculado a un perfil del CRM (RF-62).
 * Varios identificadores pueden apuntar al mismo perfil (por ejemplo, después de unificar duplicados).
 * <p>
 * Implementa {@link Persistable} para que vincular un identificador nuevo sea siempre un INSERT:
 * si otro mensaje ya lo vinculó, la llave primaria lo rechaza.
 */
@Entity
@Table(schema = "perfil", name = "cliente_origen")
public class ClienteOrigen implements Persistable<String> {

    public static final String ALTA_AUTOMATICA = "ALTA_AUTOMATICA";
    public static final String VINCULACION_MANUAL = "VINCULACION_MANUAL";
    public static final String UNIFICACION = "UNIFICACION";
    public static final String SINCRONIZACION_AUTOMATICA = "sincronizacion-automatica";

    @Id
    private String idClienteOrigen;

    @Column(nullable = false)
    private Long idCliente;

    @Column(nullable = false)
    private OffsetDateTime fechaVinculacion = OffsetDateTime.now();

    @Column(nullable = false)
    private String motivoVinculacion = ALTA_AUTOMATICA;

    private OffsetDateTime ultimaActualizacionOrigen;

    @Column(nullable = false)
    private String vinculadoPor = SINCRONIZACION_AUTOMATICA;

    @Transient
    private boolean nuevo = true;

    protected ClienteOrigen() {
    }

    public ClienteOrigen(String idClienteOrigen, Long idCliente, OffsetDateTime ultimaActualizacionOrigen) {
        this.idClienteOrigen = idClienteOrigen;
        this.idCliente = idCliente;
        this.ultimaActualizacionOrigen = ultimaActualizacionOrigen;
    }

    /** Vínculo hecho por una persona, no por la llegada de un evento. */
    public static ClienteOrigen vincular(String idClienteOrigen, Long idCliente, String motivo, String vinculadoPor,
                                         OffsetDateTime ultimaActualizacionOrigen) {
        ClienteOrigen vinculo = new ClienteOrigen(idClienteOrigen, idCliente, ultimaActualizacionOrigen);
        vinculo.motivoVinculacion = motivo;
        vinculo.vinculadoPor = vinculadoPor;
        return vinculo;
    }

    /** Pasa el identificador a otro perfil, conservando la fecha del último cambio aplicado. */
    public void reasignar(Long nuevoCliente, String motivo, String responsable) {
        this.idCliente = nuevoCliente;
        this.motivoVinculacion = motivo;
        this.vinculadoPor = responsable;
        this.fechaVinculacion = OffsetDateTime.now();
    }

    public void registrarActualizacion(OffsetDateTime fechaCambio) {
        this.ultimaActualizacionOrigen = fechaCambio;
    }

    @Override
    public String getId() {
        return idClienteOrigen;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PostPersist
    void marcarExistente() {
        nuevo = false;
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public OffsetDateTime getFechaVinculacion() {
        return fechaVinculacion;
    }

    public String getMotivoVinculacion() {
        return motivoVinculacion;
    }

    public OffsetDateTime getUltimaActualizacionOrigen() {
        return ultimaActualizacionOrigen;
    }

    public String getVinculadoPor() {
        return vinculadoPor;
    }
}
