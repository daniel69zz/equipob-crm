package com.maxiconecta.crm.fidelizacion.movimiento;

import com.maxiconecta.crm.fidelizacion.movimiento.BitacoraMovimientos.Estado;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Detalle de puntos por transacción (SCRUM-390), confirmación de redenciones (SCRUM-406), bitácora de
 * movimientos y reproceso. El API Gateway exige PUNTOS_CONSULTAR para los GET y FIDELIZACION_CONFIGURAR
 * para el reproceso.
 */
@RestController
@RequestMapping("/api/fidelizacion")
public class MovimientosController {

    private static final int LIMITE_BITACORA = 200;

    private final RegistroPuntos registro;
    private final BitacoraMovimientos bitacora;
    private final IngestaMovimientos ingesta;
    private final ConfirmacionRedenciones confirmaciones;

    public MovimientosController(RegistroPuntos registro, BitacoraMovimientos bitacora, IngestaMovimientos ingesta,
                                 ConfirmacionRedenciones confirmaciones) {
        this.registro = registro;
        this.bitacora = bitacora;
        this.ingesta = ingesta;
        this.confirmaciones = confirmaciones;
    }

    @GetMapping("/clientes/{clienteId}/movimientos")
    public List<RegistroPuntos.DetalleMovimiento> movimientos(@PathVariable long clienteId) {
        return registro.movimientos(clienteId);
    }

    /** Bitácora filtrable por estado y tipo: {@code ?estado=FALLIDO&tipo=REDENCION} (SCRUM-408). */
    @GetMapping("/eventos")
    public List<BitacoraMovimientos.EventoMovimiento> eventos(@RequestParam(required = false) Estado estado,
                                                              @RequestParam(required = false) TipoMovimiento tipo) {
        return bitacora.listar(estado, tipo, LIMITE_BITACORA);
    }

    /** Confirmación de una redención por su transacción de origen (SCRUM-406). */
    @GetMapping("/redenciones/{idTransaccion}")
    public ConfirmacionRedenciones.Confirmacion redencion(@PathVariable String idTransaccion) {
        return confirmaciones.confirmar(idTransaccion).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No se recibió ninguna redención con la transacción " + idTransaccion));
    }

    @PostMapping("/eventos/{id}/reprocesar")
    public BitacoraMovimientos.EventoMovimiento reprocesar(@PathVariable long id) {
        try {
            return ingesta.reprocesar(id);
        } catch (IngestaMovimientos.EventoNoEncontradoException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IngestaMovimientos.EventoNoReprocesableException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }
}
