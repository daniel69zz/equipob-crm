import { test, expect, Page, APIRequestContext } from '@playwright/test';

/**
 * SCRUM-65 · Parámetros generales del CRM en el navegador, contra el entorno de herramientas/pruebas-reportes/
 * ejecutar.sh (Gateway, comportamiento y fidelización reales). Cada prueba parte de los valores vigentes y al final
 * se restauran, para no alterar las demás pruebas del entorno.
 */
const admin = process.env['E2E_USUARIO'] ?? 'admin-reportes';
const password = process.env['E2E_PASSWORD'];
const gerente = 'reportes-gerente-comercial';

interface Parametro { codigo: string; valor: number; version: number }

async function ingresar(page: Page, usuario = admin) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
}

async function tokenDe(request: APIRequestContext, usuario = admin): Promise<string> {
  const respuesta = await request.post('/api/auth/login', { data: { usuario, password } });
  expect(respuesta.ok()).toBeTruthy();
  return (await respuesta.json()).token;
}

async function parametros(request: APIRequestContext, token: string, servicio: string): Promise<Parametro[]> {
  const respuesta = await request.get(`/api/${servicio}/parametros`, { headers: { Authorization: `Bearer ${token}` } });
  expect(respuesta.status()).toBe(200);
  return respuesta.json();
}

let originales: Record<string, Parametro[]> = {};

test.beforeAll(async ({ request }) => {
  if (!password) throw new Error('Ejecute herramientas/pruebas-reportes/ejecutar.sh para preparar el entorno aislado.');
  const token = await tokenDe(request);
  originales = {
    comportamiento: await parametros(request, token, 'comportamiento'),
    fidelizacion: await parametros(request, token, 'fidelizacion'),
  };
});

test.afterAll(async ({ request }) => {
  const token = await tokenDe(request);
  for (const servicio of ['comportamiento', 'fidelizacion']) {
    const actuales = await parametros(request, token, servicio);
    const cambios = actuales.flatMap(p => {
      const original = originales[servicio].find(o => o.codigo === p.codigo)!;
      return original.valor === p.valor ? [] : [{ codigo: p.codigo, valor: original.valor, version: p.version }];
    });
    // El umbral alto se restaura junto con el medio: se valida el conjunto, no cada valor por separado.
    if (cambios.length) {
      const respuesta = await request.put(`/api/${servicio}/parametros`, {
        headers: { Authorization: `Bearer ${token}` }, data: { cambios } });
      expect(respuesta.status()).toBe(200);
    }
  }
});

test('criterios 1 y 3: el cambio rige en el cálculo siguiente y queda en el historial', async ({ page, request }) => {
  await ingresar(page);
  await page.getByRole('link', { name: 'Parámetros generales', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Parámetros generales', exact: true })).toBeVisible();

  const inactividad = page.getByLabel('Periodo de inactividad (días)');
  const anterior = Number(await inactividad.inputValue());
  const nuevo = anterior === 120 ? 121 : 120;
  await inactividad.fill(String(nuevo));
  const vigencia = page.getByLabel('Vigencia de los puntos (meses)');
  const vigenciaAnterior = Number(await vigencia.inputValue());
  const vigenciaNueva = vigenciaAnterior === 24 ? 25 : 24;
  await vigencia.fill(String(vigenciaNueva));

  const clientes = page.locator('section').filter({ hasText: 'Clientes y consumo' });
  await clientes.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Se guardó periodo de inactividad');
  const fidelizacion = page.locator('section').filter({ hasText: 'Programa de fidelización' });
  await fidelizacion.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Se guardó vigencia de los puntos');

  // Criterio 1: los cálculos siguientes usan el valor nuevo, sin reiniciar nada.
  const token = await tokenDe(request);
  const inactivos = await request.get('/api/comportamiento/clientes?estado=inactivo',
    { headers: { Authorization: `Bearer ${token}` } });
  expect((await inactivos.json()).umbralDias).toBe(nuevo);
  const caducidad = await request.get('/api/fidelizacion/caducidad/configuracion',
    { headers: { Authorization: `Bearer ${token}` } });
  expect((await caducidad.json()).vigenciaMeses).toBe(vigenciaNueva);

  // Criterio 3: el historial muestra el valor anterior, el nuevo, quién y cuándo.
  const historial = page.getByRole('table');
  const filaInactividad = historial.getByRole('row').filter({ hasText: 'Periodo de inactividad' }).first();
  await expect(filaInactividad).toContainText(admin);
  await expect(filaInactividad).toContainText(`${anterior} días`);
  await expect(filaInactividad).toContainText(`${nuevo} días`);
  await expect(historial.getByRole('row').filter({ hasText: 'Vigencia de los puntos' }).first())
    .toContainText(`${vigenciaNueva} meses`);
});

test('criterio 2: un valor fuera de rango se rechaza en la pantalla y en el servicio', async ({ page, request }) => {
  await ingresar(page);
  await page.goto('/admin/parametros');
  const inactividad = page.getByLabel('Periodo de inactividad (días)');
  const vigente = await inactividad.inputValue();
  let envios = 0;
  page.on('request', r => { if (r.method() === 'PUT' && r.url().includes('/parametros')) envios++; });

  await inactividad.fill('3');
  const clientes = page.locator('section').filter({ hasText: 'Clientes y consumo' });
  await clientes.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
  await expect(clientes.getByText('Debe estar entre 7 y 730 días.')).toBeVisible();

  const medio = Number(await page.getByLabel('Umbral de consumo medio (Bs)').inputValue());
  await inactividad.fill(vigente);
  await page.getByLabel('Umbral de consumo alto (Bs)').fill(String(medio));
  await clientes.getByRole('button', { name: 'Guardar cambios', exact: true }).click();
  await expect(clientes.getByText('Debe ser mayor que el umbral de consumo medio.')).toBeVisible();
  expect(envios).toBe(0);

  // El servicio también lo rechaza aunque la solicitud no venga de la pantalla.
  const token = await tokenDe(request);
  const [vigencia] = await parametros(request, token, 'fidelizacion');
  const rechazo = await request.put('/api/fidelizacion/parametros', { headers: { Authorization: `Bearer ${token}` },
    data: { cambios: [{ codigo: vigencia.codigo, valor: 121, version: vigencia.version }] } });
  expect(rechazo.status()).toBe(400);
  expect((await rechazo.json()).campos.PUNTOS_VIGENCIA_MESES).toBe('debe estar entre 1 y 120 meses');
  expect((await parametros(request, token, 'fidelizacion'))[0].valor).toBe(vigencia.valor);
});

test('solo el Administrador de CRM configura los parámetros generales', async ({ page, request }) => {
  await ingresar(page, gerente);
  await expect(page.getByRole('link', { name: 'Parámetros generales', exact: true })).toHaveCount(0);
  await page.goto('/admin/parametros');
  await expect(page).toHaveURL(/acceso-denegado/);

  const token = await tokenDe(request, gerente);
  for (const servicio of ['comportamiento', 'fidelizacion']) {
    const respuesta = await request.get(`/api/${servicio}/parametros`, { headers: { Authorization: `Bearer ${token}` } });
    expect(respuesta.status()).toBe(403);
  }
});
