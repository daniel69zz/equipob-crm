package com.maxiconecta.crm.comportamiento.evolucion;

import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias;
import com.maxiconecta.crm.comportamiento.compra.AgregadoConsumoCategorias.ConsumoEnRango;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Evolución del consumo de un cliente por categoría, calculada al consultarla sobre su historial
 * de compras (docs/compra/evolucion-consumo-categoria.md): agrega en la base lo comprado y lo
 * devuelto del rango, lo depura, lo agrupa por periodo y compara cada periodo.
 */
@Service
public class ConsultaEvolucionConsumo {

    private final AgregadoConsumoCategorias agregado;
    private final Clock reloj;
    private final ZoneId zona;

    public ConsultaEvolucionConsumo(AgregadoConsumoCategorias agregado, Clock reloj,
                                    @Value("${comportamiento.evolucion.zona-horaria:America/La_Paz}") ZoneId zona) {
        this.agregado = agregado;
        this.reloj = reloj;
        this.zona = zona;
    }

    /** Fecha actual en la zona horaria del negocio, para completar el rango por defecto. */
    public LocalDate hoy() {
        return LocalDate.now(reloj.withZone(zona));
    }

    public EvolucionConsumo consultar(List<Identificador> identificadores, FiltroEvolucionConsumo filtro) {
        ConsumoEnRango consumo = agregado.consumir(identificadores.stream().distinct().toList(),
                filtro.desde().atStartOfDay(zona).toOffsetDateTime(),
                filtro.hasta().plusDays(1).atStartOfDay(zona).toOffsetDateTime());
        DepuracionConsumo.Resultado depurado = DepuracionConsumo.depurar(consumo);
        CalculoEvolucionConsumo.Resultado calculado = CalculoEvolucionConsumo.calcular(filtro.periodos(), zona,
                depurado.consumo(), filtro.categorias());

        int indiceBase = filtro.indiceBase();
        List<SerieComparada> categorias = calculado.series().stream()
                .map(serie -> ComparacionPeriodos.comparar(serie, indiceBase, filtro.periodos()))
                .toList();
        boolean sinDatos = calculado.series().stream().allMatch(serie -> serie.compras() == 0);
        return new EvolucionConsumo(filtro.desde(), filtro.hasta(), filtro.periodo(), zona.getId(),
                filtro.periodoBase(), filtro.periodos(), calculado.categoriasDisponibles(), categorias,
                calculado.totales(), depurado.calidad(), sinDatos);
    }
}
