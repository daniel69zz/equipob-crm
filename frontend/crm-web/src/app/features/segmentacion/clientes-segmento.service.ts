import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type NivelConsumo = 'BAJO' | 'MEDIO' | 'ALTO';
export type OrdenClientes = 'valor' | 'nivelConsumo' | 'ultimaCompra' | 'frecuencia' | 'segmento' | 'asignadoEn';
export type Direccion = 'asc' | 'desc';

/** Segmento que se puede consultar, con cuántos clientes tiene hoy (GET /api/segmentacion/segmentos). */
export interface SegmentoDisponible {
  segmento: string;
  indicador: 'FRECUENCIA' | 'VALOR' | 'CATEGORIA' | null;
  /** null cuando ningún criterio tiene hoy ese nombre. */
  criterioActivo: boolean | null;
  clientes: number;
}

export interface ClienteDelSegmento {
  clienteId: number;
  nombres: string | null;
  apellidos: string | null;
  segmento: string | null;
  indicador: string | null;
  asignadoEn: string | null;
  nivelConsumo: NivelConsumo | null;
  valorAcumulado: number | null;
  frecuencia: number | null;
  ultimaCompra: string | null;
}

export interface PaginaClientesSegmento {
  content: ClienteDelSegmento[];
  pagina: number;
  tamanio: number;
  total: number;
  orden: OrdenClientes;
  direccion: Direccion;
  /** false cuando servicio-perfil no respondió: los clientes llegan sin nombre. */
  nombresDisponibles: boolean;
}

/** Grupo y filtros como texto: los vacíos no se envían. */
export interface FiltroClientesSegmento {
  segmento: string;
  nivelConsumo: '' | NivelConsumo;
  ultimaCompraDesde: string;
  ultimaCompraHasta: string;
}

/**
 * Clientes de un segmento o nivel de consumo (SCRUM-28, GET /api/segmentacion/clientes):
 * docs/segmentacion/clientes-por-segmento.md.
 */
@Injectable({ providedIn: 'root' })
export class ClientesSegmentoService {
  private readonly http = inject(HttpClient);

  segmentos(): Observable<SegmentoDisponible[]> {
    return this.http.get<SegmentoDisponible[]>('/api/segmentacion/segmentos');
  }

  buscar(
    filtro: FiltroClientesSegmento,
    orden: OrdenClientes,
    direccion: Direccion,
    pagina: number,
    tamanio: number,
  ): Observable<PaginaClientesSegmento> {
    let params = new HttpParams().set('orden', orden).set('direccion', direccion).set('pagina', pagina).set('tamanio', tamanio);
    for (const [clave, valor] of Object.entries(filtro)) {
      if (valor.trim()) {
        params = params.set(clave, valor.trim());
      }
    }
    return this.http.get<PaginaClientesSegmento>('/api/segmentacion/clientes', { params });
  }
}
