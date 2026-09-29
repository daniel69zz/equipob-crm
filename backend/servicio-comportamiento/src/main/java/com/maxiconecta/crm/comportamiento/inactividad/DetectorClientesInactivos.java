package com.maxiconecta.crm.comportamiento.inactividad;

import com.maxiconecta.crm.comportamiento.compra.AgregadoComprasCliente;
import com.maxiconecta.crm.comportamiento.compra.Origen;
import com.maxiconecta.crm.comportamiento.compra.UltimaCompraCliente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Detecta y persiste los clientes inactivos (SCRUM-294, SCRUM-296): sin compras vigentes desde
 * hace más del umbral definido en {@link CriterioInactividad}. {@link #detectar()} se ejecuta
 * periódicamente (SCRUM-297, {@link ClientesInactivosScheduler}); {@link #reactivar} corrige de
 * inmediato a un cliente que vuelve a comprar, sin esperar a la próxima ejecución (criterio de
 * aceptación 2 de SCRUM-31).
 */
@Service
public class DetectorClientesInactivos {

    private final AgregadoComprasCliente agregado;
    private final ClienteInactivoRepository inactivos;
    private final CriterioInactividad criterio;

    public DetectorClientesInactivos(AgregadoComprasCliente agregado, ClienteInactivoRepository inactivos,
                                     CriterioInactividad criterio) {
        this.agregado = agregado;
        this.inactivos = inactivos;
        this.criterio = criterio;
    }

    /**
     * Recalcula el listado completo: agrega los clientes recién inactivos, actualiza la última
     * compra de los que ya estaban y quita a quienes volvieron a comprar dentro del umbral (por si
     * la reactivación puntual de {@link #reactivar} no llegó a aplicarse). Devuelve cuántos
     * clientes quedaron inactivos tras la ejecución.
     */
    @Transactional
    public int detectar() {
        List<UltimaCompraCliente> candidatos = agregado.ultimaCompraVigentePorClienteAnteriorA(criterio.fechaCorte());
        Set<String> vigentes = new HashSet<>();
        for (UltimaCompraCliente candidato : candidatos) {
            vigentes.add(clave(candidato.origen(), candidato.idClienteOrigen()));
            inactivos.findByOrigenAndIdClienteOrigen(candidato.origen(), candidato.idClienteOrigen())
                    .ifPresentOrElse(existente -> existente.actualizar(candidato.ultimaCompra()),
                            () -> inactivos.save(new ClienteInactivo(candidato.origen(), candidato.idClienteOrigen(),
                                    candidato.ultimaCompra())));
        }
        for (ClienteInactivo registrado : inactivos.findAll()) {
            if (!vigentes.contains(clave(registrado.getOrigen(), registrado.getIdClienteOrigen()))) {
                inactivos.delete(registrado);
            }
        }
        return candidatos.size();
    }

    /** Quita a un cliente del listado en cuanto se procesa una compra suya. */
    @Transactional
    public void reactivar(Origen origen, String idClienteOrigen) {
        inactivos.deleteByOrigenAndIdClienteOrigen(origen, idClienteOrigen);
    }

    private static String clave(Origen origen, String idClienteOrigen) {
        return origen + ":" + idClienteOrigen;
    }
}
