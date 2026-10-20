import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface ReglaHistorica {
  id?: number;
  criterioId?: number;
  version?: number;
  nombre?: string;
  segmento?: string;
  indicador?: string;
  condicion?: string;
  minimo?: number | null;
  maximo?: number | null;
  categoria?: string | null;
  clasificacion?: string | null;
}

export interface CambioSegmento {
  id: number;
  clienteId: number;
  fecha: string;
  operacion: string;
  segmentoAnterior: string | null;
  segmentoNuevo: string | null;
  regla: ReglaHistorica | null;
  criteriosEvaluados: ReglaHistorica[] | null;
  metricas: { frecuencia?: number | null; valor?: number | null; categorias?: string[] | null; clasificacionFrecuencia?: string };
  motivo: string;
  idEvento: string;
  tipoEventoOrigen: string | null;
  reglaCompleta: boolean;
}

export interface PaginaSegmentos {
  contenido: CambioSegmento[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

@Injectable({ providedIn: 'root' })
export class HistorialSegmentosService {
  private readonly http = inject(HttpClient);

  consultar(clienteId: string, pagina: number) {
    const params = new HttpParams().set('pagina', pagina).set('tamanio', 20);
    return this.http.get<PaginaSegmentos>(
      `/api/segmentacion/clientes/${encodeURIComponent(clienteId)}/historial-segmentos`, { params });
  }
}
