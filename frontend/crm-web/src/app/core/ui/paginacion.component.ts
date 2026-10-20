import { Component, computed, input, output } from '@angular/core';

/**
 * Paginación de un listado (SCRUM-275): rango mostrado, total, página actual y botones Anterior y
 * Siguiente. Recibe la página en base 0 tal como la devuelve el backend y emite la página pedida;
 * el listado decide cómo consultarla. Sin resultados no se muestra.
 */
@Component({
  selector: 'app-paginacion',
  standalone: true,
  template: `
    @if (total() > 0) {
      <nav class="paginacion" aria-label="Paginación">
        <span>{{ desde() }}–{{ hasta() }} de {{ total() }}</span>
        <span class="pagina">Página {{ pagina() + 1 }} de {{ paginas() }}</span>
        <button type="button" class="secundario" [disabled]="pagina() === 0 || cargando()" (click)="cambiar.emit(pagina() - 1)">
          Anterior
        </button>
        <button type="button" class="secundario" [disabled]="esUltima() || cargando()" (click)="cambiar.emit(pagina() + 1)">
          Siguiente
        </button>
      </nav>
    }
  `,
  styles: `
    .pagina { padding: 0 0.4rem; border-left: 1px solid var(--color-borde-fuerte); }
  `,
})
export class PaginacionComponent {
  /** Página actual, empezando en 0. */
  readonly pagina = input.required<number>();
  readonly tamanio = input.required<number>();
  readonly total = input.required<number>();
  /** Mientras se consulta, los botones quedan deshabilitados para no pedir dos páginas a la vez. */
  readonly cargando = input(false);
  readonly cambiar = output<number>();

  readonly desde = computed(() => this.pagina() * this.tamanio() + 1);
  readonly hasta = computed(() => Math.min((this.pagina() + 1) * this.tamanio(), this.total()));
  readonly paginas = computed(() => Math.max(1, Math.ceil(this.total() / this.tamanio())));
  readonly esUltima = computed(() => this.pagina() + 1 >= this.paginas());
}
