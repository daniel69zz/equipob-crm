import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { IdentificadorOrigen } from './clientes.service';

export type TendenciaConsumo = 'CRECE' | 'DECAE' | 'ESTABLE' | 'NUEVO';

/**
 * Variación de una métrica del periodo A al B (docs/compra/comparacion-periodos.md). Con el periodo A en
 * cero no hay base para un porcentaje: `variacionPorcentual` es nulo y la tendencia, `NUEVO`.
 */
export interface VariacionConsumo {
  periodoA: number;
  periodoB: number;
  variacionAbsoluta: number;
  variacionPorcentual: number | null;
  tendencia: TendenciaConsumo;
}

export interface PeriodoComparado {
  desde: string;
  hasta: string;
}

export interface ResultadoComparacion {
  alcance: { tipo: 'CLIENTE' | 'SEGMENTO'; segmento: string | null; clientes: number | null };
  zonaHoraria: string;
  periodoA: PeriodoComparado;
  periodoB: PeriodoComparado;
  frecuencia: VariacionConsumo;
  valor: VariacionConsumo;
  sinDatos: boolean;
  mensaje: string | null;
}

export interface PeriodosElegidos {
  desdeA: string;
  hastaA: string;
  desdeB: string;
  hastaB: string;
}

/**
 * Comparación de la frecuencia y el valor de compra entre dos periodos (SCRUM-40): de un cliente
 * (GET /api/comportamiento/clientes/{id}/comparacion-periodos) o de los clientes de un segmento
 * (GET /api/comportamiento/comparacion-periodos?segmento=).
 */
@Injectable({ providedIn: 'root' })
export class ComparacionPeriodosService {
  private readonly http = inject(HttpClient);

  compararCliente(
    idCliente: number,
    identificadores: IdentificadorOrigen[],
    periodos: PeriodosElegidos,
  ): Observable<ResultadoComparacion> {
    const params = identificadores.reduce(
      (acumulado, id) => acumulado.append('identificador', id.idCliente),
      parametrosDePeriodos(periodos),
    );
    return this.http.get<ResultadoComparacion>(`/api/comportamiento/clientes/${idCliente}/comparacion-periodos`, {
      params,
    });
  }

  compararSegmento(segmento: string, periodos: PeriodosElegidos): Observable<ResultadoComparacion> {
    return this.http.get<ResultadoComparacion>('/api/comportamiento/comparacion-periodos', {
      params: parametrosDePeriodos(periodos).set('segmento', segmento),
    });
  }
}

function parametrosDePeriodos(periodos: PeriodosElegidos): HttpParams {
  return new HttpParams()
    .set('desdeA', periodos.desdeA)
    .set('hastaA', periodos.hastaA)
    .set('desdeB', periodos.desdeB)
    .set('hastaB', periodos.hastaB);
}
