package com.maxiconecta.crm.comportamiento.compra;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Categorías conocidas por el CRM a partir de las compras recibidas de Marketplace y Ventas. */
@RestController
public class CategoriasConsumoController {
    private final JdbcTemplate jdbc;

    public CategoriasConsumoController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/comportamiento/categorias")
    @Transactional(readOnly = true)
    public List<String> listar() {
        return jdbc.queryForList("""
                SELECT DISTINCT btrim(categoria) AS nombre
                FROM comportamiento.compra_item
                WHERE categoria IS NOT NULL AND btrim(categoria) <> ''
                ORDER BY nombre
                """, String.class);
    }
}
