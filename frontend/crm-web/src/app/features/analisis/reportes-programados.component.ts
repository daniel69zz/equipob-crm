import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { descargarArchivo } from './exportar-reporte.component';
import { ConfiguracionReporte, EjecucionReporte, ProgramacionReporte, ReportesProgramadosService } from './reportes-programados.service';

@Component({
  selector: 'app-reportes-programados', standalone: true, imports: [DatePipe, FormsModule],
  template: `
    <h1>Reportes programados</h1>
    <p class="subtitulo">Reciba aquí sus reportes de comportamiento de compra en CSV o PDF y los avisos de cada ejecución.</p>
    <p>La hora se interpreta en Bolivia (America/La_Paz). Cada archivo incluye los días completos anteriores a su fecha programada.</p>
    @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    @if (mensaje()) { <p role="status">{{ mensaje() }}</p> }
    <form class="tarjeta filtros" #form="ngForm" (ngSubmit)="guardar()">
      <h2>{{ editando ? 'Editar programación' : 'Nueva programación' }}</h2>
      <label>Nombre<input name="nombre" [(ngModel)]="modelo.nombre" required maxlength="120" /></label>
      <label>Frecuencia<select name="frecuencia" [(ngModel)]="modelo.frecuencia">
        <option value="DIARIA">Diaria</option><option value="SEMANAL">Semanal</option><option value="MENSUAL">Mensual</option>
      </select></label>
      <label>Formato<select name="formato" [(ngModel)]="modelo.formato"><option>CSV</option><option>PDF</option></select></label>
      <label>Días del reporte<input type="number" name="dias" [(ngModel)]="modelo.dias" min="1" max="1830" required /></label>
      <label>Segmento (vacío: toda la cartera)<input name="segmento" [(ngModel)]="modelo.segmento" maxlength="60" /></label>
      <label>Inicio<input type="datetime-local" name="inicio" [(ngModel)]="inicio" required /></label>
      <label>Fin (opcional)<input type="datetime-local" name="fin" [(ngModel)]="fin" /></label>
      <div class="acciones"><button type="submit" [disabled]="form.invalid || ocupado()">{{ editando ? 'Guardar cambios' : 'Programar reporte' }}</button>
      @if (editando) { <button type="button" class="secundario" (click)="limpiar()">Salir de edición</button> }</div>
    </form>
    <h2>Mis programaciones</h2>
    @for (p of programaciones(); track p.id) {
      <article class="tarjeta">
        <h3>{{ p.nombre }}</h3>
        <p>{{ p.frecuencia }} · {{ p.formato }} · {{ p.dias }} días · {{ p.segmento || 'Toda la cartera' }} · {{ p.estado }}</p>
        <p>Próxima fecha: {{ p.proxima | date:'dd/MM/yyyy HH:mm':'-0400' }}</p>
        @if (p.estado !== 'CANCELADA') {
          <div class="acciones"><button class="secundario" (click)="editar(p)" [disabled]="ocupado()">Editar</button>
          <button class="secundario" (click)="estado(p, p.estado === 'ACTIVA' ? 'PAUSADA' : 'ACTIVA')" [disabled]="ocupado()">{{ p.estado === 'ACTIVA' ? 'Pausar' : 'Activar' }}</button>
          <button class="secundario" (click)="estado(p, 'CANCELADA')" [disabled]="ocupado()">Cancelar programación</button></div>
        }
      </article>
    } @empty { <p>No tiene reportes programados.</p> }
    <h2>Avisos e historial de ejecuciones</h2>
    <button class="secundario" (click)="cargar()">Actualizar</button>
    <div aria-live="polite">
      @for (e of ejecuciones(); track e.id) {
        <article class="tarjeta" [class.pendiente]="!e.leida">
          <h3>{{ e.reporte }} · {{ e.estado === 'COMPLETADA' ? 'Reporte disponible' : 'No se pudo generar el reporte' }}</h3>
          <p>Programado: {{ e.prevista | date:'dd/MM/yyyy HH:mm':'-0400' }} · Ejecutado: {{ e.ejecutada | date:'dd/MM/yyyy HH:mm':'-0400' }}</p>
          @if (e.causa) { <p class="error">{{ e.causa }}</p> }
          <div class="acciones">@if (e.estado === 'COMPLETADA') { <button (click)="descargar(e)" [disabled]="ocupado()">Descargar {{ e.nombre }}</button> }
          @if (!e.leida) { <button class="secundario" (click)="leer(e)" [disabled]="ocupado()">Marcar aviso leído</button> }</div>
        </article>
      } @empty { <p>No hay ejecuciones en esta página.</p> }
    </div>
    <div class="acciones"><button class="secundario" (click)="pagina = pagina - 1; cargar()" [disabled]="pagina === 0">Anterior</button>
    <span>Página {{ pagina + 1 }}</span><button class="secundario" (click)="pagina = pagina + 1; cargar()" [disabled]="ejecuciones().length < 50">Siguiente</button></div>
  `,
  styles: `
    .filtros { display:grid; grid-template-columns:repeat(auto-fit,minmax(220px,1fr)); gap:1rem; }
    .filtros h2 { grid-column:1/-1; } label { display:flex; flex-direction:column; gap:.4rem; }
    article { margin:1rem 0; padding:1rem; overflow-wrap:anywhere; } h3 { margin-top:0; }
    .acciones { display:flex; gap:.6rem; flex-wrap:wrap; align-items:center; }
    .pendiente { border-left:4px solid var(--color-primario); }
  `,
})
export class ReportesProgramadosComponent implements OnInit {
  private readonly servicio = inject(ReportesProgramadosService);
  readonly programaciones = signal<ProgramacionReporte[]>([]);
  readonly ejecuciones = signal<EjecucionReporte[]>([]);
  readonly error = signal(''); readonly mensaje = signal(''); readonly ocupado = signal(false);
  editando: string | null = null; pagina = 0; inicio = ''; fin = '';
  modelo: ConfiguracionReporte = this.nuevo();
  constructor() { timer(30000, 30000).pipe(takeUntilDestroyed()).subscribe(() => this.cargar()); }
  ngOnInit() { this.cargar(); }
  private nuevo(): ConfiguracionReporte { return { nombre: '', frecuencia: 'SEMANAL', formato: 'CSV', dias: 7, segmento: '', inicio: '', fin: null }; }
  cargar() {
    this.servicio.listar().subscribe({ next: p => this.programaciones.set(p), error: e => this.fallo(e) });
    this.servicio.historial(this.pagina).subscribe({ next: e => this.ejecuciones.set(e), error: e => this.fallo(e) });
  }
  guardar() {
    const inicio = this.inicio + ':00-04:00'; const fin = this.fin ? this.fin + ':00-04:00' : null;
    if (!this.modelo.nombre.trim() || !this.inicio || Date.parse(inicio) <= Date.now() || (fin && Date.parse(fin) < Date.parse(inicio))) {
      this.error.set('Complete el nombre, un inicio futuro y un fin posterior al inicio.'); return;
    }
    this.ocupado.set(true); this.error.set('');
    this.servicio.guardar(this.editando, { ...this.modelo, segmento: this.modelo.segmento?.trim() || null, inicio, fin }).subscribe({
      next: () => { this.ocupado.set(false); this.limpiar(); this.mensaje.set('Programación guardada.'); this.cargar(); }, error: e => this.fallo(e),
    });
  }
  editar(p: ProgramacionReporte) {
    this.editando = p.id; this.modelo = { ...p };
    const local = (fecha: string) => new Date(Date.parse(fecha) - 4 * 3600000).toISOString().slice(0,16);
    this.inicio = local(p.inicio); this.fin = p.fin ? local(p.fin) : '';
    this.error.set(''); this.mensaje.set('Para editar, seleccione el nuevo inicio futuro.');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  limpiar() { this.editando = null; this.modelo = this.nuevo(); this.inicio = ''; this.fin = ''; }
  estado(p: ProgramacionReporte, estado: string) {
    this.ocupado.set(true); this.error.set('');
    this.servicio.estado(p.id, estado).subscribe({ next: () => { this.ocupado.set(false); this.cargar(); }, error: e => this.fallo(e) });
  }
  leer(e: EjecucionReporte) {
    this.ocupado.set(true);
    this.servicio.leer(e.id).subscribe({ next: () => { this.ocupado.set(false); this.cargar(); }, error: e => this.fallo(e) });
  }
  descargar(e: EjecucionReporte) {
    this.ocupado.set(true);
    this.servicio.archivo(e.id).subscribe({ next: r => { descargarArchivo(r, e.nombre || 'reporte'); this.ocupado.set(false); }, error: err => this.fallo(err) });
  }
  private fallo(e: { status?: number; error?: { mensaje?: string; detail?: string } }) {
    this.ocupado.set(false);
    this.error.set(e.status === 403 ? 'Su rol no tiene permiso para programar y descargar reportes.' : e.error?.mensaje || e.error?.detail || 'No se pudo completar la operación. Intente nuevamente.');
  }
}
