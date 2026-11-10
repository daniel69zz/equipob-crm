package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.reporte.ReporteComparativo.Indicador;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.maxiconecta.crm.comportamiento.reporte.EscrituraCsv.numero;

/**
 * Exporta el reporte comparativo a CSV (SCRUM-499, docs/compra/reporte-comparativo.md). Se escribe a partir
 * del mismo {@link ReporteComparativo} que devuelve el JSON, sin volver a calcular nada, así que conserva los
 * mismos valores (criterio de aceptación 3).
 *
 * <p>El formato es el estándar de los reportes ({@link EscrituraCsv}, SCRUM-479).
 */
@Component
public class ExportacionComparativoCsv {

    static final String BOM = EscrituraCsv.BOM;

    public String exportar(ReporteComparativo reporte) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of("Reporte comparativo de indicadores de clientes"));
        filas.add(List.of("Alcance", reporte.alcance().segmento() == null ? "Toda la cartera"
                : "Segmento " + reporte.alcance().segmento()));
        filas.add(List.of("Periodo A", reporte.periodoA().desde() + " a " + reporte.periodoA().hasta()));
        filas.add(List.of("Periodo B", reporte.periodoB().desde() + " a " + reporte.periodoB().hasta()));
        filas.add(List.of("Zona horaria", reporte.zonaHoraria()));
        filas.add(List.of("Generado", reporte.generadoEn().toString()));
        filas.add(List.of());
        filas.add(List.of("Indicador", "Codigo", "Periodo A", "Periodo B", "Variacion", "Variacion %", "Tendencia"));
        for (Indicador i : reporte.indicadores()) {
            filas.add(List.of(i.nombre(), i.codigo(), numero(i.periodoA()), numero(i.periodoB()),
                    numero(i.variacionAbsoluta()), numero(i.variacionPorcentual()),
                    i.tendencia() == null ? "" : i.tendencia().name()));
        }
        return EscrituraCsv.escribir(filas);
    }
}
