package com.maxiconecta.crm.comportamiento.consumo;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.Identificador;
import com.maxiconecta.crm.comportamiento.compra.ValorCliente;
import com.maxiconecta.crm.comportamiento.indicador.ValorAcumulado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Asigna y mantiene el nivel de consumo de los clientes (SCRUM-27, docs/compra/nivel-consumo.md).
 * Reutiliza el valor acumulado (SCRUM-271): no define un cálculo propio, solo lo clasifica con
 * {@link CriterioNivelConsumo}.
 * <p>
 * {@link #reclasificar} corrige a un cliente al momento en que llega una compra o se anula o
 * devuelve una (criterio de aceptación 2); {@link #clasificarTodos()} recalcula a todos de forma
 * programada (SCRUM-268, {@link ClasificacionConsumoScheduler}). Un cliente sin compras vigentes
 * queda sin nivel: se quita su fila, no se trata como error (criterio de aceptación 3).
 */
@Service
public class ClasificadorConsumo {

    private final AgregadoComprasCliente agregado;
    private final ClienteNivelConsumoRepository niveles;
    private final CriterioNivelConsumo criterio;

    public ClasificadorConsumo(AgregadoComprasCliente agregado, ClienteNivelConsumoRepository niveles,
                               CriterioNivelConsumo criterio) {
        this.agregado = agregado;
        this.niveles = niveles;
        this.criterio = criterio;
    }

    /** Vuelve a clasificar a un solo cliente con lo que tiene vigente en este momento. */
    @Transactional
    public Optional<NivelConsumo> reclasificar(String idClienteOrigen) {
        BigDecimal valor = ValorAcumulado.de(agregado.resumir(List.of(new Identificador(idClienteOrigen)))).valor();
        return aplicar(idClienteOrigen, valor);
    }

    /**
     * Recalcula el nivel de todos los clientes con compras vigentes y quita a quienes ya no tienen.
     * Devuelve cuántos clientes quedaron con nivel.
     */
    @Transactional
    public int clasificarTodos() {
        Set<String> conNivel = new HashSet<>();
        for (ValorCliente cliente : agregado.valorVigentePorCliente()) {
            if (aplicar(cliente.idClienteOrigen(), cliente.valor()).isPresent()) {
                conNivel.add(cliente.idClienteOrigen());
            }
        }
        for (ClienteNivelConsumo registrado : niveles.findAll()) {
            if (!conNivel.contains(registrado.getIdClienteOrigen())) {
                niveles.delete(registrado);
            }
        }
        return conNivel.size();
    }

    private Optional<NivelConsumo> aplicar(String idClienteOrigen, BigDecimal valor) {
        Optional<NivelConsumo> nivel = criterio.clasificar(valor);
        if (nivel.isPresent()) {
            niveles.findById(idClienteOrigen).ifPresentOrElse(
                    existente -> existente.actualizar(nivel.get(), valor),
                    () -> niveles.save(new ClienteNivelConsumo(idClienteOrigen, nivel.get(), valor)));
        } else {
            niveles.deleteById(idClienteOrigen);
        }
        return nivel;
    }
}
