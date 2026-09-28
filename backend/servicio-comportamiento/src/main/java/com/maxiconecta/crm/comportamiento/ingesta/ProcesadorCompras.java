package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.Origen;
import com.maxiconecta.crm.comportamiento.validacion.ValidadorEventos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra la compra de un evento ya anotado en la bitácora. Todo ocurre en una transacción:
 * si algo falla, no queda una compra a medias.
 */
@Service
public class ProcesadorCompras {

    private final EventoRecibidoRepository eventos;
    private final CompraRepository compras;
    private final LectorEventos lector;
    private final ValidadorEventos validador;

    public ProcesadorCompras(EventoRecibidoRepository eventos, CompraRepository compras, LectorEventos lector,
                             ValidadorEventos validador) {
        this.eventos = eventos;
        this.compras = compras;
        this.lector = lector;
        this.validador = validador;
    }

    @Transactional
    public Compra procesar(Long idEvento) {
        EventoRecibido recibido = eventos.findById(idEvento)
                .orElseThrow(() -> new IllegalStateException("No existe el evento " + idEvento + " en la bitácora"));
        EventoCompraConfirmada evento = lector.leer(recibido.getContenido());
        validador.validar(evento);
        Origen origen = origen(evento.origen());
        EventoCompraConfirmada.DatosCompra datos = evento.compra();

        compras.findByOrigenAndIdCompraOrigen(origen, datos.idCompra()).ifPresent(existente -> {
            throw new CompraDuplicadaException("La compra " + origen + "/" + datos.idCompra()
                    + " ya fue registrada por el evento " + existente.getIdEvento());
        });

        Compra compra = new Compra(origen, datos.idCompra(), datos.idCliente(), datos.fecha(), datos.montoTotal(),
                recibido.getId());
        datos.items().forEach(item -> compra.agregarItem(item.categoria(), item.cantidad(), item.monto()));
        Compra guardada = compras.save(compra);
        recibido.marcarProcesado();
        return guardada;
    }

    private static Origen origen(String valor) {
        try {
            return Origen.valueOf(valor);
        } catch (IllegalArgumentException ex) {
            throw new EventoIlegibleException("Origen desconocido: " + valor + " (se espera MARKETPLACE o VENTAS)");
        }
    }
}
