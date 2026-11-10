import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

export interface ConfiguracionReporte {
  nombre: string;
  frecuencia: 'DIARIA' | 'SEMANAL' | 'MENSUAL';
  formato: 'CSV' | 'PDF';
  dias: number;
  segmento: string | null;
  inicio: string;
  fin: string | null;
}
export interface ProgramacionReporte extends ConfiguracionReporte {
  id: string; proxima: string; estado: string;
}
export interface EjecucionReporte {
  id: string; programacion: string; reporte: string; prevista: string; ejecutada: string;
  estado: string; causa: string | null; nombre: string | null; leida: boolean;
}
@Injectable({ providedIn: 'root' })
export class ReportesProgramadosService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/comportamiento/reportes/programaciones';
  listar() { return this.http.get<ProgramacionReporte[]>(this.url); }
  guardar(id: string | null, s: ConfiguracionReporte) {
    return id ? this.http.put(this.url + '/' + id, s) : this.http.post(this.url, s);
  }
  estado(id: string, estado: string) { return this.http.put(`${this.url}/${id}/estado`, { estado }); }
  historial(pagina: number) { return this.http.get<EjecucionReporte[]>(`${this.url}/ejecuciones`, { params: { pagina } }); }
  leer(id: string) { return this.http.put(`${this.url}/ejecuciones/${id}/leida`, {}); }
  archivo(id: string) { return this.http.get(`${this.url}/ejecuciones/${id}/archivo`, { observe: 'response', responseType: 'blob' }); }
}
