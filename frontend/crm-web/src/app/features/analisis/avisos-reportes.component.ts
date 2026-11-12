import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { filter, timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Permisos } from '../../core/auth/sesion';
import { ReportesProgramadosService } from './reportes-programados.service';

const PERMISOS_PROGRAMACION = [Permisos.REPORTES_CONSULTAR, Permisos.REPORTES_EXPORTAR, Permisos.REPORTES_PROGRAMAR];
const INTERVALO_MS = 30000;

/** Avisos de los reportes programados en la cabecera (SCRUM-61): cuántas ejecuciones no se han leído. */
@Component({
  selector: 'app-avisos-reportes', standalone: true, imports: [RouterLink],
  template: `@if (puedeConsultar()) {
    <a routerLink="/analisis/programaciones" aria-live="polite">Reportes: {{ servicio.noLeidas() }} avisos sin leer</a>
  }`,
  styles: `a { font-size:.85rem; color:var(--color-primario); }`,
})
export class AvisosReportesComponent {
  private readonly auth = inject(AuthService);
  protected readonly servicio = inject(ReportesProgramadosService);

  puedeConsultar() { return PERMISOS_PROGRAMACION.every(p => this.auth.tienePermiso(p)); }

  constructor() {
    timer(0, INTERVALO_MS).pipe(filter(() => this.puedeConsultar()), takeUntilDestroyed())
      .subscribe(() => this.servicio.actualizarAvisos());
  }
}
