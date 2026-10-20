import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';

export function normalizarOpcion(texto: string): string {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().replace(/\s+/g, ' ').toLocaleLowerCase('es');
}

/** Selector con búsqueda y selección explícita, con navegación por teclado. */
@Component({
  selector: 'app-combobox', standalone: true,
  template: `
    @if (multiple() && seleccion().length) {
      <div class="seleccionadas" aria-label="Categorías seleccionadas">
        @for (valor of seleccion(); track valor) {
          <span class="chip">{{ valor }}
            <button type="button" [disabled]="disabled()" [attr.aria-label]="'Quitar ' + valor" (click)="quitar(valor)">×</button>
          </span>
        }
      </div>
    }
    <div class="control" (focusout)="salir($event)">
      <div class="entrada">
        <input [id]="id()" type="text" role="combobox" autocomplete="off" [disabled]="disabled()"
               [value]="abierto() ? busqueda() : multiple() ? '' : textoSeleccion()"
               [placeholder]="placeholder()" [attr.aria-expanded]="abierto()"
               aria-autocomplete="list" [attr.aria-controls]="abierto() ? id() + '-opciones' : null"
               [attr.aria-activedescendant]="abierto() && filtradas().length && activa() >= 0 ? id() + '-opcion-' + activa() : null"
               (click)="abrir()" (input)="buscar($any($event.target).value)" (keydown)="tecla($event)" />
        @if (!multiple() && seleccion().length) {
          <button type="button" class="limpiar" [disabled]="disabled()" aria-label="Quitar categoría seleccionada" (click)="limpiar()">×</button>
        }
        <button type="button" class="desplegar" [disabled]="disabled()" [attr.aria-label]="abierto() ? 'Cerrar categorías' : 'Mostrar categorías'"
                [attr.aria-controls]="id() + '-opciones'" [attr.aria-expanded]="abierto()" (click)="alternar()">▾</button>
      </div>
      @if (abierto()) {
        <div class="desplegable">
          <div [id]="id() + '-opciones'" role="listbox" aria-label="Categorías disponibles" [attr.aria-multiselectable]="multiple() ? true : null">
            @for (opcion of filtradas(); track opcion; let i = $index) {
              <button type="button" role="option" tabindex="-1" [id]="id() + '-opcion-' + i"
                      [attr.aria-selected]="estaSeleccionada(opcion)" [class.activa]="activa() === i" [disabled]="disabled()"
                      (pointerdown)="$event.preventDefault()" (click)="elegir(opcion)">
                <span>{{ opcion }}</span><span aria-hidden="true">{{ estaSeleccionada(opcion) ? '✓' : '' }}</span>
              </button>
            }
          </div>
          @if (!filtradas().length) { <p role="status">{{ opciones().length ? 'No hay categorías que coincidan con la búsqueda.' : 'Todavía no hay categorías registradas.' }}</p> }
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .control { position: relative; }
    .entrada { display: flex; border: 1px solid var(--color-borde-fuerte); border-radius: var(--radio-chico); background: var(--color-superficie); }
    .entrada:focus-within { box-shadow: var(--foco); border-color: var(--color-primario); }
    .entrada input { flex: 1; min-width: 0; width: 0; border: 0; background: transparent; box-shadow: none; }
    .entrada button { flex: 0 0 38px; padding: 0; border: 0; border-radius: 4px; background: transparent; color: var(--color-texto-suave); }
    .entrada button:hover { background: var(--color-primario-claro); }
    .desplegable { position: absolute; z-index: 20; top: calc(100% + 4px); left: 0; right: 0; max-height: 240px; overflow-y: auto; padding: .3rem; background: var(--color-superficie); border: 1px solid var(--color-borde-fuerte); border-radius: var(--radio-chico); box-shadow: var(--sombra-2); }
    [role=option] { display: flex; width: 100%; gap: .5rem; justify-content: space-between; text-align: left; white-space: normal; overflow-wrap: anywhere; border: 0; border-radius: 4px; padding: .55rem .7rem; color: var(--color-texto); background: transparent; font-weight: 400; }
    [role=option]:hover, [role=option].activa { background: var(--color-primario-claro); color: var(--color-primario-oscuro); }
    [aria-selected=true] { font-weight: 600; }
    .desplegable p { margin: .5rem; font-size: .85rem; color: var(--color-texto-suave); }
    .seleccionadas { display: flex; flex-wrap: wrap; gap: .4rem; margin-bottom: .4rem; }
    .chip { display: inline-flex; align-items: center; gap: .3rem; padding: .2rem .3rem .2rem .6rem; background: var(--color-primario-claro); border-radius: 6px; font-size: .85rem; }
    .chip button { border: 0; padding: .1rem .3rem; background: transparent; color: var(--color-primario-oscuro); }
  `,
})
export class ComboboxComponent {
  readonly id = input.required<string>();
  readonly opciones = input<string[]>([]);
  readonly seleccion = input<string[]>([]);
  readonly multiple = input(false);
  readonly disabled = input(false);
  readonly placeholder = input('Buscar o seleccionar categoría');
  readonly seleccionChange = output<string[]>();
  readonly abierto = signal(false);
  readonly busqueda = signal('');
  readonly activa = signal(-1);
  readonly textoSeleccion = computed(() => this.seleccion().at(0) ?? '');
  readonly filtradas = computed(() => this.opciones().filter(opcion => normalizarOpcion(opcion).includes(normalizarOpcion(this.busqueda()))));
  private readonly elemento = inject(ElementRef<HTMLElement>);

  abrir() { if (this.disabled() || this.abierto()) return; this.busqueda.set(''); this.activa.set(-1); this.abierto.set(true); }
  buscar(texto: string) { this.busqueda.set(texto); this.abierto.set(true); this.activa.set(-1); }
  alternar() { if (this.abierto()) this.abierto.set(false); else { this.abrir(); this.enfocar(); } }
  estaSeleccionada(opcion: string) { return this.seleccion().some(valor => normalizarOpcion(valor) === normalizarOpcion(opcion)); }
  elegir(opcion: string) {
    if (this.disabled()) return;
    if (this.multiple()) {
      this.seleccionChange.emit(this.estaSeleccionada(opcion)
        ? this.seleccion().filter(valor => normalizarOpcion(valor) !== normalizarOpcion(opcion))
        : [...this.seleccion(), opcion]);
      this.busqueda.set(''); this.activa.set(-1);
    } else { this.seleccionChange.emit([opcion]); this.abierto.set(false); }
    this.enfocar();
  }
  quitar(opcion: string) { this.seleccionChange.emit(this.seleccion().filter(valor => valor !== opcion)); }
  limpiar() { this.seleccionChange.emit([]); this.abierto.set(false); this.enfocar(); }
  salir(evento: FocusEvent) { if (!this.elemento.nativeElement.contains(evento.relatedTarget as Node | null)) this.abierto.set(false); }
  @HostListener('document:pointerdown', ['$event'])
  fuera(evento: Event) { if (!this.elemento.nativeElement.contains(evento.target as Node)) this.abierto.set(false); }
  tecla(evento: KeyboardEvent) {
    if (evento.key === 'Escape') { if (this.abierto()) { evento.preventDefault(); this.abierto.set(false); } return; }
    if (evento.key === 'ArrowDown' || evento.key === 'ArrowUp') {
      evento.preventDefault(); if (!this.abierto()) this.abrir();
      const total = this.filtradas().length;
      if (total) this.activa.set(evento.key === 'ArrowDown' ? (this.activa() + 1) % total : (this.activa() < 0 ? total - 1 : (this.activa() + total - 1) % total));
      requestAnimationFrame(() => this.elemento.nativeElement.querySelector('.activa')?.scrollIntoView({ block: 'nearest' }));
    }
    if (evento.key === 'Enter' && this.abierto()) {
      evento.preventDefault(); const opcion = this.filtradas()[this.activa()]; if (opcion) this.elegir(opcion);
    }
  }
  private enfocar() { this.elemento.nativeElement.querySelector('input')?.focus(); }
}
