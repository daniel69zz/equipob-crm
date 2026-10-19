import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subject, catchError, distinctUntilChanged, map, of, startWith, switchMap } from 'rxjs';
import { PestaniasClienteComponent } from './pestanias-cliente.component';
import { PerfilPreferencias, PreferenciasService } from './preferencias.service';

type EstadoConsulta =
  | { tipo: 'cargando' }
  | { tipo: 'resultado'; perfil: PerfilPreferencias }
  | { tipo: 'error'; mensaje: string; reintentable: boolean };

@Component({
  selector: 'app-preferencias',
  standalone: true,
  imports: [DatePipe, DecimalPipe, PestaniasClienteComponent],
  templateUrl: './preferencias.component.html',
  styleUrl: './preferencias.component.css',
})
export class PreferenciasComponent {
  private readonly servicio = inject(PreferenciasService);
  private readonly recargas = new Subject<void>();
  readonly clienteId = signal('');
  readonly estado = signal<EstadoConsulta>({ tipo: 'cargando' });

  constructor() {
    inject(ActivatedRoute).paramMap.pipe(
      map(params => params.get('id') ?? ''),
      distinctUntilChanged(),
      switchMap(id => {
        this.clienteId.set(id);
        // Cambiar de cliente cancela también una consulta o recarga en curso.
        const numero = Number(id);
        if (!/^\d+$/.test(id) || !Number.isSafeInteger(numero) || numero <= 0) {
          return of<EstadoConsulta>({ tipo: 'error', mensaje: 'El identificador del cliente no es válido.', reintentable: false });
        }
        return this.recargas.pipe(
          startWith(undefined),
          switchMap(() => this.servicio.consultar(numero).pipe(
            map(perfil => ({ tipo: 'resultado', perfil }) as EstadoConsulta),
            catchError((error: HttpErrorResponse) => of(this.describirError(error))),
            startWith<EstadoConsulta>({ tipo: 'cargando' }),
          )),
        );
      }),
      takeUntilDestroyed(),
    ).subscribe(estado => this.estado.set(estado));
  }

  consultar(): void { this.recargas.next(); }

  private describirError(error: HttpErrorResponse): EstadoConsulta {
    const mensajes: Record<number, string> = {
      400: 'El identificador del cliente no es válido.',
      401: 'La sesión ha vencido. Inicie sesión nuevamente.',
      403: 'No tiene permiso para consultar las preferencias de este cliente.',
      404: 'No se encontró el cliente solicitado.',
      409: 'El cliente fue consolidado. Consulte su perfil vigente.',
    };
    const detalle = typeof error.error?.mensaje === 'string' ? error.error.mensaje : null;
    return {
      tipo: 'error',
      mensaje: mensajes[error.status] ? (error.status === 409 ? detalle ?? mensajes[409] : mensajes[error.status])
        : 'No se pudieron consultar las preferencias. Intente nuevamente.',
      reintentable: error.status === 0 || error.status >= 500,
    };
  }
}
