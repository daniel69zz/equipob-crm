import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth/auth.service';
import { Permisos } from '../../core/auth/sesion';

/** Formatos estándar de exportación de los reportes de clientes (SCRUM-479, docs/compra/exportacion-reportes.md). */
export type FormatoExportacion = 'csv' | 'pdf';

const FORMATOS: { valor: FormatoExportacion; nombre: string }[] = [
  { valor: 'csv', nombre: 'CSV — Excel y herramientas de análisis' },
  { valor: 'pdf', nombre: 'PDF — imprimir o compartir' },
];

/**
 * Selección del formato de exportación de un reporte y botón para descargarlo (SCRUM-60, SCRUM-480). Solo se muestra
 * a quien tiene REPORTES_EXPORTAR (SCRUM-482); es una ayuda visual, el Gateway y el servicio lo vuelven a comprobar.
 * El archivo lo genera el servidor a partir del mismo reporte que se ve en pantalla.
 */
@Component({
  selector: 'app-exportar-reporte',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (puedeExportar) {
      <div class="exportar" role="group" aria-label="Exportar reporte">
        <label>
          Formato
          <select name="formatoExportacion" [(ngModel)]="formato" aria-label="Formato de exportación">
            @for (f of formatos; track f.valor) {
              <option [ngValue]="f.valor">{{ f.nombre }}</option>
            }
          </select>
        </label>
        <button type="button" class="secundario" (click)="exportar.emit(formato)" [disabled]="deshabilitado || exportando">
          {{ exportando ? 'Exportando…' : 'Exportar ' + formato.toUpperCase() }}
        </button>
      </div>
    }
  `,
  styles: `
    .exportar { display: flex; align-items: flex-end; gap: 0.5rem; }
    label { display: flex; flex-direction: column; gap: 0.25rem; font-size: 0.85rem; color: var(--color-texto-suave); }
  `,
})
export class ExportarReporteComponent {
  private readonly auth = inject(AuthService);

  @Input() deshabilitado = false;
  @Input() exportando = false;
  @Output() readonly exportar = new EventEmitter<FormatoExportacion>();

  readonly formatos = FORMATOS;
  readonly puedeExportar = this.auth.tienePermiso(Permisos.REPORTES_EXPORTAR);
  formato: FormatoExportacion = 'csv';
}

/** Descarga en el navegador el archivo que devolvió el servidor, con el nombre que propone. */
export function descargarArchivo(respuesta: HttpResponse<Blob>, nombrePorDefecto: string): void {
  const url = URL.createObjectURL(respuesta.body!);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombreArchivo(respuesta.headers.get('Content-Disposition')) ?? nombrePorDefecto;
  enlace.click();
  URL.revokeObjectURL(url);
}

/** El nombre que propone el servidor en Content-Disposition, o null si no lo indica. */
export function nombreArchivo(cabecera: string | null): string | null {
  return /filename="?([^";]+)"?/.exec(cabecera ?? '')?.[1] ?? null;
}

/** Mensaje para el usuario cuando la exportación falla; el 403 es el criterio 2 de SCRUM-60. */
export function motivoFalloExportacion(error: HttpErrorResponse, reporte: string): string {
  if (error.status === 403) {
    return 'Su rol no tiene permiso para exportar los reportes de clientes.';
  }
  return `No se pudo exportar el ${reporte}.`;
}
