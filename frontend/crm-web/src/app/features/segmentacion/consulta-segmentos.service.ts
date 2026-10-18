import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface SegmentoDisponible { id: number | null; nombre: string; activo: boolean; legado: boolean; }
export interface ClienteSegmentado {
  clienteId: number; nombre: string; segmentoId: number | null; segmento: string | null;
  nivelConsumo: 'BAJO' | 'MEDIO' | 'ALTO' | null; ultimaCompra: string | null;
}
export interface PaginaSegmentados { clientes: ClienteSegmentado[]; pagina: number; tamanio: number; total: number; }
export type ConsultaSegmentos = Record<string, string>;

@Injectable({ providedIn: 'root' })
export class ConsultaSegmentosService {
  private readonly http = inject(HttpClient);
  catalogo() { return this.http.get<SegmentoDisponible[]>('/api/segmentacion/segmentos'); }
  buscar(filtros: ConsultaSegmentos) {
    let params = new HttpParams();
    for (const [clave, valor] of Object.entries(filtros)) {
      if (valor !== '') params = params.set(clave, valor);
    }
    return this.http.get<PaginaSegmentados>('/api/segmentacion/clientes', { params });
  }
}
