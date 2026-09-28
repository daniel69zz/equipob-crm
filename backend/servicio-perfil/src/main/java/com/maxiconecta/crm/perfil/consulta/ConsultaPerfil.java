package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.Cliente;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigen;
import com.maxiconecta.crm.perfil.cliente.ClienteOrigenRepository;
import com.maxiconecta.crm.perfil.cliente.ClienteRepository;
import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.comun.RecursoNoEncontradoException;
import com.maxiconecta.crm.perfil.comun.ReglaNegocioException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Consulta del perfil del cliente. Es la base de la ficha integral (SCRUM-10).
 */
@Service
public class ConsultaPerfil {

    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final ClienteRepository clientes;
    private final ClienteOrigenRepository origenes;

    public ConsultaPerfil(ClienteRepository clientes, ClienteOrigenRepository origenes) {
        this.clientes = clientes;
        this.origenes = origenes;
    }

    @Transactional(readOnly = true)
    public PerfilResponse obtener(Long idCliente) {
        Cliente cliente = clientes.findById(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente " + idCliente));
        return PerfilResponse.de(cliente, origenes.findByIdClienteOrderByFechaVinculacion(idCliente));
    }

    /**
     * Busca por identificador de origen, por documento o por estado. Por identificador de origen
     * devuelve como máximo un perfil.
     */
    @Transactional(readOnly = true)
    public Page<ResumenPerfil> buscar(FiltroPerfiles filtro, int pagina, int tamanio) {
        PageRequest solicitud = PageRequest.of(Math.max(pagina, 0), Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA),
                Sort.by(Sort.Order.desc("actualizadoEn"), Sort.Order.desc("id")));
        if (filtro.idClienteOrigen() != null) {
            if (filtro.origen() == null) {
                throw new ReglaNegocioException("Para buscar por 'idClienteOrigen' también se necesita 'origen'");
            }
            List<ResumenPerfil> encontrado = origenes.findById(new ClienteOrigen.Clave(filtro.origen(), filtro.idClienteOrigen()))
                    .flatMap(vinculo -> clientes.findById(vinculo.getIdCliente()))
                    .map(ResumenPerfil::de)
                    .stream().toList();
            return new PageImpl<>(encontrado, solicitud, encontrado.size());
        }
        return clientes.findAll(especificacion(filtro), solicitud).map(ResumenPerfil::de);
    }

    private static Specification<Cliente> especificacion(FiltroPerfiles filtro) {
        return (cliente, consulta, criterios) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.tipoDocumento() != null) {
                condiciones.add(criterios.equal(cliente.get("tipoDocumento"), filtro.tipoDocumento()));
            }
            if (filtro.numeroDocumento() != null) {
                condiciones.add(criterios.equal(cliente.get("numeroDocumento"), filtro.numeroDocumento()));
            }
            if (filtro.estado() != null) {
                condiciones.add(criterios.equal(cliente.get("estado"), filtro.estado()));
            }
            return criterios.and(condiciones.toArray(Predicate[]::new));
        };
    }

    public record FiltroPerfiles(Origen origen, String idClienteOrigen, String tipoDocumento, String numeroDocumento,
                                 EstadoPerfil estado) {

        public FiltroPerfiles {
            idClienteOrigen = limpio(idClienteOrigen);
            tipoDocumento = limpio(tipoDocumento) != null ? tipoDocumento.trim().toUpperCase() : null;
            numeroDocumento = limpio(numeroDocumento);
        }

        private static String limpio(String valor) {
            return valor == null || valor.isBlank() ? null : valor.trim();
        }
    }
}
