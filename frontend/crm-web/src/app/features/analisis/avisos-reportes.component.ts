import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { timer, filter, switchMap, catchError, of } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-avisos-reportes', standalone: true, imports: [RouterLink],
  template: `@if (puedeConsultar()) {
    <a routerLink="/analisis/programaciones" aria-live="polite">Reportes: {{ noLeidas() }} avisos sin leer</a>
  }`,
  styles: `a { font-size:.85rem; color:var(--color-primario); }`,
})
export class AvisosReportesComponent {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  readonly noLeidas = signal(0);
  puedeConsultar() { return ['REPORTES_CONSULTAR', 'REPORTES_EXPORTAR', 'REPORTES_PROGRAMAR'].every(p => this.auth.tienePermiso(p)); }
  constructor() {
    timer(0, 30000).pipe(filter(() => this.puedeConsultar()),
      switchMap(() => this.http.get<{ noLeidas: number }>('/api/comportamiento/reportes/programaciones/avisos').pipe(catchError(() => of({ noLeidas: 0 })))),
      takeUntilDestroyed()).subscribe(r => this.noLeidas.set(r.noLeidas));
  }
}
