package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.ClientePerfiles;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.FrecuenciaCompraRepository;
import com.maxiconecta.crm.comportamiento.consumo.ClasificadorConsumo;
import com.maxiconecta.crm.comportamiento.inactividad.DetectorClientesInactivos;
import com.maxiconecta.crm.comportamiento.metrica.PublicadorMetricas;
import com.maxiconecta.crm.comportamiento.validacion.ValidadorEventos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra la compra de un evento ya anotado en la bitácora. Todo ocurre en una transacción:
 * si algo falla, no queda una compra a medias.
 * <p>
 * Una transacción ya procesada (misma clave de idempotencia) se rechaza con
 * {@link CompraDuplicadaException} sin tocar el historial. Ver docs/ingesta/idempotencia-eventos-venta.md.
 * <p>
 * La clave se reserva antes de guardar la compra (ver {@link ControlIdempotencia}).
 * <p>
 * Al confirmarse, publica las métricas del cliente para que Segmentación reevalúe su segmento
 * (SCRUM-26, {@link PublicadorMetricas}).
 */
@Service
public class ProcesadorCompras {

    private final EventoRecibidoRepository eventos;
    private final CompraRepository compras;
    private final ControlIdempotencia idempotencia;
    private final LectorEventos lector;
    private final ValidadorEventos validador;
    private final ClientePerfiles perfiles;
    private final FrecuenciaCompraRepository frecuencias;
    private final DetectorClientesInactivos inactividad;
    private final ClasificadorConsumo consumo;
    private final PublicadorMetricas metricas;

    public ProcesadorCompras(EventoRecibidoRepository eventos, CompraRepository compras,
                             EventoProcesadoRepository procesados, LectorEventos lector, ValidadorEventos validador,
                             ClientePerfiles perfiles, FrecuenciaCompraRepository frecuencias,
                             DetectorClientesInactivos inactividad, ClasificadorConsumo consumo,
                             PublicadorMetricas metricas) {
        this.eventos = eventos;
        this.compras = compras;
        this.idempotencia = new ControlIdempotencia(procesados);
        this.lector = lector;
        this.validador = validador;
        this.perfiles = perfiles;
        this.frecuencias = frecuencias;
        this.inactividad = inactividad;
        this.consumo = consumo;
        this.metricas = metricas;
    }

    @Transactional
    public Compra procesar(Long idEvento) {
        EventoRecibido recibido = eventos.findById(idEvento)
                .orElseThrow(() -> new IllegalStateException("No existe el evento " + idEvento + " en la bitácora"));
        EventoCompraConfirmada evento = lector.leer(recibido.getContenido());
        validador.validar(evento);
        EventoCompraConfirmada.DatosCompra datos = evento.compra();

        ClaveIdempotencia clave = new ClaveIdempotencia(evento.tipoEvento(), datos.idCompra());
        idempotencia.reservar(clave, recibido.getId(),
                motivo -> new CompraDuplicadaException("La compra " + datos.idCompra() + " " + motivo));

        Compra compra = new Compra(datos.idCompra(), datos.idCliente(), datos.fecha(), datos.montoTotal(),
                recibido.getId());
        datos.items().forEach(item -> compra.agregarItem(item.categoria(), item.cantidad(), item.monto()));
        perfiles.buscar(datos.idCliente()).ifPresent(compra::vincularCliente);
        Compra guardada = compras.save(compra);
        frecuencias.incrementar(datos.idCliente());
        recibido.marcarProcesado();
        inactividad.reactivar(datos.idCliente());
        consumo.reclasificar(datos.idCliente());
        metricas.programar(evento.idEvento(), evento.tipoEvento(), guardada);
        return guardada;
    }
}
