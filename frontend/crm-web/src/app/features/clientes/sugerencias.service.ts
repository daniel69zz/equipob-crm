import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** docs/preferencias/sugerencia-categorias.md#cuándo-no-hay-sugerencias-criterio-de-aceptación-3 */
export type MotivoSinSugerencias =
  | 'SIN_PERFIL'
  | 'SIN_PREFERENCIAS'
  | 'SIN_SEGMENTO'
  | 'SIN_DATOS_SEGMENTO'
  | 'SIN_CATEGORIAS_NUEVAS';

export interface CategoriaSugerida {
  posicion: number;
  categoria: string;
  /** Proporción (0 a 1) de los clientes del segmento que compran la categoría. */
  afinidad: number;
  clientesQueCompran: number;
  montoSegmento: number;
}

export interface SugerenciasCliente {
  clienteId: number;
  segmento: string | null;
  generadoEn: string;
  criterio: string;
  clientesDelSegmento: number;
  sugerencias: CategoriaSugerida[];
  sinSugerencias: boolean;
  motivo: MotivoSinSugerencias | null;
  mensaje: string | null;
}

/** Categorías afines sugeridas (SCRUM-44, GET /api/comportamiento/clientes/{id}/sugerencias-categorias). */
@Injectable({ providedIn: 'root' })
export class SugerenciasService {
  private readonly http = inject(HttpClient);

  consultar(clienteId: number) {
    return this.http.get<SugerenciasCliente>(`/api/comportamiento/clientes/${clienteId}/sugerencias-categorias`);
  }
}
