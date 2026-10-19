import { Routes } from '@angular/router';
import { preferenciasGuard } from './core/auth/auth.guards';
import { PreferenciasComponent } from './features/clientes/preferencias.component';
import { administradorSegmentacionGuard, autenticadoGuard, invitadoGuard, permisoGuard } from './core/auth/auth.guards';
import { CriteriosComponent } from './features/segmentacion/criterios.component';
import { Permisos } from './core/auth/sesion';
import { RetencionCohortesComponent } from './features/analisis/retencion-cohortes.component';
import { AccesoDenegadoComponent } from './features/acceso-denegado/acceso-denegado.component';
import { AuditoriaComponent } from './features/auditoria/auditoria.component';
import { BuscarClientesComponent } from './features/clientes/buscar-clientes.component';
import { ClientesInactivosComponent } from './features/clientes/clientes-inactivos.component';
import { ConsentimientoComponent } from './features/clientes/consentimiento.component';
import { EvolucionConsumoComponent } from './features/clientes/evolucion-consumo.component';
import { FichaIntegralComponent } from './features/clientes/ficha-integral.component';
import { HistorialCambiosComponent } from './features/clientes/historial-cambios.component';
import { HistorialComprasComponent } from './features/clientes/historial-compras.component';
import { SugerenciasComponent } from './features/clientes/sugerencias.component';
import { BitacoraIngestaComponent } from './features/ingesta/bitacora-ingesta.component';
import { RolesComponent } from './features/admin/roles.component';
import { UsuariosComponent } from './features/admin/usuarios.component';
import { InicioComponent } from './features/inicio/inicio.component';
import { LayoutComponent } from './features/layout/layout.component';
import { LoginComponent } from './features/login/login.component';
import { RevisionPerfilesComponent } from './features/perfiles/revision-perfiles.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent, canActivate: [invitadoGuard] },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [autenticadoGuard],
    children: [
      { path: 'clientes/:id/preferencias', component: PreferenciasComponent, canActivate: [preferenciasGuard] },
      { path: '', component: InicioComponent },
      { path: 'segmentacion/criterios', component: CriteriosComponent, canActivate: [administradorSegmentacionGuard] },
      {
        path: 'clientes',
        component: BuscarClientesComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.CLIENTE_CONSULTAR },
      },
      {
        path: 'clientes/:id/ficha-integral',
        component: FichaIntegralComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.FICHA_INTEGRAL_CONSULTAR },
      },
      {
        path: 'clientes/:id/historial',
        component: HistorialCambiosComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.CLIENTE_CONSULTAR },
      },
      {
        path: 'clientes/:id/consentimiento',
        component: ConsentimientoComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.CLIENTE_CONSULTAR },
      },
      {
        path: 'clientes/:id/compras',
        component: HistorialComprasComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.INDICADORES_CONSULTAR },
      },
      {
        path: 'clientes/:id/evolucion-consumo',
        component: EvolucionConsumoComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.EVOLUCION_CONSUMO_CONSULTAR },
      },
      {
        path: 'clientes/:id/sugerencias',
        component: SugerenciasComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.SUGERENCIAS_CONSULTAR },
      },
      {
        path: 'clientes-inactivos',
        component: ClientesInactivosComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.INDICADORES_CONSULTAR },
      },
      {
        path: 'analisis/cohortes',
        component: RetencionCohortesComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.INDICADORES_CONSULTAR },
      },
      {
        path: 'perfiles/revision',
        component: RevisionPerfilesComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.CLIENTE_CONSULTAR },
      },
      {
        path: 'eventos',
        component: BitacoraIngestaComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.EVENTOS_REPROCESAR },
      },
      {
        path: 'admin/usuarios',
        component: UsuariosComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.USUARIOS_ADMINISTRAR },
      },
      {
        path: 'admin/roles',
        component: RolesComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.USUARIOS_ADMINISTRAR },
      },
      {
        path: 'admin/auditoria',
        component: AuditoriaComponent,
        canActivate: [permisoGuard],
        data: { permiso: Permisos.AUDITORIA_CONSULTAR },
      },
      { path: 'acceso-denegado', component: AccesoDenegadoComponent },
    ],
  },
  { path: '**', redirectTo: '' },
];
