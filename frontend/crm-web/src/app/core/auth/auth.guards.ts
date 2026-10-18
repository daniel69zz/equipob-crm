import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Solo deja pasar si hay una sesión vigente; si no, envía al inicio de sesión. */
export const autenticadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estaAutenticado() ? true : inject(Router).createUrlTree(['/login']);
};

/**
 * Exige el permiso indicado en la ruta: `data: { permiso: 'USUARIOS_ADMINISTRAR' }`.
 * Sin el permiso, envía a la pantalla de acceso denegado.
 */
export const permisoGuard: CanActivateFn = (ruta) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.estaAutenticado()) {
    return router.createUrlTree(['/login']);
  }
  const permiso = ruta.data['permiso'] as string | undefined;
  return !permiso || auth.tienePermiso(permiso) ? true : router.createUrlTree(['/acceso-denegado']);
};

/** Evita mostrar el inicio de sesión a quien ya tiene una sesión vigente. */
export const invitadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estaAutenticado() ? inject(Router).createUrlTree(['/']) : true;
};

export const preferenciasGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.estaAutenticado()) return router.createUrlTree(['/login']);
  return auth.puedeConsultarPreferencias() ? true : router.createUrlTree(['/acceso-denegado']);
};

/** SCRUM-25 exige también el rol, aunque otro rol reciba por error el permiso. */
export const administradorSegmentacionGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.estaAutenticado() && auth.sesion()?.rol === 'ADMINISTRADOR_CRM' && auth.tienePermiso('SEGMENTACION_CONFIGURAR')
    ? true : inject(Router).createUrlTree(['/acceso-denegado']);
};
