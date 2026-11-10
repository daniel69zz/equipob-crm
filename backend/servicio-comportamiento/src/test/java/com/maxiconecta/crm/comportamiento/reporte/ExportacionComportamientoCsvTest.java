package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Alcance;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Categoria;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Frecuencia;
import com.maxiconecta.crm.comportamiento.reporte.ReporteComportamiento.Valor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** SCRUM-483 · Formato del CSV del reporte de comportamiento de compra (SCRUM-58, SCRUM-479). */
class ExportacionComportamientoCsvTest {

    private final ExportacionComportamientoCsv csv = new ExportacionComportamientoCsv();

    @Test
    void escribeElEncabezadoLosIndicadoresYLasCategoriasConBomYCrlf() {
        String texto = csv.exportar(reporte(new Alcance("Alto valor", 2L), false, null,
                new Frecuencia(3, 2, new BigDecimal("1.50")),
                new Valor(new BigDecimal("1180.00"), new BigDecimal("393.33"), new BigDecimal("590.00")),
                List.of(new Categoria("Hogar", 2, 5, new BigDecimal("900.00"), new BigDecimal("76.27"), false),
                        new Categoria("Sin categoría", 1, 1, new BigDecimal("280.00"), new BigDecimal("23.73"), true))));

        assertThat(texto).startsWith(EscrituraCsv.BOM + "Reporte de comportamiento de compra\r\n");
        assertThat(texto).contains("Alcance,Segmento Alto valor (2 clientes)\r\n");
        assertThat(texto).contains("Periodo,2026-09-01 a 2026-09-30\r\n");
        assertThat(texto).contains("Zona horaria,America/La_Paz\r\n");
        assertThat(texto).contains("Indicador,Codigo,Valor\r\n");
        assertThat(texto).contains("Compras,COMPRAS,3\r\n");
        assertThat(texto).contains("Clientes que compraron,CLIENTES_CON_COMPRAS,2\r\n");
        assertThat(texto).contains("Compras por cliente,COMPRAS_POR_CLIENTE,1.50\r\n");
        assertThat(texto).contains("Valor total (Bs),VALOR_TOTAL,1180.00\r\n");
        assertThat(texto).contains("Ticket promedio (Bs),TICKET_PROMEDIO,393.33\r\n");
        assertThat(texto).contains("Valor por cliente (Bs),VALOR_POR_CLIENTE,590.00\r\n");
        assertThat(texto).contains("Categoria,Compras,Unidades,Monto (Bs),Porcentaje del monto,Sin categoria\r\n");
        assertThat(texto).contains("Hogar,2,5,900.00,76.27,NO\r\n");
        assertThat(texto).contains("Sin categoría,1,1,280.00,23.73,SI\r\n");
        assertThat(texto).doesNotContain("Sin datos");
    }

    @Test
    void unReporteSinDatosLoDiceYDejaVaciosLosPromediosEnLugarDeCeros() {
        String texto = csv.exportar(reporte(new Alcance(null, null), true, "No hay compras vigentes en el periodo.",
                new Frecuencia(0, 0, null), new Valor(new BigDecimal("0.00"), null, null), List.of()));

        assertThat(texto).contains("Alcance,Toda la cartera\r\n");
        assertThat(texto).contains("Sin datos,No hay compras vigentes en el periodo.\r\n");
        assertThat(texto).contains("Compras por cliente,COMPRAS_POR_CLIENTE,\r\n");
        assertThat(texto).contains("Ticket promedio (Bs),TICKET_PROMEDIO,\r\n");
        assertThat(texto).contains("Valor total (Bs),VALOR_TOTAL,0.00\r\n");
    }

    @Test
    void entrecomillaUnaCategoriaConComas() {
        String texto = csv.exportar(reporte(new Alcance(null, null), false, null, new Frecuencia(1, 1, BigDecimal.ONE),
                new Valor(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
                List.of(new Categoria("Ropa, calzado", 1, 1, BigDecimal.TEN, new BigDecimal("100.00"), false))));

        assertThat(texto).contains("\"Ropa, calzado\",1,1,10,100.00,NO\r\n");
    }

    @Test
    void elSufijoDelArchivoQuitaTildesEspaciosYSimbolos() {
        assertThat(ExportacionReportes.sufijo(null)).isEmpty();
        assertThat(ExportacionReportes.sufijo("Alto valor")).isEqualTo("_alto-valor");
        assertThat(ExportacionReportes.sufijo("  Clientes Ñandú / 2026 ")).isEqualTo("_clientes-nandu-2026");
        assertThat(ExportacionReportes.sufijo("¿?")).isEmpty();
    }

    private static ReporteComportamiento reporte(Alcance alcance, boolean sinDatos, String mensaje, Frecuencia frecuencia,
                                                 Valor valor, List<Categoria> categorias) {
        return new ReporteComportamiento(OffsetDateTime.parse("2026-11-10T09:15:00-04:00"), LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30), "America/La_Paz", alcance, sinDatos, mensaje, frecuencia, valor, categorias);
    }
}
