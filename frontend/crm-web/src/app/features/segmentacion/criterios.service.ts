import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export type Indicador = 'FRECUENCIA' | 'VALOR' | 'CATEGORIA';
export type Condicion = 'ENTRE' | 'MAYOR_IGUAL' | 'MENOR_IGUAL' | 'IGUAL' | 'CONTIENE';
export interface SolicitudCriterio {
  nombre: string; descripcion: string; indicador: Indicador; condicion: Condicion;
  minimo: number | null; maximo: number | null; categoria: string | null; version?: number;
}
export interface Criterio extends SolicitudCriterio {
  id: number; activo: boolean; version: number; creadoEn: string; actualizadoEn: string; actualizadoPor: string;
}
export interface CambioCriterio {
  id: number; fecha: string; usuario: string; operacion: string; anterior: Criterio | null; nuevo: Criterio;
}
export interface Caso { frecuencia?: number | null; valor?: number | null; categorias?: string[]; }
export interface Comprobacion { criterioId: number; version: number; activo: boolean; cumple: boolean; }

@Injectable({ providedIn: 'root' })
export class CriteriosService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/segmentacion/criterios';
  listar() { return this.http.get<Criterio[]>(this.url); }
  crear(datos: SolicitudCriterio) { return this.http.post<Criterio>(this.url, datos); }
  editar(id: number, datos: SolicitudCriterio) { return this.http.put<Criterio>(`${this.url}/${id}`, datos); }
  estado(criterio: Criterio, activo: boolean) {
    return this.http.patch<Criterio>(`${this.url}/${criterio.id}/estado`, { activo, version: criterio.version });
  }
  historial(id: number) { return this.http.get<CambioCriterio[]>(`${this.url}/${id}/historial`); }
  comprobar(id: number, caso: Caso) { return this.http.post<Comprobacion>(`${this.url}/${id}/comprobar`, caso); }
}

export const INDICADORES: Record<Indicador, string> = {
  FRECUENCIA: 'Frecuencia de compra', VALOR: 'Valor acumulado (Bs)', CATEGORIA: 'Categoría consumida',
};
export const CONDICIONES: Record<Condicion, string> = {
  ENTRE: 'Entre (incluye ambos límites)', MAYOR_IGUAL: 'Mayor o igual que',
  MENOR_IGUAL: 'Menor o igual que', IGUAL: 'Igual a', CONTIENE: 'Ha consumido la categoría',
};
export function reglaTexto(c: SolicitudCriterio): string {
  const unidad = c.indicador === 'VALOR' ? ' Bs' : ' compras';
  if (c.indicador === 'CATEGORIA') return `Ha consumido ${c.categoria ?? 'una categoría'}`;
  if (c.condicion === 'ENTRE') return `${c.minimo ?? '…'} a ${c.maximo ?? '…'}${unidad} (inclusive)`;
  if (c.condicion === 'MAYOR_IGUAL') return `≥ ${c.minimo ?? '…'}${unidad}`;
  if (c.condicion === 'MENOR_IGUAL') return `≤ ${c.maximo ?? '…'}${unidad}`;
  return `= ${c.minimo ?? '…'}${unidad}`;
}
