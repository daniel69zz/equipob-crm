import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, combineLatest, finalize, of, startWith, switchMap } from 'rxjs';
import { SiTienePermisoDirective } from '../../core/auth/si-tiene-permiso.directive';
import { Permisos } from '../../core/auth/sesion';
import { ConsultaSegmentos, ConsultaSegmentosService, PaginaSegmentados, SegmentoDisponible } from './consulta-segmentos.service';

@Component({
  selector: 'app-consulta-segmentos',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, SiTienePermisoDirective],
  templateUrl: './consulta-segmentos.component.html',
  styleUrl: './consulta-segmentos.component.css',
})
export class ConsultaSegmentosComponent implements OnInit {
  private readonly servicio = inject(ConsultaSegmentosService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refrescar = new Subject<void>();
  readonly permisos = Permisos;
  readonly segmentos = signal<SegmentoDisponible[]>([]);
  readonly cargandoCatalogo = signal(false);
  readonly errorCatalogo = signal('');
  readonly resultado = signal<PaginaSegmentados | null>(null);
  readonly cargando = signal(false);
  readonly error = signal('');
  readonly consultado = signal(false);
  readonly columnas = [
    { clave: 'nombre', titulo: 'Cliente' }, { clave: 'segmento', titulo: 'Segmento' },
    { clave: 'nivelConsumo', titulo: 'Nivel de consumo' }, { clave: 'ultimaCompra', titulo: 'Última compra' },
  ];
  seleccion = '';
  nivel = '';
  nombre = '';
  tamanio = '20';
  aplicada: ConsultaSegmentos = {};

  ngOnInit(): void {
    this.cargarCatalogo();
    combineLatest([this.ruta.queryParamMap, this.refrescar.pipe(startWith(undefined))]).pipe(
      switchMap(([params]) => {
        this.aplicada = {};
        for (const clave of ['segmentoId', 'segmento', 'nivelConsumo', 'nombre', 'pagina', 'tamanio', 'orden', 'direccion']) {
          if (params.has(clave)) this.aplicada[clave] = params.get(clave)!;
        }
        this.seleccion = params.get('segmentoId') ? 'id:' + params.get('segmentoId')
          : params.get('segmento') ? 'nombre:' + params.get('segmento') : '';
        this.nivel = params.get('nivelConsumo') ?? '';
        this.nombre = params.get('nombre') ?? '';
        this.tamanio = params.get('tamanio') ?? '20';
        this.resultado.set(null);
        this.error.set('');
        const consultar = !!(this.seleccion || this.nivel);
        this.consultado.set(consultar);
        this.cargando.set(consultar);
        if (!consultar) return of(null);
        return this.servicio.buscar(this.aplicada).pipe(
          catchError((e: HttpErrorResponse) => {
            this.error.set(e.status === 403 ? 'No tiene permiso para consultar clientes por segmento.'
              : e.error?.mensaje ?? 'No se pudo consultar los clientes. Intente nuevamente.');
            return of(null);
          }),
          finalize(() => this.cargando.set(false)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(resultado => this.resultado.set(resultado));
  }

  cargarCatalogo(): void {
    this.cargandoCatalogo.set(true);
    this.errorCatalogo.set('');
    this.servicio.catalogo().pipe(takeUntilDestroyed(this.destroyRef),
      finalize(() => this.cargandoCatalogo.set(false))).subscribe({
      next: segmentos => this.segmentos.set(segmentos),
      error: () => this.errorCatalogo.set('No se pudieron cargar los segmentos. Puede reintentar o consultar por nivel.'),
    });
  }

  clave(segmento: SegmentoDisponible): string {
    return segmento.id !== null ? 'id:' + segmento.id : 'nombre:' + segmento.nombre;
  }

  consultar(): void {
    if (!this.seleccion && !this.nivel) {
      this.error.set('Seleccione un segmento o un nivel de consumo.');
      return;
    }
    const query: ConsultaSegmentos = {
      nivelConsumo: this.nivel, nombre: this.nombre.trim(), pagina: '0', tamanio: this.tamanio,
      orden: this.aplicada['orden'] ?? 'nombre', direccion: this.aplicada['direccion'] ?? 'asc',
    };
    if (this.seleccion.startsWith('id:')) query['segmentoId'] = this.seleccion.slice(3);
    if (this.seleccion.startsWith('nombre:')) query['segmento'] = this.seleccion.slice(7);
    this.navegar(query);
  }

  limpiar(): void { this.navegar({}); }

  reintentar(): void {
    this.refrescar.next();
  }

  pagina(numero: number): void { this.navegar({ ...this.aplicada, pagina: String(numero) }); }
  cambiarTamanio(): void {
    if (this.consultado()) this.navegar({ ...this.aplicada, tamanio: this.tamanio, pagina: '0' });
  }
  ordenar(columna: string): void {
    const direccion = this.aplicada['orden'] === columna && this.aplicada['direccion'] !== 'desc' ? 'desc' : 'asc';
    this.navegar({ ...this.aplicada, orden: columna, direccion, pagina: '0' });
  }
  ordenAria(columna: string): string {
    return (this.aplicada['orden'] ?? 'nombre') !== columna ? 'none'
      : this.aplicada['direccion'] === 'desc' ? 'descending' : 'ascending';
  }
  etiquetaNivel(nivel: string | null): string {
    return nivel === 'ALTO' ? 'Alto' : nivel === 'MEDIO' ? 'Medio' : nivel === 'BAJO' ? 'Bajo' : 'Sin nivel';
  }
  fin(r: PaginaSegmentados): number { return Math.min((r.pagina + 1) * r.tamanio, r.total); }
  private navegar(queryParams: ConsultaSegmentos): void {
    const limpios = Object.fromEntries(Object.entries(queryParams).filter(([, valor]) => valor !== ''));
    const claves = Object.keys(limpios);
    if (claves.length === Object.keys(this.aplicada).length && claves.every(k => limpios[k] === this.aplicada[k])) {
      this.refrescar.next();
    } else {
      void this.router.navigate([], { relativeTo: this.ruta, queryParams: limpios });
    }
  }
}
