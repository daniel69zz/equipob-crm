import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

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
 * GET /api/comportamiento/reportes/comportamiento, docs/compra/reportes-comportamiento.md).
 */
@Injectable({ providedIn: 'root' })
export class ReporteComportamientoService {
  private readonly http = inject(HttpClient);

  generar(desde: string, hasta: string, segmento: string | null): Observable<ReporteComportamiento> {
    let params = new HttpParams().set('desde', desde).set('hasta', hasta);
    if (segmento) {
      params = params.set('segmento', segmento);
    }
    return this.http.get<ReporteComportamiento>('/api/comportamiento/reportes/comportamiento', { params });
  }
}
