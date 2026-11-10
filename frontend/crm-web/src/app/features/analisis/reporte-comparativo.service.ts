import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type Tendencia = 'CRECE' | 'DECAE' | 'ESTABLE' | 'NUEVO';

export interface IndicadorComparado {
  codigo: string;
  nombre: string;
  periodoA: number | null;
  periodoB: number | null;
  variacionAbsoluta: number | null;
  variacionPorcentual: number | null;
  tendencia: Tendencia | null;
}

export interface ReporteComparativo {
  generadoEn: string;
  periodoA: { desde: string; hasta: string };
  periodoB: { desde: string; hasta: string };
  zonaHoraria: string;
  alcance: { segmento: string | null; clientesDelSegmento: number | null };
  sinDatos: boolean;
  mensaje: string | null;
  indicadores: IndicadorComparado[];
}

export interface PeriodosComparados {
  desdeA: string;
  hastaA: string;
  desdeB: string;
  hastaB: string;
  segmento: string | null;
}

/**
 * Reporte comparativo de indicadores de clientes entre dos periodos y su exportación a CSV (SCRUM-62,
 * GET /api/comportamiento/reportes/comparativo[/csv], docs/compra/reporte-comparativo.md).
 */
@Injectable({ providedIn: 'root' })
export class ReporteComparativoService {
  private readonly http = inject(HttpClient);

  comparar(periodos: PeriodosComparados): Observable<ReporteComparativo> {
    return this.http.get<ReporteComparativo>('/api/comportamiento/reportes/comparativo', { params: parametros(periodos) });
  }

  /** El CSV lo genera el servidor a partir del mismo reporte, para que conserve los mismos valores. */
  exportarCsv(periodos: PeriodosComparados): Observable<HttpResponse<Blob>> {
    return this.http.get('/api/comportamiento/reportes/comparativo/csv',
      { params: parametros(periodos), responseType: 'blob', observe: 'response' });
  }
}

function parametros(p: PeriodosComparados): HttpParams {
  let params = new HttpParams().set('desdeA', p.desdeA).set('hastaA', p.hastaA).set('desdeB', p.desdeB).set('hastaB', p.hastaB);
  if (p.segmento) {
    params = params.set('segmento', p.segmento);
  }
  return params;
}
