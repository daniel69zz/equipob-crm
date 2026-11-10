import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FormatoExportacion } from './exportar-reporte.component';

export interface CategoriaReporte {
  categoria: string;
  compras: number;
  unidades: number;
  monto: number;
  porcentaje: number;
  sinCategoria: boolean;
}

export interface ReporteComportamiento {
  generadoEn: string;
  desde: string;
  hasta: string;
  zonaHoraria: string;
  alcance: { segmento: string | null; clientesDelSegmento: number | null };
  sinDatos: boolean;
  mensaje: string | null;
  frecuencia: { compras: number; clientesConCompras: number; comprasPorCliente: number | null };
  valor: { total: number; ticketPromedio: number | null; valorPorCliente: number | null };
  categorias: CategoriaReporte[];
}

/**
 * Reporte de frecuencia, valor y categorías de compra (SCRUM-58,
 * GET /api/comportamiento/reportes/comportamiento, docs/compra/reportes-comportamiento.md) y su exportación a CSV o
 * PDF (SCRUM-60, docs/compra/exportacion-reportes.md).
 */
@Injectable({ providedIn: 'root' })
export class ReporteComportamientoService {
  private readonly http = inject(HttpClient);

  generar(desde: string, hasta: string, segmento: string | null): Observable<ReporteComportamiento> {
    return this.http.get<ReporteComportamiento>('/api/comportamiento/reportes/comportamiento',
      { params: parametros(desde, hasta, segmento) });
  }

  /** El archivo lo genera el servidor a partir del mismo reporte que se ve en pantalla. */
  exportar(desde: string, hasta: string, segmento: string | null, formato: FormatoExportacion): Observable<HttpResponse<Blob>> {
    return this.http.get('/api/comportamiento/reportes/comportamiento/exportar',
      { params: parametros(desde, hasta, segmento).set('formato', formato), responseType: 'blob', observe: 'response' });
  }
}

function parametros(desde: string, hasta: string, segmento: string | null): HttpParams {
  const params = new HttpParams().set('desde', desde).set('hasta', hasta);
  return segmento ? params.set('segmento', segmento) : params;
}
