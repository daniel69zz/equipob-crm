import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { BandejaNotificaciones, InteraccionesService, Notificacion } from './interacciones.service';
import { NotificacionesPendientes } from './notificaciones-pendientes.service';

/**
 * Notificaciones del usuario de la sesión (SCRUM-459, SCRUM-455): las interacciones que el Administrador le asignó,
 * con el enlace a las interacciones del cliente. Se marcan como leídas una por una.
 */
@Component({
  selector: 'app-notificaciones',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <header class="encabezado">
      <div>
        <h1>Notificaciones</h1>
        <p class="subtitulo">Interacciones que le asignaron, de la más reciente a la más antigua.</p>
      </div>
      <label class="filtro">
        <input type="checkbox" name="soloNoLeidas" [(ngModel)]="soloNoLeidas" (change)="cargar()" /> Solo no leídas
      </label>
    </header>

    @if (error()) {
      <div class="error" role="alert">
        <p>{{ error() }}</p>
        <button type="button" class="secundario" (click)="cargar()">Reintentar</button>
      </div>
    }

    @if (bandeja(); as datos) {
      <p class="conteo" role="status">{{ datos.noLeidas }} {{ datos.noLeidas === 1 ? 'notificación sin leer' : 'notificaciones sin leer' }}.</p>
      <ol class="lista" aria-label="Notificaciones">
        @for (n of datos.notificaciones; track n.id) {
          <li class="tarjeta" [class.no-leida]="!n.leidaEn">
            <p class="mensaje">{{ n.mensaje }}</p>
            @if (n.comentario) { <p class="comentario">«{{ n.comentario }}»</p> }
            <footer>
              <span>De {{ n.creadaPor }} · <time [attr.datetime]="n.creadaEn">{{ n.creadaEn | date: 'dd/MM/yyyy HH:mm' }}</time></span>
              <a [routerLink]="['/clientes', n.clienteId, 'interacciones']">Ver interacciones del cliente</a>
              @if (n.leidaEn) {
                <span>Leída el {{ n.leidaEn | date: 'dd/MM/yyyy HH:mm' }}</span>
              } @else {
                <button type="button" class="secundario" [disabled]="marcando() === n.id" (click)="marcar(n)">Marcar como leída</button>
              }
            </footer>
          </li>
        } @empty {
          <li class="tarjeta vacio">{{ soloNoLeidas ? 'No tiene notificaciones sin leer.' : 'No tiene notificaciones.' }}</li>
        }
      </ol>
    } @else if (!error()) {
      <p role="status">Cargando las notificaciones…</p>
    }
  `,
  styles: `
    .encabezado { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; flex-wrap: wrap; }
    .filtro { display: flex; gap: .4rem; align-items: center; font-size: .9rem; }
    .conteo { color: var(--color-texto-suave); font-size: .9rem; }
    .lista { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: .6rem; }
    .lista li { padding: .9rem 1.1rem; }
    .no-leida { box-shadow: inset 3px 0 0 var(--color-primario); }
    .mensaje { margin: 0; font-weight: 600; }
    .comentario { margin: .35rem 0 0; color: var(--color-texto-suave); overflow-wrap: anywhere; }
    footer { display: flex; gap: 1rem; flex-wrap: wrap; align-items: center; margin-top: .5rem; font-size: .82rem; color: var(--color-texto-suave); }
    footer button { padding: .2rem .65rem; font-size: .8rem; }
    .vacio { color: var(--color-texto-suave); }
  `,
})
export class NotificacionesComponent {
  private readonly servicio = inject(InteraccionesService);
  private readonly pendientes = inject(NotificacionesPendientes);

  readonly bandeja = signal<BandejaNotificaciones | null>(null);
  readonly error = signal('');
  readonly marcando = signal<number | null>(null);
  soloNoLeidas = false;

  constructor() {
    this.cargar();
  }

  cargar(): void {
    this.error.set('');
    this.servicio.notificaciones(this.soloNoLeidas).subscribe({
      next: bandeja => {
        this.bandeja.set(bandeja);
        this.pendientes.noLeidas.set(bandeja.noLeidas);
      },
      error: (e: HttpErrorResponse) => this.error.set(e.status === 0
        ? 'No se pudo conectar con el servidor. Compruebe la conexión e intente de nuevo.'
        : e.error?.mensaje ?? 'No se pudieron cargar las notificaciones.'),
    });
  }

  marcar(notificacion: Notificacion): void {
    this.marcando.set(notificacion.id);
    this.servicio.marcarLeida(notificacion.id)
      .pipe(finalize(() => this.marcando.set(null)))
      .subscribe({
        next: () => this.cargar(),
        error: (e: HttpErrorResponse) => this.error.set(e.error?.mensaje ?? 'No se pudo marcar la notificación.'),
      });
  }
}
