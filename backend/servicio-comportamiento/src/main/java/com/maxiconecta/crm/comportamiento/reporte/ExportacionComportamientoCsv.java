package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Alcance;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Categoria;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.maxiconecta.crm.comportamiento.reporte.EscrituraCsv.numero;

/**
 * Exporta el reporte de comportamiento de compra (SCRUM-58) a CSV (SCRUM-60, SCRUM-483,
 * docs/compra/exportacion-reportes.md). Se escribe a partir del mismo {@link ReporteComportamiento} que devuelve
 * el JSON de la pantalla, sin volver a calcular nada, así que el archivo tiene los mismos datos (criterio 1).
 *
 * <p>Los códigos de los indicadores son los del reporte comparativo (SCRUM-498), para poder cruzar las dos
 * exportaciones.
 */
@Component
public class ExportacionComportamientoCsv {

    public String exportar(ReporteComportamiento reporte) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of("Reporte de comportamiento de compra"));
        filas.add(List.of("Alcance", alcance(reporte.alcance())));
        filas.add(List.of("Periodo", reporte.desde() + " a " + reporte.hasta()));
        filas.add(List.of("Zona horaria", reporte.zonaHoraria()));
        filas.add(List.of("Generado", reporte.generadoEn().toString()));
        if (reporte.sinDatos()) {
            filas.add(List.of("Sin datos", reporte.mensaje() == null ? "" : reporte.mensaje()));
        }
        filas.add(List.of());
        filas.add(List.of("Indicador", "Codigo", "Valor"));
        for (IndicadorExportado i : indicadores(reporte)) {
            filas.add(List.of(i.nombre(), i.codigo(), numero(i.valor())));
        }
        filas.add(List.of());
        filas.add(List.of("Categoria", "Compras", "Unidades", "Monto (Bs)", "Porcentaje del monto", "Sin categoria"));
        for (Categoria c : reporte.categorias()) {
            filas.add(List.of(c.categoria(), String.valueOf(c.compras()), String.valueOf(c.unidades()),
                    numero(c.monto()), numero(c.porcentaje()), c.sinCategoria() ? "SI" : "NO"));
        }
        return EscrituraCsv.escribir(filas);
    }

    /** Cómo se describe el grupo del reporte en el encabezado de los dos formatos. */
    static String alcance(Alcance alcance) {
        if (alcance.segmento() == null) {
            return "Toda la cartera";
        }
        return "Segmento " + alcance.segmento() + " (" + alcance.clientesDelSegmento() + " clientes)";
    }

    /** Los indicadores del reporte en el orden en que se exportan; los conteos van sin decimales. */
    static List<IndicadorExportado> indicadores(ReporteComportamiento reporte) {
        return List.of(
                new IndicadorExportado("COMPRAS", "Compras", BigDecimal.valueOf(reporte.frecuencia().compras()), true),
                new IndicadorExportado("CLIENTES_CON_COMPRAS", "Clientes que compraron",
                        BigDecimal.valueOf(reporte.frecuencia().clientesConCompras()), true),
                new IndicadorExportado("COMPRAS_POR_CLIENTE", "Compras por cliente",
                        reporte.frecuencia().comprasPorCliente(), false),
                new IndicadorExportado("VALOR_TOTAL", "Valor total (Bs)", reporte.valor().total(), false),
                new IndicadorExportado("TICKET_PROMEDIO", "Ticket promedio (Bs)", reporte.valor().ticketPromedio(), false),
                new IndicadorExportado("VALOR_POR_CLIENTE", "Valor por cliente (Bs)",
                        reporte.valor().valorPorCliente(), false));
    }

    record IndicadorExportado(String codigo, String nombre, BigDecimal valor, boolean conteo) {
    }
}
