package com.maxiconecta.crm.comportamiento.ingesta;

import com.maxiconecta.crm.comportamiento.compra.Anulacion;
import com.maxiconecta.crm.comportamiento.compra.AnulacionRepository;
import com.maxiconecta.crm.comportamiento.compra.Compra;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.TipoAnulacion;
import com.maxiconecta.crm.comportamiento.validacion.ValidadorAnulaciones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra la anulación o devolución de un evento RIO-CRM-05 ya anotado en la bitácora y la
 * descuenta de la compra original, todo en una transacción
 * (docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md).
 * <p>
 * La clave de idempotencia se reserva antes de comparar con la compra: un reenvío de una anulación
 * ya aplicada queda DESCARTADO, aunque la compra ya esté anulada. Si después una regla falla, la
 * transacción se revierte y la clave queda libre para el reproceso.
 */
@Service
public class ProcesadorAnulaciones {

    private final EventoRecibidoRepository eventos;
    private final CompraRepository compras;
    private final AnulacionRepository anulaciones;
    private final ControlIdempotencia idempotencia;
    private final LectorEventos lector;
    private final ValidadorAnulaciones validador;

    public ProcesadorAnulaciones(EventoRecibidoRepository eventos, CompraRepository compras,
                                 AnulacionRepository anulaciones, ControlIdempotencia idempotencia,
                                 LectorEventos lector, ValidadorAnulaciones validador) {
        this.eventos = eventos;
        this.compras = compras;
        this.anulaciones = anulaciones;
        this.idempotencia = idempotencia;
        this.lector = lector;
        this.validador = validador;
    }

    @Transactional
    public Anulacion procesar(Long idEvento) {
        EventoRecibido recibido = eventos.findById(idEvento)
                .orElseThrow(() -> new IllegalStateException("No existe el evento " + idEvento + " en la bitácora"));
        EventoAnulacionCompra evento = lector.leerAnulacion(recibido.getContenido());
        validador.validar(evento);
        EventoAnulacionCompra.DatosAnulacion datos = evento.anulacion();
        TipoAnulacion tipo = TipoAnulacion.valueOf(datos.tipo());

        ClaveIdempotencia clave = new ClaveIdempotencia(evento.tipoEvento(), datos.idAnulacion());
        idempotencia.reservar(clave, recibido.getId(), motivo -> new AnulacionDuplicadaException(
                "La anulación " + datos.idAnulacion() + " " + motivo));

        String nombreCompra = datos.idCompra();
        Compra compra = compras.bloquear(datos.idCompra())
                .orElseThrow(() -> rechazo("La compra " + nombreCompra + " no está registrada"));
        exigirCoherenciaConLaCompra(datos, compra, nombreCompra);
        if (tipo == TipoAnulacion.TOTAL) {
            exigirTotal(datos.montoRevertido(), compra, nombreCompra);
        } else {
            exigirDevolucionPosible(datos, compra, nombreCompra);
        }

        Anulacion anulacion = new Anulacion(datos.idAnulacion(), compra, tipo, datos.fecha(),
                datos.montoRevertido(), datos.motivo(), recibido.getId());
        if (tipo == TipoAnulacion.PARCIAL) {
            datos.items().forEach(item -> anulacion.agregarItem(item.categoria(), item.cantidad(), item.monto()));
        }
        compra.revertir(datos.montoRevertido());
        Anulacion guardada = anulaciones.save(anulacion);
        recibido.marcarProcesado();
        return guardada;
    }

    private static void exigirCoherenciaConLaCompra(EventoAnulacionCompra.DatosAnulacion datos, Compra compra,
                                                    String nombreCompra) {
        if (!compra.getIdClienteOrigen().equals(datos.idCliente())) {
            throw rechazo("El cliente " + datos.idCliente() + " no es el de la compra " + nombreCompra
                    + " (" + compra.getIdClienteOrigen() + ")");
        }
        if (datos.fecha().isBefore(compra.getFecha())) {
            throw rechazo("La anulación (" + datos.fecha() + ") no puede ser anterior a la compra ("
                    + compra.getFecha() + ")");
        }
        if (compra.estaAnulada()) {
            throw rechazo("La compra " + nombreCompra + " ya está anulada");
        }
    }

    private static void exigirTotal(BigDecimal monto, Compra compra, String nombreCompra) {
        if (monto.compareTo(compra.getMontoVigente()) != 0) {
            throw rechazo("La anulación total debe revertir " + compra.getMontoVigente().toPlainString()
                    + ", lo que sigue vigente de la compra " + nombreCompra);
        }
    }

    private void exigirDevolucionPosible(EventoAnulacionCompra.DatosAnulacion datos, Compra compra,
                                         String nombreCompra) {
        if (datos.montoRevertido().compareTo(compra.getMontoVigente()) > 0) {
            throw rechazo("La devolución (" + datos.montoRevertido().toPlainString()
                    + ") supera lo que sigue vigente de la compra " + nombreCompra + " ("
                    + compra.getMontoVigente().toPlainString() + ")");
        }
        Map<String, Cantidades> comprado = porCategoria(compra.getItems().stream()
                .map(i -> new Linea(i.getCategoria(), i.getCantidad(), i.getMonto())).toList());
        Map<String, Cantidades> devueltoAntes = new LinkedHashMap<>();
        anulaciones.devueltoPorCategoria(compra.getId()).forEach(d -> devueltoAntes.put(d.getCategoria(),
                new Cantidades(d.getCantidad(), d.getMonto())));
        Map<String, Cantidades> aDevolver = porCategoria(datos.items().stream()
                .map(i -> new Linea(i.categoria(), i.cantidad(), i.monto())).toList());

        aDevolver.forEach((categoria, devolucion) -> {
            Cantidades original = comprado.get(categoria);
            if (original == null) {
                throw rechazo("La compra " + nombreCompra + " no tiene ítems de la categoría '" + categoria + "'");
            }
            Cantidades previo = devueltoAntes.getOrDefault(categoria, Cantidades.CERO);
            long unidadesRestantes = original.unidades() - previo.unidades();
            if (devolucion.unidades() > unidadesRestantes) {
                throw rechazo("Se devuelven " + devolucion.unidades() + " unidades de '" + categoria
                        + "', pero solo quedan " + unidadesRestantes + " de la compra");
            }
            BigDecimal montoRestante = original.monto().subtract(previo.monto());
            if (devolucion.monto().compareTo(montoRestante) > 0) {
                throw rechazo("Se devuelven " + devolucion.monto().toPlainString() + " de '" + categoria
                        + "', pero solo quedan " + montoRestante.toPlainString() + " de la compra");
            }
        });
    }

    private static Map<String, Cantidades> porCategoria(List<Linea> lineas) {
        Map<String, Cantidades> resultado = new LinkedHashMap<>();
        lineas.forEach(l -> resultado.merge(l.categoria(), new Cantidades(l.cantidad(), l.monto()), Cantidades::mas));
        return resultado;
    }

    private static AnulacionInconsistenteException rechazo(String mensaje) {
        return new AnulacionInconsistenteException(mensaje);
    }

    private record Linea(String categoria, long cantidad, BigDecimal monto) {
    }

    private record Cantidades(long unidades, BigDecimal monto) {

        static final Cantidades CERO = new Cantidades(0, BigDecimal.ZERO);

        Cantidades mas(Cantidades otras) {
            return new Cantidades(unidades + otras.unidades, monto.add(otras.monto));
        }
    }
}
