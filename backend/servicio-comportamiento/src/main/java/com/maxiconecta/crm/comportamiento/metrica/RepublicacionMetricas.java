package com.maxiconecta.crm.comportamiento.metrica;

import com.maxiconecta.crm.comportamiento.compra.CompraRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Vuelve a publicar las métricas vigentes de todos los clientes con compras, para que Segmentación
 * los reevalúe sin esperar a su próxima compra o anulación. Sirve para clientes cuyas compras se
 * cargaron sin pasar por los eventos (database/semillas) o cuyas métricas quedaron desactualizadas.
 * <p>
 * Cada foto lleva un {@code idEvento} nuevo, que es la clave de idempotencia en Segmentación, y el
 * tipo {@link #TIPO}. El perfil de preferencias no se actualiza con estas fotos
 * ({@code ReceptorPreferencias}); se construye con {@code POST .../preferencias/construir}.
 */
@Service
public class RepublicacionMetricas {

    public static final String TIPO = "RECALCULO_METRICAS";

    private static final Logger log = LoggerFactory.getLogger(RepublicacionMetricas.class);

    private final CompraRepository compras;
    private final CalculoMetricasCliente calculo;
    private final PublicadorMetricas publicador;

    public RepublicacionMetricas(CompraRepository compras, CalculoMetricasCliente calculo,
                                 PublicadorMetricas publicador) {
        this.compras = compras;
        this.calculo = calculo;
        this.publicador = publicador;
    }

    @Transactional(readOnly = true)
    public Resultado republicarTodos() {
        List<Long> clientes = compras.clientesConCompras();
        for (Long idCliente : clientes) {
            publicador.publicar(calculo.calcular(UUID.randomUUID().toString(), TIPO, idCliente));
        }
        log.info("Métricas republicadas para {} clientes", clientes.size());
        return new Resultado(clientes.size());
    }

    public record Resultado(int clientes) {
    }
}
