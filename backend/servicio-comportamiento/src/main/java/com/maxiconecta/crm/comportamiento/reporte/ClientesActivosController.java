package com.maxiconecta.crm.comportamiento.reporte;

import com.maxiconecta.crm.comportamiento.inactividad.CriterioInactividad;
import com.maxiconecta.crm.comportamiento.periodo.AgregadoConsumoPeriodo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Clientes activos de la cartera para el panel de indicadores (SCRUM-475, SCRUM-59,
 * docs/segmentacion/panel-indicadores.md): los que tienen al menos una compra vigente vinculada en los
 * últimos {@code umbralDias} días. Es el complemento exacto de la lista de clientes inactivos (SCRUM-295):
 * usa el mismo {@link CriterioInactividad}, así que un cliente es activo aquí si y solo si no figura como
 * inactivo allí.
 *
 * <p>Es una consulta interna que hace servicio-segmentacion: el Gateway la deniega a los usuarios, y por
 * eso no lleva el control de {@link PermisoReportes}.
 */
@RestController
@RequestMapping("/api/comportamiento/reportes")
public class ClientesActivosController {

    private final AgregadoConsumoPeriodo agregado;
    private final CriterioInactividad criterio;
    private final Clock reloj;

    public ClientesActivosController(AgregadoConsumoPeriodo agregado, CriterioInactividad criterio, Clock reloj) {
        this.agregado = agregado;
        this.criterio = criterio;
        this.reloj = reloj;
    }

    @GetMapping("/clientes-activos")
    public ClientesActivos clientesActivos() {
        OffsetDateTime desde = criterio.fechaCorte();
        long activos = agregado.clientesConComprasVinculadas(desde, OffsetDateTime.now(reloj));
        return new ClientesActivos(activos, criterio.umbralDias(), desde);
    }

    /** {@code desde}: inicio de la ventana; una compra vigente posterior hace activo al cliente. */
    public record ClientesActivos(long activos, int umbralDias, OffsetDateTime desde) {
    }
}
