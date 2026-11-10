package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.comparacion.ClientesDelSegmento;
import com.maxiconecta.crm.comportamiento.comun.ReglaNegocioException;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import com.maxiconecta.crm.comportamiento.indicador.RankingCategorias;
import com.maxiconecta.crm.comportamiento.indicador.TicketPromedio;
import com.maxiconecta.crm.comportamiento.indicador.ValorAcumulado;
import com.maxiconecta.crm.comportamiento.periodo.AgregadoConsumoPeriodo;
import com.maxiconecta.crm.comportamiento.periodo.RangoPeriodo;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Alcance;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Categoria;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Frecuencia;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Valor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Arma el reporte de frecuencia, valor y categorías de compra de un segmento, o de la cartera
 * completa, en un periodo (SCRUM-58, docs/compra/reportes-comportamiento.md). Se calcula al
 * consultarlo, así que refleja de inmediato cualquier compra, devolución o anulación nueva.
 *
 * <p>No tiene cálculos propios: reutiliza los agregados del valor por periodo (SCRUM-39, SCRUM-40) y
 * del consumo por categoría (SCRUM-32), y los mismos {@link ValorAcumulado}, {@link TicketPromedio} y
 * {@link RankingCategorias} que los indicadores del cliente. Por eso sus resultados coinciden con
 * ellos (criterio de aceptación 3, SCRUM-470).
 */
@Service
public class ConsultaReporteComportamiento {

    static final int LARGO_MAXIMO_SEGMENTO = 60;

    private final AgregadoConsumoPeriodo periodo;
    private final AgregadoConsumoCategorias categorias;
    private final ClientesDelSegmento clientesDelSegmento;
    private final Clock reloj;
    private final ZoneId zona;

    public ConsultaReporteComportamiento(AgregadoConsumoPeriodo periodo, AgregadoConsumoCategorias categorias,
                                         ClientesDelSegmento clientesDelSegmento, Clock reloj,
                                         @Value("${comportamiento.consumo-periodo.zona-horaria:America/La_Paz}") ZoneId zona) {
        this.periodo = periodo;
        this.categorias = categorias;
        this.clientesDelSegmento = clientesDelSegmento;
        this.reloj = reloj;
        this.zona = zona;
    }

    /**
     * @param rango    periodo validado: completo, en orden y de hasta cinco años (SCRUM-469)
     * @param segmento segmento a reportar; nulo o vacío es la cartera completa
     */
    public ReporteComportamiento generar(RangoPeriodo rango, String segmento) {
        OffsetDateTime inicio = rango.inicio(zona);
        OffsetDateTime fin = rango.fin(zona);
        String codigo = segmento == null ? "" : segmento.strip();
        if (codigo.length() > LARGO_MAXIMO_SEGMENTO) {
            throw new ReglaNegocioException("El segmento no puede tener más de " + LARGO_MAXIMO_SEGMENTO + " caracteres");
        }
        if (codigo.isEmpty()) {
            return armar(rango, Alcance.cartera(), periodo.resumirVinculadas(inicio, fin),
                    periodo.clientesConComprasVinculadas(inicio, fin), categorias.consumirVinculadas(inicio, fin),
                    "No hay compras vigentes en el periodo elegido.");
        }
        List<Long> clientes = clientesDelSegmento.idsDe(codigo).stream().distinct().toList();
        Alcance alcance = Alcance.segmento(codigo, clientes.size());
        if (clientes.isEmpty()) {
            return vacio(rango, alcance, "El segmento '" + codigo + "' no tiene clientes asignados.");
        }
        return armar(rango, alcance, periodo.resumirPerfiles(clientes, inicio, fin),
                periodo.clientesConCompras(clientes, inicio, fin), categorias.consumirPerfiles(clientes, inicio, fin),
                "Los clientes del segmento '" + codigo + "' no tienen compras vigentes en el periodo elegido.");
    }

    private ReporteComportamiento armar(RangoPeriodo rango, Alcance alcance, ResumenCompras resumen,
                                        long clientesConCompras, ConsumoEnRango consumo, String mensajeSinCompras) {
        if (resumen.compras() == 0) {
            return vacio(rango, alcance, mensajeSinCompras);
        }
        List<Categoria> ranking = Categoria.de(RankingCategorias.calcular(consumo.compradas(), consumo.devueltas()));
        return new ReporteComportamiento(OffsetDateTime.now(reloj), rango.desde(), rango.hasta(), zona.getId(), alcance,
                false, null, Frecuencia.de(resumen.compras(), clientesConCompras),
                Valor.de(ValorAcumulado.de(resumen), TicketPromedio.de(resumen), clientesConCompras), ranking);
    }

    private ReporteComportamiento vacio(RangoPeriodo rango, Alcance alcance, String mensaje) {
        ResumenCompras sinCompras = ResumenCompras.VACIO;
        return new ReporteComportamiento(OffsetDateTime.now(reloj), rango.desde(), rango.hasta(), zona.getId(), alcance,
                true, mensaje, Frecuencia.de(0, 0), Valor.de(ValorAcumulado.de(sinCompras), TicketPromedio.de(sinCompras), 0),
                List.of());
    }
}
