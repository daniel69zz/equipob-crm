import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Compra } from './compras.service';
import { FichaIntegral, FichaIntegralService } from './ficha-integral.service';
import { PuntosFidelizacionComponent } from './puntos-fidelizacion.component';

const NOMBRES_DE_ESTADO: Record<Compra['estado'], string> = {
  CONFIRMADA: 'Confirmada',
  DEVOLUCION_PARCIAL: 'Devolución parcial',
  ANULADA: 'Anulada',
};

const NOMBRES_DE_TIPO_DIRECCION: Record<string, string> = {
  ENTREGA: 'Entrega',
  FACTURACION: 'Facturación',
  OTRA: 'Otra',
};

/**
 * Ficha integral del cliente (SCRUM-10, SCRUM-150): datos personales, contacto, direcciones,
 * historial de compras, segmento y puntos en una sola pantalla, para resolver la atención sin
 * cambiar de sistema.
 */
@Component({
  selector: 'app-ficha-integral',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, PuntosFidelizacionComponent],
  template: `
    <p><a routerLink="/clientes">← Clientes</a></p>
    <h1>Ficha integral del cliente</h1>

    @if (ficha(); as datos) {
      <section class="tarjeta datos-personales" aria-label="Datos personales y contacto">
        <h2>Datos personales</h2>
        <dl>
          <div>
            <dt>Nombre</dt>
            <dd>{{ datos.datosPersonales.nombres }} {{ datos.datosPersonales.apellidos }}</dd>
          </div>
          <div>
            <dt>Documento</dt>
            <dd>{{ datos.datosPersonales.tipoDocumento }} {{ datos.datosPersonales.numeroDocumento }}</dd>
          </div>
          <div>
            <dt>Correo</dt>
            <dd>{{ datos.datosPersonales.email || '—' }}</dd>
          </div>
          <div>
            <dt>Teléfono</dt>
            <dd>{{ datos.datosPersonales.telefono || '—' }}</dd>
          </div>
        </dl>
      </section>

      <section class="tarjeta direcciones" aria-label="Direcciones">
        <h2>Direcciones</h2>
        @for (direccion of datos.datosPersonales.direcciones; track direccion.idDireccion) {
          <p class="direccion">
            <span class="etiqueta">{{ nombreTipoDireccion(direccion.tipo) }}</span>
            {{ direccion.calle }} {{ direccion.numero }}, {{ direccion.zona }}, {{ direccion.ciudad }}
            @if (direccion.principal) {
              <span class="etiqueta principal">Principal</span>
            }
          </p>
        } @empty {
          <p><small class="sin-datos">Este cliente no tiene direcciones registradas.</small></p>
        }
      </section>

      <section class="tarjeta segmento" aria-label="Segmento">
        <h2>Segmento</h2>
        @if (datos.segmento.sinDatos) {
          <strong class="sin-datos">Sin datos</strong>
          <small>Este cliente aún no tiene un segmento asignado.</small>
        } @else {
          <strong>{{ datos.segmento.segmento }}</strong>
          @if (datos.segmento.asignadoEn) {
            <small>Asignado el {{ datos.segmento.asignadoEn | date: 'dd/MM/yyyy' }}</small>
          }
        }
      </section>

      <app-puntos-fidelizacion [puntos]="datos.puntos" />

      <section class="tarjeta historial" aria-label="Historial de compras">
        <h2>Historial de compras</h2>
        @for (compra of datos.historialCompras; track $index) {
          <article class="compra">
            <header>
              <span class="fecha">{{ compra.fecha | date: 'dd/MM/yyyy HH:mm' }}</span>
              <span class="referencia">{{ compra.referencia }}</span>
              <span class="etiqueta" [class]="'estado-' + compra.estado">{{ nombreEstado(compra.estado) }}</span>
              <strong class="monto">{{ compra.montoTotal | number: '1.2-2' }}</strong>
            </header>
          </article>
        } @empty {
          <p><small class="sin-datos">Este cliente no tiene compras registradas.</small></p>
        }
      </section>
    } @else if (error()) {
      <p class="error" role="status">{{ error() }}</p>
    } @else {
      <p><small>Cargando…</small></p>
    }
  `,
  styles: `
    h2 { margin: 0 0 0.5rem; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--color-texto-suave); }
    section.tarjeta { margin-bottom: 1rem; padding: 1rem 1.25rem; }
    .datos-personales dl { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 0.75rem; margin: 0; }
    .datos-personales dt { color: var(--color-texto-suave); font-size: 0.8rem; }
    .datos-personales dd { margin: 0; }
    .direccion { display: flex; align-items: center; gap: 0.5rem; margin: 0.35rem 0; }
    .segmento { display: flex; flex-direction: column; gap: 0.15rem; }
    .segmento strong { font-size: 1.6rem; }
    .segmento small { color: var(--color-texto-suave); }
    .sin-datos { color: var(--color-texto-suave); }
    .compra { padding: 0.5rem 0; border-bottom: 1px solid var(--color-borde, #e5e5e5); }
    .compra:last-child { border-bottom: none; }
    .compra header { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; }
    .fecha { font-family: monospace; }
    .referencia { color: var(--color-texto-suave); font-family: monospace; }
    .monto { margin-left: auto; }
    .etiqueta { padding: 0.15rem 0.6rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; background: #eef2f7; }
    .etiqueta.principal { color: #0b6e4f; background: #e3f6ee; }
    .estado-CONFIRMADA { color: #0b6e4f; background: #e3f6ee; }
    .estado-DEVOLUCION_PARCIAL { color: #9a6700; background: #fff4e0; }
    .estado-ANULADA { color: var(--color-error); background: #fdeceb; }
  `,
})
export class FichaIntegralComponent implements OnInit {
  private readonly servicio = inject(FichaIntegralService);

  /** Identificador del cliente, tomado de la ruta /clientes/:id/ficha-integral. */
  readonly id = input.required<string>();

  readonly ficha = signal<FichaIntegral | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.cargando.set(true);
    this.error.set(null);
    this.servicio.obtener(Number(this.id())).subscribe({
      next: (ficha) => {
        this.ficha.set(ficha);
        this.cargando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.cargando.set(false);
        this.error.set(e.error?.mensaje ?? 'No se pudo consultar la ficha integral del cliente.');
      },
    });
  }

  nombreEstado(estado: Compra['estado']): string {
    return NOMBRES_DE_ESTADO[estado] ?? estado;
  }

  nombreTipoDireccion(tipo: string): string {
    return NOMBRES_DE_TIPO_DIRECCION[tipo] ?? tipo;
  }
}
