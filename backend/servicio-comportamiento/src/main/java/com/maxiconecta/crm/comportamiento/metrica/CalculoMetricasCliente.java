package com.maxiconecta.crm.comportamiento.metrica;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias;
import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ResumenCompras;
import com.maxiconecta.crm.comportamiento.frecuencia.ClasificacionFrecuencia;
import com.maxiconecta.crm.comportamiento.frecuencia.CriterioClasificacionFrecuencia;
import com.maxiconecta.crm.comportamiento.indicador.CategoriaConsumida;
import com.maxiconecta.crm.comportamiento.indicador.RankingCategorias;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Calcula las métricas que usa la segmentación (SCRUM-262) con los mismos criterios que los
 * indicadores de la ficha: frecuencia y valor acumulado vigentes (SCRUM-17/33) y las categorías con
 * consumo vigente, de mayor a menor monto (SCRUM-18), con la clasificación por frecuencia (SCRUM-38). Suma todos los identificadores vinculados
 * al perfil del cliente. Se llama dentro de la transacción de la compra o la anulación, por eso ya
 * ve el cambio recién registrado.
 */
@Service
public class CalculoMetricasCliente {

    private final CompraRepository compras;
    private final AgregadoComprasCliente agregado;
    private final AgregadoConsumoCategorias consumo;
    private final Clock reloj;
    private final CriterioClasificacionFrecuencia clasificacion;

    public CalculoMetricasCliente(CompraRepository compras, AgregadoComprasCliente agregado,
                                  AgregadoConsumoCategorias consumo, Clock reloj,
                                  CriterioClasificacionFrecuencia clasificacion) {
        this.compras = compras;
        this.agregado = agregado;
        this.consumo = consumo;
        this.reloj = reloj;
        this.clasificacion = clasificacion;
    }

    public MetricasCliente calcular(String idEvento, String tipoEventoOrigen, long idCliente) {
        List<String> identificadoresOrigen = compras.identificadoresDe(idCliente);
        List<Identificador> identificadores = identificadoresOrigen.stream().map(Identificador::new).toList();
        ResumenCompras resumen = agregado.resumir(identificadores);
        AgregadoConsumoCategorias.ConsumoEnRango historial = consumo.consumirTodo(identificadores);
        List<String> categorias = RankingCategorias.calcular(historial.compradas(), historial.devueltas()).stream()
                .filter(categoria -> !categoria.sinCategoria())
                .map(CategoriaConsumida::categoria)
                .toList();
        String clasificacionFrecuencia = clasificacion.clasificar(resumen.compras())
                .map(ClasificacionFrecuencia::name).orElse(null);
        return new MetricasCliente(idEvento, tipoEventoOrigen, idCliente, identificadoresOrigen, resumen.compras(),
                resumen.monto(), categorias, OffsetDateTime.now(reloj), clasificacionFrecuencia);
    }
}
