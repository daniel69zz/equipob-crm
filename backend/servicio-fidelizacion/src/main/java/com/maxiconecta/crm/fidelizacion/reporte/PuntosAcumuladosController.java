package com.maxiconecta.crm.fidelizacion.reporte;

import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository;
import com.maxiconecta.crm.fidelizacion.puntos.SaldoPuntosRepository.TotalPuntos;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puntos acumulados de la cartera para el panel de indicadores (SCRUM-476, SCRUM-59,
 * docs/segmentacion/panel-indicadores.md): la suma de los saldos vigentes y cuántos clientes tienen saldo.
 * Es una consulta interna que hace servicio-segmentacion, como la distribución de niveles (SCRUM-464):
 * el Gateway deniega {@code /api/fidelizacion/reportes/**} a los usuarios.
 */
@RestController
@RequestMapping("/api/fidelizacion/reportes")
public class PuntosAcumuladosController {

    private final SaldoPuntosRepository saldos;

    public PuntosAcumuladosController(SaldoPuntosRepository saldos) {
        this.saldos = saldos;
    }

    @GetMapping("/puntos")
    public PuntosAcumulados puntos() {
        TotalPuntos total = saldos.totalPuntos();
        return new PuntosAcumulados(total.getPuntos(), total.getClientes());
    }

    /** {@code puntos}: suma de los saldos vigentes; {@code clientesConPuntos}: clientes con saldo mayor que cero. */
    public record PuntosAcumulados(long puntos, long clientesConPuntos) {
    }
}
