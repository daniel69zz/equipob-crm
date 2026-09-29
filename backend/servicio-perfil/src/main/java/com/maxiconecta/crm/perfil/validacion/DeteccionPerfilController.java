package com.maxiconecta.crm.perfil.validacion;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** La detección exige CLIENTE_EDITAR en el Gateway, como las demás escrituras del perfil. */
@RestController
public class DeteccionPerfilController {

    private final DeteccionPerfiles deteccion;

    public DeteccionPerfilController(DeteccionPerfiles deteccion) {
        this.deteccion = deteccion;
    }

    @PostMapping("/api/perfil/clientes/{clienteId}/validacion")
    public DeteccionPerfiles.Resultado detectar(@PathVariable Long clienteId) {
        return deteccion.detectar(clienteId);
    }
}
