package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.compra.Origen;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Cliente sin compras vigentes desde hace más del umbral de inactividad configurado
 * (docs/compra/clientes-inactivos.md, SCRUM-296). Se recalcula en cada ejecución programada
 * (SCRUM-297) y se quita de inmediato en cuanto el cliente vuelve a comprar.
 */
@Entity
@Table(schema = "comportamiento", name = "cliente_inactivo")
public class ClienteInactivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(nullable = false)
    private String idClienteOrigen;

    @Column(nullable = false)
    private OffsetDateTime ultimaCompra;

    @Column(nullable = false)
    private OffsetDateTime detectadoEn = OffsetDateTime.now();

    protected ClienteInactivo() {
    }

    public ClienteInactivo(Origen origen, String idClienteOrigen, OffsetDateTime ultimaCompra) {
        this.origen = origen;
        this.idClienteOrigen = idClienteOrigen;
        this.ultimaCompra = ultimaCompra;
    }

    /** Actualiza la última compra conocida en una ejecución posterior en la que el cliente sigue inactivo. */
    public void actualizar(OffsetDateTime ultimaCompra) {
        this.ultimaCompra = ultimaCompra;
        this.detectadoEn = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public OffsetDateTime getUltimaCompra() {
        return ultimaCompra;
    }

    public OffsetDateTime getDetectadoEn() {
        return detectadoEn;
    }
}
