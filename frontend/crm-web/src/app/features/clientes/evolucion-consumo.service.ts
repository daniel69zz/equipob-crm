import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IdentificadorOrigen } from './clientes.service';

export type TipoPeriodo = 'MENSUAL' | 'TRIMESTRAL' | 'ANUAL';

/** docs/compra/evolucion-consumo-categoria.md#tendencia-de-cada-categoría */
export type Tendencia = 'CRECE' | 'ESTABLE' | 'DECRECE' | 'DEJO_DE_COMPRAR' | 'SIN_CONSUMO' | 'SIN_COMPARACION';

/** Sin monto de referencia `porcentaje` es nulo: pasar de nada a algo no tiene un porcentaje finito. */
export interface Variacion {
  monto: number;
  porcentaje: number | null;
}

export interface PuntoEvolucion {
  periodo: string;
  monto: number;
  compras: number;
  unidades: number;
  /** Nulo en el primer periodo del rango. */
  variacionAnterior: Variacion | null;
  variacionBase: Variacion;
}

export interface SerieCategoria {
  categoria: string;
  monto: number;
  compras: number;
  unidades: number;
  tendencia: Tendencia;
  evolucion: PuntoEvolucion[];
}

/** Periodo recortado al rango: `parcial` avisa que el rango no lo cubre completo. */
export interface PeriodoEvolucion {
  clave: string;
  inicio: string;
  fin: string;
  parcial: boolean;
}

export interface TotalPeriodo {
  periodo: string;
  monto: number;
  compras: number;
}

export interface CalidadDatos {
  comprasAnalizadas: number;
  comprasAnuladasExcluidas: number;
  registrosSinCategoria: number;
  registrosSinMonto: number;
  devolucionesInconsistentes: number;
  comprasConDesgloseInconsistente: number;
  consistente: boolean;
}

export interface EvolucionConsumo {
  desde: string;
  hasta: string;
  periodo: TipoPeriodo;
  zonaHoraria: string;
  periodoBase: string;
  periodos: PeriodoEvolucion[];
  categoriasDisponibles: string[];
  categorias: SerieCategoria[];
  totales: TotalPeriodo[];
  calidadDatos: CalidadDatos;
  sinDatos: boolean;
}

/** Sin fechas el servicio toma los últimos 12 meses; sin categorías, todas. */
export interface FiltroEvolucion {
  desde: string;
  hasta: string;
  periodo: TipoPeriodo;
  categorias: string[];
  periodoBase: string;
}

/**
 * Evolución del consumo de un cliente por categoría (SCRUM-32,
 * GET /api/comportamiento/clientes/{clienteId}/evolucion-consumo).
 */
@Injectable({ providedIn: 'root' })
export class EvolucionConsumoService {
  private readonly http = inject(HttpClient);

  consultar(
    idCliente: number,
    identificadores: IdentificadorOrigen[],
    filtro: FiltroEvolucion,
  ): Observable<EvolucionConsumo> {
    let params = identificadores.reduce((p, id) => p.append('identificador', id.idCliente), new HttpParams());
    params = params.set('periodo', filtro.periodo);
    if (filtro.desde) {
      params = params.set('desde', filtro.desde);
    }
    if (filtro.hasta) {
      params = params.set('hasta', filtro.hasta);
    }
    if (filtro.periodoBase) {
      params = params.set('periodoBase', filtro.periodoBase);
    }
    params = filtro.categorias.reduce((p, categoria) => p.append('categoria', categoria), params);
    return this.http.get<EvolucionConsumo>(`/api/comportamiento/clientes/${idCliente}/evolucion-consumo`, { params });
  }
}
