package com.maxiconecta.crm.segmentacion.consulta;

import com.maxiconecta.crm.segmentacion.consulta.ListadoClientesRepository.FilaCliente;
import com.maxiconecta.crm.segmentacion.consulta.ListadoClientesRepository.PaginaFilas;
import com.maxiconecta.crm.segmentacion.consulta.ListadoClientesRepository.SegmentoDisponible;
import com.maxiconecta.crm.segmentacion.consulta.OrdenClientes.Direccion;
import com.maxiconecta.crm.segmentacion.consulta.PerfilesClientes.NombresClientes;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Listado de clientes de un segmento o nivel de consumo para dirigir acciones comerciales (SCRUM-28,
 * docs/segmentacion/criterio-consulta-clientes.md): lee la página de la segmentación, le pone el
 * nombre de cada cliente y deja registrada la consulta en la auditoría antes de responder.
 */
@Service
public class ConsultaClientesSegmento {

    public static final int TAMANIO_POR_DEFECTO = 20;
    public static final int TAMANIO_MAXIMO_PAGINA = 100;

    private final ListadoClientesRepository listado;
    private final PerfilesClientes perfiles;
    private final AuditoriaConsultaClientes auditoria;

    public ConsultaClientesSegmento(ListadoClientesRepository listado, PerfilesClientes perfiles,
                                    AuditoriaConsultaClientes auditoria) {
        this.listado = listado;
        this.perfiles = perfiles;
        this.auditoria = auditoria;
    }

    public ListadoClientes buscar(FiltroClientes filtro, OrdenClientes orden, Direccion direccion, int pagina,
                                  int tamanio, String usuario) {
        int paginaValida = Math.max(pagina, 0);
        int tamanioValido = Math.min(Math.max(tamanio, 1), TAMANIO_MAXIMO_PAGINA);
        PaginaFilas encontrados = listado.buscar(filtro, orden, direccion, paginaValida, tamanioValido);
        List<Long> clientes = encontrados.filas().stream().map(FilaCliente::clienteId).toList();
        NombresClientes nombres = perfiles.buscar(clientes);
        auditoria.registrar(usuario, filtro, orden, direccion, paginaValida, tamanioValido, encontrados.total(),
                clientes);
        List<ListadoClientes.Cliente> contenido = encontrados.filas().stream()
                .map(fila -> ListadoClientes.Cliente.de(fila, nombres.de(fila.clienteId())))
                .toList();
        return new ListadoClientes(contenido, paginaValida, tamanioValido, encontrados.total(), orden.parametro(),
                direccion.parametro(), nombres.disponibles());
    }

    public List<SegmentoDisponible> segmentos() {
        return listado.segmentos();
    }
}
