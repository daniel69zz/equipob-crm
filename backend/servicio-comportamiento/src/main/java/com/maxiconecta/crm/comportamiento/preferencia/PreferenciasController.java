package com.maxiconecta.crm.comportamiento.preferencia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** API del perfil persistido. No recibe categorías, montos ni identificadores externos del navegador. */
@RestController
@RequestMapping("/api/comportamiento/clientes/{clienteId}/preferencias")
public class PreferenciasController {
    private final PerfilesPreferencias perfiles;
    public PreferenciasController(PerfilesPreferencias perfiles) { this.perfiles = perfiles; }

    @PostMapping("/construir")
    public PerfilPreferencias construir(@PathVariable long clienteId) { return perfiles.construir(clienteId); }

    @GetMapping
    public PerfilPreferencias consultar(@PathVariable long clienteId) { return perfiles.consultar(clienteId); }

    /** SCRUM-368: actualizaciones por eventos de venta, de la más reciente a la más antigua. */
    @GetMapping("/actualizaciones")
    public List<PerfilPreferenciasRepository.Actualizacion> actualizaciones(@PathVariable long clienteId) {
        return perfiles.actualizaciones(clienteId);
    }
}
