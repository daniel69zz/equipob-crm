import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { PestaniasClienteComponent } from './pestanias-cliente.component';
import { CambioSegmento, HistorialSegmentosService, PaginaSegmentos, ReglaHistorica } from './historial-segmentos.service';

@Component({
  selector: 'app-historial-segmentos',
  standalone: true,
  imports: [DatePipe, PestaniasClienteComponent],
  templateUrl: './historial-segmentos.component.html',
  styleUrl: './historial-segmentos.component.css',
})
export class HistorialSegmentosComponent {
  private readonly servicio = inject(HistorialSegmentosService);
  private readonly destroyRef = inject(DestroyRef);
  private peticion?: Subscription;
  readonly id = input.required<string>();
  readonly historial = signal<PaginaSegmentos | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);
  private ultimaPagina = 0;

  constructor() {
    // La ruta puede reutilizar el componente al navegar a otro cliente.
    effect(() => { this.id(); this.buscar(0); }, { allowSignalWrites: true });
  }

  buscar(pagina: number): void {
    this.peticion?.unsubscribe();
    this.ultimaPagina = pagina;
    this.historial.set(null);
    this.error.set(null);
    if (!/^[1-9]\d*$/.test(this.id())) {
      this.cargando.set(false);
      this.error.set('El identificador del cliente no es válido.');
      return;
    }
    this.cargando.set(true);
    this.peticion = this.servicio.consultar(this.id(), pagina).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: datos => { this.historial.set(datos); this.cargando.set(false); },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.status === 403 ? 'No tiene permiso para consultar el historial de segmentos.'
          : e.error?.mensaje ?? 'No se pudo consultar el historial de segmentos. Intente nuevamente.');
      },
    });
  }

  reintentar(): void { this.buscar(this.ultimaPagina); }

  operacion(cambio: CambioSegmento): string {
    return ({ SEGMENTO_ASIGNADO: 'Primera asignación', SEGMENTO_CAMBIADO: 'Cambio de segmento',
      SEGMENTO_RETIRADO: 'Retiro de segmento' } as Record<string, string>)[cambio.operacion] ?? 'Cambio de segmento';
  }

  regla(regla: ReglaHistorica | null): string {
    if (!regla) return 'Regla no disponible';
    const indicador = ({ FRECUENCIA: 'Frecuencia de compra', VALOR: 'Valor acumulado', CATEGORIA: 'Categoría',
      CLASIFICACION_FRECUENCIA: 'Clasificación por frecuencia' } as Record<string, string>)[regla.indicador ?? '']
      ?? 'Criterio';
    let condicion = '';
    switch (regla.condicion) {
      case 'ENTRE': condicion = `entre ${regla.minimo} y ${regla.maximo}`; break;
      case 'MAYOR_IGUAL': condicion = `mayor o igual a ${regla.minimo}`; break;
      case 'MENOR_IGUAL': condicion = `menor o igual a ${regla.maximo}`; break;
      case 'IGUAL': condicion = `igual a ${regla.clasificacion ?? regla.minimo}`; break;
      case 'CONTIENE': condicion = `incluye ${regla.categoria}`; break;
    }
    return `${indicador}${condicion ? ': ' + condicion : ''}`;
  }
}
