import { Injectable, inject, signal } from '@angular/core';
import { InteraccionesService } from './interacciones.service';

/** Cuántas notificaciones sin leer tiene el usuario (SCRUM-459), para el aviso de la barra superior. */
@Injectable({ providedIn: 'root' })
export class NotificacionesPendientes {
  private readonly servicio = inject(InteraccionesService);

  readonly noLeidas = signal(0);

  /** Si falla, conserva el último valor: el aviso no debe interrumpir el trabajo. */
  actualizar(): void {
    this.servicio.notificaciones(true).subscribe({
      next: bandeja => this.noLeidas.set(bandeja.noLeidas),
      error: () => undefined,
    });
  }
}
