package com.maxiconecta.crm.perfil.consulta;

import com.maxiconecta.crm.perfil.cliente.EstadoPerfil;
import com.maxiconecta.crm.perfil.cliente.Origen;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta del perfil del cliente. El API Gateway exige CLIENTE_CONSULTAR para GET /api/perfil/**
 * y audita cada consulta (docs/seguridad/convencion-rutas-clientes.md).
 */
@RestController
@RequestMapping("/api/perfil/clientes")
public class PerfilController {

    private final ConsultaPerfil consulta;

    public PerfilController(ConsultaPerfil consulta) {
        this.consulta = consulta;
    }

    @GetMapping("/{clienteId}")
    public PerfilResponse obtener(@PathVariable Long clienteId) {
        return consulta.obtener(clienteId);
    }

    @GetMapping
    public PaginaPerfiles buscar(@RequestParam(required = false) Origen origen,
                                 @RequestParam(required = false) String idClienteOrigen,
                                 @RequestParam(required = false) String tipoDocumento,
                                 @RequestParam(required = false) String numeroDocumento,
                                 @RequestParam(required = false) EstadoPerfil estado,
                                 @RequestParam(defaultValue = "0") int pagina,
                                 @RequestParam(defaultValue = "50") int tamanio) {
        Page<ResumenPerfil> resultado = consulta.buscar(new ConsultaPerfil.FiltroPerfiles(origen, idClienteOrigen,
                tipoDocumento, numeroDocumento, estado), pagina, tamanio);
        return new PaginaPerfiles(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements());
    }

    public record PaginaPerfiles(List<ResumenPerfil> clientes, int pagina, int tamanio, long total) {
    }
}
