package com.maxiconecta.crm.perfil.vinculacion;

import com.maxiconecta.crm.perfil.cliente.EstadoVinculacion;
import com.maxiconecta.crm.perfil.cliente.Origen;
import com.maxiconecta.crm.perfil.sincronizacion.EstadoEventoCliente;
import com.maxiconecta.crm.perfil.sincronizacion.SincronizacionClientes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vinculación de identificadores de origen (docs/perfil/identificadores-origen.md). Las decisiones
 * (POST) exigen CLIENTE_EDITAR en el Gateway; el responsable es el usuario que envía el Gateway.
 */
@RestController
public class VinculacionController {

    static final String CABECERA_USUARIO = "X-Usuario";
    static final String USUARIO_DESCONOCIDO = "desconocido";

    private final VinculacionIdentificadores vinculacion;
    private final SincronizacionClientes sincronizacion;

    public VinculacionController(VinculacionIdentificadores vinculacion, SincronizacionClientes sincronizacion) {
        this.vinculacion = vinculacion;
        this.sincronizacion = sincronizacion;
    }

    @GetMapping("/api/perfil/vinculaciones")
    public List<VinculacionResponse> listar(@RequestParam(defaultValue = "PENDIENTE") EstadoVinculacion estado) {
        return vinculacion.listar(estado);
    }

    @PostMapping("/api/perfil/clientes/{clienteId}/identificadores")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoVinculacion vincular(@PathVariable Long clienteId, @Valid @RequestBody IdentificadorRequest solicitud,
                                         @RequestHeader(value = CABECERA_USUARIO, required = false) String usuario) {
        List<Long> pendientes = vinculacion.vincular(clienteId, solicitud.origen(), solicitud.idCliente(),
                responsable(usuario));
        return new ResultadoVinculacion(solicitud.origen(), solicitud.idCliente().trim(), clienteId, aplicar(pendientes));
    }

    @PostMapping("/api/perfil/vinculaciones/nuevo-perfil")
    public ResultadoVinculacion declararNuevoPerfil(@Valid @RequestBody IdentificadorRequest solicitud,
                                                    @RequestHeader(value = CABECERA_USUARIO, required = false) String usuario) {
        List<Long> pendientes = vinculacion.declararNuevoPerfil(solicitud.origen(), solicitud.idCliente(),
                responsable(usuario));
        return new ResultadoVinculacion(solicitud.origen(), solicitud.idCliente().trim(), null, aplicar(pendientes));
    }

    /** Los eventos pendientes se aplican en el orden en que llegaron, cada uno en su propia transacción. */
    private List<EventoAplicado> aplicar(List<Long> pendientes) {
        return pendientes.stream().map(id -> new EventoAplicado(id, sincronizacion.procesar(id))).toList();
    }

    private static String responsable(String usuario) {
        return usuario != null && !usuario.isBlank() ? usuario.trim() : USUARIO_DESCONOCIDO;
    }

    public record IdentificadorRequest(@NotNull Origen origen, @NotBlank @Size(max = 64) String idCliente) {
    }

    public record EventoAplicado(Long idEvento, EstadoEventoCliente estado) {
    }

    public record ResultadoVinculacion(Origen origen, String idCliente, Long idClienteVinculado,
                                       List<EventoAplicado> eventosAplicados) {
    }
}
