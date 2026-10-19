package com.maxiconecta.crm.comportamiento.sugerencia;

import com.maxiconecta.crm.comportamiento.preferencia.IdentificadoresPreferencias;
import com.maxiconecta.crm.comportamiento.preferencia.PerfilPreferencias;
import com.maxiconecta.crm.comportamiento.preferencia.PerfilPreferencias.CategoriaPreferida;
import com.maxiconecta.crm.comportamiento.preferencia.PerfilPreferenciasRepository;
import com.maxiconecta.crm.comportamiento.sugerencia.AgregadoSegmento.ConsumoSegmento;
import com.maxiconecta.crm.comportamiento.sugerencia.SugerenciasCliente.Motivo;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Sugerencia de categorías afines de un cliente (SCRUM-44, docs/preferencias/sugerencia-categorias.md).
 * Los servicios de Perfil y Segmentación se consultan antes de abrir la transacción; el perfil del
 * cliente y los de sus compañeros se leen en la misma instantánea. Consultar no modifica nada.
 */
@Service
@EnableConfigurationProperties(ReglaSugerencias.class)
public class SugerenciasCategorias {
    private final IdentificadoresPreferencias identificadores;
    private final MiembrosSegmento segmentos;
    private final PerfilPreferenciasRepository perfiles;
    private final AgregadoSegmento agregado;
    private final ReglaSugerencias regla;
    private final Clock reloj;
    private final TransactionTemplate lectura;

    public SugerenciasCategorias(IdentificadoresPreferencias identificadores, MiembrosSegmento segmentos,
                                 PerfilPreferenciasRepository perfiles, AgregadoSegmento agregado,
                                 ReglaSugerencias regla, Clock reloj, PlatformTransactionManager manager) {
        this.identificadores = identificadores;
        this.segmentos = segmentos;
        this.perfiles = perfiles;
        this.agregado = agregado;
        this.regla = regla;
        this.reloj = reloj;
        lectura = new TransactionTemplate(manager);
        lectura.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        lectura.setReadOnly(true);
    }

    public SugerenciasCliente sugerir(long clienteId) {
        // Valida que el cliente exista y no esté consolidado (400, 404, 409 o 503), como el perfil.
        identificadores.obtener(clienteId);
        MiembrosSegmento.Segmento segmento = segmentos.obtener(clienteId);
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        return lectura.execute(estado -> {
            PerfilPreferencias perfil = perfiles.obtener(clienteId);
            if (!perfil.construido()) { return sinSugerencias(clienteId, segmento, ahora, 0, Motivo.SIN_PERFIL); }
            if (perfil.categorias().isEmpty()) {
                return sinSugerencias(clienteId, segmento, ahora, 0, Motivo.SIN_PREFERENCIAS);
            }
            if (!segmento.asignado()) { return sinSugerencias(clienteId, segmento, ahora, 0, Motivo.SIN_SEGMENTO); }
            ConsumoSegmento consumo = agregado.consumir(segmento.companeros());
            if (consumo.clientesConPreferencias() == 0) {
                return sinSugerencias(clienteId, segmento, ahora, 0, Motivo.SIN_DATOS_SEGMENTO);
            }
            var sugerencias = MotorSugerencias.sugerir(
                    perfil.categorias().stream().map(CategoriaPreferida::categoria).toList(), consumo, regla);
            if (sugerencias.isEmpty()) {
                return sinSugerencias(clienteId, segmento, ahora, consumo.clientesConPreferencias(),
                        Motivo.SIN_CATEGORIAS_NUEVAS);
            }
            return new SugerenciasCliente(clienteId, segmento.nombre(), ahora, MotorSugerencias.CRITERIO,
                    consumo.clientesConPreferencias(), sugerencias, false, null, null);
        });
    }

    private static SugerenciasCliente sinSugerencias(long clienteId, MiembrosSegmento.Segmento segmento,
                                                     OffsetDateTime ahora, int clientes, Motivo motivo) {
        return new SugerenciasCliente(clienteId, segmento.nombre(), ahora, MotorSugerencias.CRITERIO, clientes,
                List.of(), true, motivo, motivo.mensaje());
    }
}
