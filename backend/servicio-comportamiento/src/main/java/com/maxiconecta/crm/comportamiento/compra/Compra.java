package com.maxiconecta.crm.comportamiento.compra;

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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Compra confirmada de un cliente, registrada a partir de un evento de Marketplace y Ventas.
 * Conserva su monto total; las anulaciones y devoluciones (RIO-CRM-05) se acumulan en
 * {@code montoRevertido}, y lo que sigue vigente es la diferencia.
 */
@Entity
@Table(schema = "comportamiento", name = "compra")
public class Compra {

    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String DEVOLUCION_PARCIAL = "DEVOLUCION_PARCIAL";
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Origen origen;

    @Column(nullable = false)
    private String idCompraOrigen;

    @Column(nullable = false)
    private String idClienteOrigen;

    private Long idCliente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoVinculacionCompra estadoVinculacion = EstadoVinculacionCompra.PENDIENTE;

    @Column(nullable = false)
    private OffsetDateTime fecha;

    @Column(nullable = false)
    private BigDecimal montoTotal;

    @Column(nullable = false)
    private String estado = CONFIRMADA;

    @Column(nullable = false)
    private BigDecimal montoRevertido = BigDecimal.ZERO;

    @Column(name = "id_evento", nullable = false)
    private Long idEvento;

    @Column(nullable = false)
    private OffsetDateTime registradaEn = OffsetDateTime.now();

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CompraItem> items = new ArrayList<>();

    protected Compra() {
    }

    public Compra(Origen origen, String idCompraOrigen, String idClienteOrigen, OffsetDateTime fecha,
                  BigDecimal montoTotal, Long idEvento) {
        this.origen = origen;
        this.idCompraOrigen = idCompraOrigen;
        this.idClienteOrigen = idClienteOrigen;
        this.fecha = fecha;
        this.montoTotal = montoTotal;
        this.idEvento = idEvento;
    }

    public void agregarItem(String categoria, int cantidad, BigDecimal monto) {
        items.add(new CompraItem(this, categoria, cantidad, monto));
    }

    public void vincularCliente(Long idCliente) {
        if (idCliente == null || idCliente <= 0) {
            throw new IllegalArgumentException("El identificador del perfil debe ser positivo");
        }
        this.idCliente = idCliente;
        this.estadoVinculacion = EstadoVinculacionCompra.VINCULADA;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public EstadoVinculacionCompra getEstadoVinculacion() {
        return estadoVinculacion;
    }

    /**
     * Descuenta de la compra el monto de una anulación o devolución ya validada. Si no queda nada
     * vigente, la compra pasa a ANULADA; si queda una parte, a DEVOLUCION_PARCIAL.
     */
    public void revertir(BigDecimal monto) {
        if (monto.signum() <= 0 || monto.compareTo(getMontoVigente()) > 0) {
            throw new IllegalArgumentException("No se puede revertir " + monto.toPlainString()
                    + " de una compra con " + getMontoVigente().toPlainString() + " vigente");
        }
        montoRevertido = montoRevertido.add(monto);
        estado = getMontoVigente().signum() == 0 ? ANULADA : DEVOLUCION_PARCIAL;
    }

    public boolean estaAnulada() {
        return ANULADA.equals(estado);
    }

    /** Lo que sigue vigente de la compra después de sus anulaciones y devoluciones. */
    public BigDecimal getMontoVigente() {
        return montoTotal.subtract(montoRevertido);
    }

    public Long getId() {
        return id;
    }

    public Origen getOrigen() {
        return origen;
    }

    public String getIdCompraOrigen() {
        return idCompraOrigen;
    }

    public String getIdClienteOrigen() {
        return idClienteOrigen;
    }

    public OffsetDateTime getFecha() {
        return fecha;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public String getEstado() {
        return estado;
    }

    public BigDecimal getMontoRevertido() {
        return montoRevertido;
    }

    public Long getIdEvento() {
        return idEvento;
    }

    public OffsetDateTime getRegistradaEn() {
        return registradaEn;
    }

    public List<CompraItem> getItems() {
        return items;
    }
}
