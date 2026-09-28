package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.CambioPerfil;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilDetalle;
import com.maxiconecta.crm.perfil.cliente.CambioPerfilRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.cliente.TipoCambio;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Consulta del histórico de cambios de un cliente, del cambio más reciente al más antiguo
 * (docs/perfil/historico-cambios.md).
 */
@Service
public class ConsultaHistorial {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final CambioPerfilRepository cambios;
    private final ClienteRepository clientes;

    public ConsultaHistorial(CambioPerfilRepository cambios, ClienteRepository clientes) {
        this.cambios = cambios;
        this.clientes = clientes;
    }

    @Transactional(readOnly = true)
    public PaginaHistorial buscar(Long idCliente, Filtro filtro, int pagina, int tamanio) {
        if (!clientes.existsById(idCliente)) {
            throw new RecursoNoEncontradoException("No existe el cliente " + idCliente);
        }
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA),
                Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id")));
        Page<CambioPerfil> resultado = cambios.findAll(especificacion(idCliente, filtro, ZoneId.systemDefault()), solicitud);
        return new PaginaHistorial(resultado.getContent().stream().map(c -> CambioResponse.de(c, filtro.campo())).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements());
    }

    private static Specification<CambioPerfil> especificacion(Long idCliente, Filtro filtro, ZoneId zona) {
        return (cambio, consulta, criterios) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(criterios.equal(cambio.get("idCliente"), idCliente));
            if (filtro.origen() != null) {
                condiciones.add(criterios.equal(cambio.get("origen"), filtro.origen()));
            }
            if (filtro.desde() != null) {
                condiciones.add(criterios.greaterThanOrEqualTo(cambio.get("fecha"),
                        filtro.desde().atStartOfDay(zona).toOffsetDateTime()));
            }
            if (filtro.hasta() != null) {
                condiciones.add(criterios.lessThan(cambio.get("fecha"),
                        filtro.hasta().plusDays(1).atStartOfDay(zona).toOffsetDateTime()));
            }
            if (filtro.campo() != null) {
                Subquery<Long> conElCampo = consulta.subquery(Long.class);
                var detalle = conElCampo.from(CambioPerfilDetalle.class);
                Join<CambioPerfilDetalle, CambioPerfil> padre = detalle.join("cambio");
                conElCampo.select(padre.get("id")).where(criterios.equal(padre.get("id"), cambio.get("id")),
                        criterios.like(detalle.get("campo"), comoPrefijo(filtro.campo()), '\\'));
                condiciones.add(criterios.exists(conElCampo));
            }
            return criterios.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private static String comoPrefijo(String campo) {
        return campo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Filtros opcionales. {@code campo} también acepta un prefijo, por ejemplo "direcciones". */
    public record Filtro(String campo, Origen origen, LocalDate desde, LocalDate hasta) {

        public Filtro {
            campo = campo == null || campo.isBlank() ? null : campo.trim();
            if (desde != null && hasta != null && desde.isAfter(hasta)) {
                throw new ReglaNegocioException("La fecha 'desde' no puede ser posterior a 'hasta'");
            }
        }
    }

    public record PaginaHistorial(List<CambioResponse> cambios, int pagina, int tamanio, long total) {
    }

    public record CambioResponse(Long id, OffsetDateTime fecha, TipoCambio tipo, Origen origen, String responsable,
                                 Long idEvento, List<CampoResponse> campos) {

        static CambioResponse de(CambioPerfil cambio, String filtroCampo) {
            return new CambioResponse(cambio.getId(), cambio.getFecha(), cambio.getTipo(), cambio.getOrigen(),
                    cambio.getResponsable(), cambio.getIdEvento(), cambio.getDetalles().stream()
                    .filter(d -> filtroCampo == null || d.getCampo().startsWith(filtroCampo))
                    .map(d -> new CampoResponse(d.getCampo(), d.getValorAnterior(), d.getValorNuevo()))
                    .toList());
        }
    }

    public record CampoResponse(String campo, String anterior, String nuevo) {
    }
}
