package com.maxiconecta.crm.segmentacion.reporte;

import com.maxiconecta.crm.segmentacion.reporte.DistribucionRepository.Conteo;
import com.maxiconecta.crm.segmentacion.reporte.NivelesFidelizacion.ConteoNiveles;
import com.maxiconecta.crm.segmentacion.reporte.ReporteDistribucion.Grupo;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Arma el reporte de distribución de clientes (SCRUM-57): cuenta los clientes por segmento y por
 * nivel de consumo con lo que guarda la segmentación (SCRUM-463 y SCRUM-460), le pide a
 * servicio-fidelizacion el corte por nivel de fidelización (SCRUM-464) y deja la generación en la
 * auditoría antes de responder.
 *
 * <p>Los tres cortes se calculan sobre la misma población, así que sus cantidades suman
 * {@code total} y sus porcentajes 100 % (salvo el redondeo a dos decimales).
 */
@Service
public class ServicioReporteDistribucion {

    /** Niveles del programa de fidelización, del más bajo al más alto (SCRUM-49). */
    static final List<String> NIVELES = List.of("BRONCE", "PLATA", "ORO");

    private final DistribucionRepository distribucion;
    private final NivelesFidelizacion fidelizacion;
    private final AuditoriaReportes auditoria;
    private final Clock reloj;

    public ServicioReporteDistribucion(DistribucionRepository distribucion, NivelesFidelizacion fidelizacion,
                                       AuditoriaReportes auditoria, Clock reloj) {
        this.distribucion = distribucion;
        this.fidelizacion = fidelizacion;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public ReporteDistribucion generar(FiltroReporte filtro, String usuario) {
        OffsetDateTime generadoEn = OffsetDateTime.now(reloj);
        long total = distribucion.total(filtro);
        if (total == 0) {
            // El filtro no deja ningún cliente: se responde "sin datos", no un error.
            auditoria.registrar(usuario, filtro, 0, true);
            return ReporteDistribucion.sinDatos(generadoEn, filtro);
        }
        List<Grupo> segmentos = grupos(distribucion.porSegmento(filtro), total);
        List<Grupo> nivelesConsumo = grupos(distribucion.porNivelConsumo(filtro), total);
        ConteoNiveles niveles = filtro.cubreTodos() ? fidelizacion.todos()
                : fidelizacion.deClientes(distribucion.clientes(filtro));
        List<Grupo> nivelesFidelizacion = niveles.disponibles()
                ? gruposDeNiveles(niveles.porNivel(), total) : List.of();
        auditoria.registrar(usuario, filtro, total, niveles.disponibles());
        return new ReporteDistribucion(generadoEn, ReporteDistribucion.Filtro.de(filtro), total, false, segmentos,
                nivelesConsumo, nivelesFidelizacion, niveles.disponibles());
    }

    private static List<Grupo> grupos(List<Conteo> conteos, long total) {
        return conteos.stream().map(conteo -> Grupo.de(conteo.valor(), conteo.clientes(), total)).toList();
    }

    /**
     * Los tres niveles en orden y, al final, el grupo sin nivel: los clientes de la población que
     * fidelización no cuenta en ningún nivel, porque no tienen puntos o no alcanzan el umbral más
     * bajo. Así el corte cubre a toda la población igual que los otros dos.
     */
    private static List<Grupo> gruposDeNiveles(Map<String, Long> porNivel, long total) {
        List<Grupo> grupos = new ArrayList<>();
        long conNivel = 0;
        for (String nivel : NIVELES) {
            long clientes = porNivel.getOrDefault(nivel, 0L);
            conNivel += clientes;
            grupos.add(Grupo.de(nivel, clientes, total));
        }
        // Un nivel que ya no esté en el catálogo se muestra igual, después de los conocidos.
        for (Map.Entry<String, Long> otro : porNivel.entrySet()) {
            if (!NIVELES.contains(otro.getKey())) {
                conNivel += otro.getValue();
                grupos.add(Grupo.de(otro.getKey(), otro.getValue(), total));
            }
        }
        grupos.add(Grupo.de(null, Math.max(total - conNivel, 0), total));
        return List.copyOf(grupos);
    }
}
