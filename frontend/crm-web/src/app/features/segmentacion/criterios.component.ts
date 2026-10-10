import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { CambioCriterio, Caso, Comprobacion, Condicion, Criterio, CriteriosService, INDICADORES, CONDICIONES, Indicador, reglaTexto, SolicitudCriterio } from './criterios.service';

@Component({
  selector: 'app-criterios', standalone: true, imports: [FormsModule, DatePipe],
  templateUrl: './criterios.component.html', styleUrl: './criterios.component.css',
})
export class CriteriosComponent {
  private readonly api = inject(CriteriosService);
  readonly indicadores = INDICADORES;
  readonly condiciones = CONDICIONES;
  readonly regla = reglaTexto;
  readonly clavesIndicador: Indicador[] = ['FRECUENCIA', 'VALOR', 'CATEGORIA'];
  readonly numericas: Condicion[] = ['ENTRE', 'MAYOR_IGUAL', 'MENOR_IGUAL', 'IGUAL'];
  readonly criterios = signal<Criterio[]>([]);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal('');
  readonly exito = signal('');
  readonly errorFormulario = signal('');
  readonly abierto = signal(false);
  readonly editando = signal<Criterio | null>(null);
  readonly estadoPendiente = signal<Criterio | null>(null);
  readonly seleccionado = signal<Criterio | null>(null);
  readonly historial = signal<CambioCriterio[]>([]);
  readonly cargandoHistorial = signal(false);
  readonly errorHistorial = signal('');
  readonly resultado = signal<Comprobacion | null>(null);
  readonly comprobando = signal(false);
  readonly errorCaso = signal('');
  readonly busqueda = signal('');
  readonly filtroIndicador = signal('');
  readonly filtroEstado = signal('');
  readonly activos = computed(() => this.criterios().filter(c => c.activo).length);
  readonly visibles = computed(() => this.criterios().filter(c =>
    c.nombre.toLocaleLowerCase().includes(this.busqueda().trim().toLocaleLowerCase()) &&
    (!this.filtroIndicador() || c.indicador === this.filtroIndicador()) &&
    (!this.filtroEstado() || String(c.activo) === this.filtroEstado())));
  formulario: SolicitudCriterio = this.vacio();
  valorCaso: number | null = null;
  categoriasCaso = '';
  private consultaHistorial = 0;

  constructor() { this.cargar(); }

  cargar() {
    this.cargando.set(true); this.error.set('');
    this.api.listar().pipe(finalize(() => this.cargando.set(false))).subscribe({
      next: datos => this.criterios.set(datos), error: e => this.error.set(this.mensaje(e)),
    });
  }
  nuevo() {
    this.editando.set(null); this.formulario = this.vacio(); this.errorFormulario.set(''); this.abierto.set(true);
  }
  editar(c: Criterio) {
    this.editando.set(c);
    this.formulario = { nombre: c.nombre, descripcion: c.descripcion, indicador: c.indicador,
      condicion: c.condicion, minimo: c.minimo, maximo: c.condicion === 'IGUAL' ? null : c.maximo,
      categoria: c.categoria, version: c.version };
    this.errorFormulario.set(''); this.abierto.set(true);
  }
  cambiarIndicador() {
    this.formulario.condicion = this.formulario.indicador === 'CATEGORIA' ? 'CONTIENE' : 'ENTRE';
    this.cambiarCondicion(); this.formulario.categoria = null;
  }
  cambiarCondicion() { this.formulario.minimo = null; this.formulario.maximo = null; }
  guardar() {
    this.errorFormulario.set('');
    if (!this.formulario.nombre.trim()) { this.errorFormulario.set('Indique el nombre del criterio.'); return; }
    if (this.formulario.indicador === 'CATEGORIA' && !this.formulario.categoria?.trim()) {
      this.errorFormulario.set('Indique la categoría consumida.'); return;
    }
    if (this.formulario.indicador !== 'CATEGORIA' &&
        ((this.formulario.condicion !== 'MENOR_IGUAL' && this.formulario.minimo === null) ||
         (['ENTRE', 'MENOR_IGUAL'].includes(this.formulario.condicion) && this.formulario.maximo === null))) {
      this.errorFormulario.set('Complete los límites de la condición.'); return;
    }
    this.guardando.set(true); this.exito.set('');
    const editado = this.editando();
    const solicitud = editado ? this.api.editar(editado.id, this.formulario) : this.api.crear(this.formulario);
    solicitud.pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: c => { this.actualizar(c); this.abierto.set(false); this.exito.set(`Criterio «${c.nombre}» ${editado ? 'actualizado' : 'creado'}.`); this.verDetalle(c); },
      error: e => this.errorFormulario.set(this.mensaje(e)),
    });
  }
  confirmarEstado() {
    const c = this.estadoPendiente(); if (!c) return;
    this.guardando.set(true); this.error.set(''); this.exito.set('');
    this.api.estado(c, !c.activo).pipe(finalize(() => this.guardando.set(false))).subscribe({
      next: nuevo => { this.actualizar(nuevo); this.estadoPendiente.set(null);
        this.exito.set(`Criterio «${nuevo.nombre}» ${nuevo.activo ? 'activado' : 'desactivado'}.`); this.verDetalle(nuevo); },
      error: e => { this.estadoPendiente.set(null); this.error.set(this.mensaje(e)); },
    });
  }
  verDetalle(c: Criterio) {
    const consulta = ++this.consultaHistorial;
    this.seleccionado.set(c); this.resultado.set(null); this.errorCaso.set(''); this.valorCaso = null; this.categoriasCaso = '';
    this.historial.set([]); this.errorHistorial.set(''); this.cargandoHistorial.set(true);
    this.api.historial(c.id).subscribe({
      next: datos => { if (consulta === this.consultaHistorial) { this.historial.set(datos); this.cargandoHistorial.set(false); } },
      error: e => { if (consulta === this.consultaHistorial) { this.errorHistorial.set(this.mensaje(e)); this.cargandoHistorial.set(false); } },
    });
  }
  comprobar() {
    const c = this.seleccionado(); if (!c) return;
    this.resultado.set(null); this.errorCaso.set('');
    const caso: Caso = c.indicador === 'CATEGORIA'
      ? { categorias: this.categoriasCaso.split(',').map(v => v.trim()).filter(Boolean) }
      : c.indicador === 'FRECUENCIA' ? { frecuencia: this.valorCaso } : { valor: this.valorCaso };
    this.comprobando.set(true);
    this.api.comprobar(c.id, caso).pipe(finalize(() => this.comprobando.set(false))).subscribe({
      next: r => { if (this.seleccionado()?.id === c.id) this.resultado.set(r); },
      error: e => { if (this.seleccionado()?.id === c.id) this.errorCaso.set(this.mensaje(e)); },
    });
  }
  operacion(codigo: string) {
    return ({ CRITERIO_CREADO: 'Creación', CRITERIO_ACTUALIZADO: 'Edición', CRITERIO_ACTIVADO: 'Activación',
      CRITERIO_DESACTIVADO: 'Desactivación' } as Record<string, string>)[codigo] ?? codigo;
  }
  private actualizar(c: Criterio) { this.criterios.update(datos => [c, ...datos.filter(x => x.id !== c.id)].sort((a,b) => b.id-a.id)); }
  private mensaje(e: HttpErrorResponse) { return e.error?.mensaje ?? 'No se pudo completar la operación. Compruebe la conexión e intente de nuevo.'; }
  private vacio(): SolicitudCriterio {
    return { nombre: '', descripcion: '', indicador: 'FRECUENCIA', condicion: 'ENTRE', minimo: null, maximo: null, categoria: null };
  }
}
