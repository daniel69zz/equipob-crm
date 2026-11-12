import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';

/** Servicio dueño de cada grupo de parámetros generales (SCRUM-65). */
export type ServicioParametros = 'comportamiento' | 'fidelizacion';

export interface ParametroGeneral {
  codigo: string;
  nombre: string;
  descripcion: string;
  unidad: string;
  valor: number;
  minimo: number;
  maximo: number;
  decimales: number;
  version: number;
  modificadoPor: string;
  modificadoEn: string;
}

export interface CambioParametro {
  codigo: string;
  valor: number;
  version: number;
}

/** Un cambio registrado en la auditoría (SCRUM-521). */
export interface CambioRegistrado {
  fecha: string;
  usuario: string;
  codigo: string;
  nombre: string;
  unidad: string;
  valorAnterior: number;
  valorNuevo: number;
  servicio: string;
}

/**
 * Parámetros generales del CRM (SCRUM-65): cada servicio guarda los suyos (comportamiento, el periodo de inactividad
 * y los umbrales de consumo; fidelización, la vigencia de los puntos) y esta pantalla los reúne.
 */
@Injectable({ providedIn: 'root' })
export class ParametrosGeneralesService {
  private readonly http = inject(HttpClient);

  listar(servicio: ServicioParametros) {
    return this.http.get<ParametroGeneral[]>(this.url(servicio));
  }

  /** Guarda juntos los cambios de un servicio: se aplican todos o ninguno. */
  guardar(servicio: ServicioParametros, cambios: CambioParametro[]) {
    return this.http.put<ParametroGeneral[]>(this.url(servicio), { cambios });
  }

  historial(servicio: ServicioParametros) {
    return this.http.get<CambioRegistrado[]>(`${this.url(servicio)}/historial`);
  }

  private url(servicio: ServicioParametros) {
    return `/api/${servicio}/parametros`;
  }
}
