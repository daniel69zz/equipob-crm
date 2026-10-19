import { test, expect, Page } from '@playwright/test';

const usuario = process.env['E2E_USUARIO'] ?? 'admin-scrum29';
const password = process.env['E2E_PASSWORD'];
const api = '/api/segmentacion/criterios';
let headers: Record<string, string>;
test.describe.configure({ mode: 'serial' });

async function entrar(page: Page, nombre = usuario) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(nombre);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
  await page.goto('/segmentacion/criterios');
}
async function nuevo(page: Page, nombre: string) {
  await page.getByRole('button', { name: '+ Nuevo criterio' }).click();
  await page.getByLabel('Nombre del criterio').fill(nombre);
  await page.getByLabel('Indicador', { exact: true }).selectOption('VALOR');
  await page.getByLabel('Mínimo', { exact: true }).fill('500');
  await page.getByLabel('Máximo', { exact: true }).fill('2000');
}
async function simular(page: Page) {
  await page.getByRole('button', { name: 'Simular impacto', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Impacto estimado de la regla' })).toBeVisible();
}

test.beforeAll(async ({ request }) => {
  if (!password) throw new Error('Use herramientas/pruebas-scrum29/ejecutar.ps1');
  const login = await request.post('/api/auth/login', { data: { usuario, password } });
  expect(login.ok()).toBeTruthy();
  headers = { Authorization: `Bearer ${(await login.json()).token}` };
  for (const rol of ['AGENTE_ATENCION', 'GERENTE_COMERCIAL']) {
    const creado = await request.post('/api/admin/usuarios', { headers, data: {
      nombreUsuario: `scrum29-${rol.toLowerCase()}`, nombreCompleto: `Prueba ${rol}`, password, rol,
    } });
    expect(creado.status()).toBe(201);
  }
});

test('Alta: distribución actual frente a simulada sin guardar y guardado explícito posterior', async ({ page, request }, info) => {
  await entrar(page);
  const criteriosAntes = await (await request.get(api, { headers })).json();
  const historialAntes = await (await request.get(`${api}/2901/historial`, { headers })).json();
  await nuevo(page, 'Alto valor');
  await simular(page);
  const impacto = page.getByRole('region', { name: 'Impacto estimado de la regla' });
  await expect(impacto).toContainText('3 clientes evaluados');
  await expect(impacto).toContainText('1 cambiarían de segmento');
  await expect(impacto).toContainText('Se excluyeron 1 asignaciones sin métricas');
  await expect(impacto.getByRole('row').filter({ hasText: 'Frecuentes' }).locator('td')).toHaveText(['2', '1', '-1']);
  await expect(impacto.getByRole('row').filter({ hasText: 'Alto valor' }).locator('td')).toHaveText(['0', '1', '+1']);
  expect(await (await request.get(api, { headers })).json()).toEqual(criteriosAntes);
  expect(await (await request.get(`${api}/2901/historial`, { headers })).json()).toEqual(historialAntes);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: info.outputPath('simulacion-desktop.png'), fullPage: true });
  await page.getByRole('button', { name: 'Guardar regla simulada' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'creado' })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: 'Alto valor' })).toBeVisible();
});

test('Edición: reemplaza la regla sin duplicarla e invalida la previsualización al editar', async ({ page }) => {
  await entrar(page);
  await page.getByRole('row').filter({ hasText: 'Frecuentes' }).getByRole('button', { name: 'Editar', exact: true }).click();
  await page.getByLabel('Mínimo', { exact: true }).fill('8');
  await simular(page);
  const impacto = page.getByRole('region', { name: 'Impacto estimado de la regla' });
  await expect(impacto.getByRole('row').filter({ hasText: 'Frecuentes' }).locator('td')).toHaveText(['2', '0', '-2']);
  await page.getByLabel('Máximo', { exact: true }).fill('10');
  await expect(impacto).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Guardar regla simulada' })).toHaveCount(0);
  await simular(page);
  await page.getByRole('button', { name: 'Guardar regla simulada' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'actualizado' })).toBeVisible();
});

test('Validaciones: incompleta, rango inverso, superposición y versión obsoleta', async ({ page, request }) => {
  await entrar(page);
  await page.getByRole('button', { name: '+ Nuevo criterio' }).click();
  await page.getByRole('button', { name: 'Simular impacto' }).click();
  await expect(page.getByRole('alert')).toContainText('nombre');
  await page.getByLabel('Nombre del criterio').fill('Regla inválida');
  await page.getByLabel('Mínimo', { exact: true }).fill('20');
  await page.getByLabel('Máximo', { exact: true }).fill('10');
  await page.getByRole('button', { name: 'Simular impacto' }).click();
  await expect(page.getByRole('alert')).toContainText('mínimo no puede superar');
  await page.getByLabel('Mínimo', { exact: true }).fill('9');
  await page.getByLabel('Máximo', { exact: true }).fill('20');
  await page.getByRole('button', { name: 'Simular impacto' }).click();
  await expect(page.getByRole('alert')).toContainText('superpone');
  const response = await request.post(`${api}/simular`, { headers, data: { criterioId: 2901,
    regla: { nombre: 'Frecuentes', indicador: 'FRECUENCIA', condicion: 'ENTRE', minimo: 8, maximo: 10, version: 999 } } });
  expect(response.status()).toBe(409);
});

test('Fallo de lectura: mensaje y nueva simulación al reintentar', async ({ page }) => {
  await entrar(page);
  await nuevo(page, 'Bajo valor');
  await page.getByLabel('Mínimo', { exact: true }).fill('0');
  await page.getByLabel('Máximo', { exact: true }).fill('499');
  await page.route(`**${api}/simular`, route => route.fulfill({ status: 503, json: { mensaje: 'No se pudo leer la base de datos. Intente nuevamente.' } }));
  await page.getByRole('button', { name: 'Simular impacto' }).click();
  await expect(page.getByRole('alert')).toContainText('Intente nuevamente');
  await expect(page.getByRole('heading', { name: 'Impacto estimado de la regla' })).toHaveCount(0);
  await page.unroute(`**${api}/simular`);
  await simular(page);
});

test('Editar mientras se simula cancela la respuesta anterior', async ({ page }) => {
  await entrar(page);
  await nuevo(page, 'Borrador');
  let liberar!: () => void, solicitada!: () => void;
  const espera = new Promise<void>(r => liberar = r), solicitud = new Promise<void>(r => solicitada = r);
  await page.route(`**${api}/simular`, async route => {
    solicitada(); await espera;
    await route.fulfill({ json: { clientesEvaluados: 999, clientesQueCambiarian: 999, reglaActiva: true, distribucion: [] } });
  });
  await page.getByRole('button', { name: 'Simular impacto' }).click();
  await solicitud;
  await expect(page.getByRole('button', { name: 'Guardar criterio', exact: true })).toBeDisabled();
  await page.getByLabel('Nombre del criterio').fill('Borrador cambiado');
  liberar();
  await expect(page.getByRole('heading', { name: 'Impacto estimado de la regla' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Simular impacto' })).toBeEnabled();
});

test('Guardar después de simular revalida un conflicto creado por otro usuario', async ({ page, request }) => {
  await entrar(page);
  await nuevo(page, 'Rango libre');
  await page.getByLabel('Mínimo', { exact: true }).fill('2001');
  await page.getByLabel('Máximo', { exact: true }).fill('3000');
  await simular(page);
  expect((await request.post(api, { headers, data: { nombre: 'Cambio concurrente', indicador: 'VALOR', condicion: 'ENTRE', minimo: 2500, maximo: 3500 } })).status()).toBe(201);
  await page.getByRole('button', { name: 'Guardar regla simulada' }).click();
  await expect(page.getByRole('alert')).toContainText('superpone');
});

test('Acceso: sin sesión y roles no administradores', async ({ page, request }) => {
  expect((await request.post(`${api}/simular`, { data: {} })).status()).toBe(401);
  for (const rol of ['agente_atencion', 'gerente_comercial']) {
    await page.context().clearCookies();
    await page.goto('/login');
    await page.evaluate(() => sessionStorage.clear());
    await entrar(page, `scrum29-${rol}`);
    await expect(page).toHaveURL('/acceso-denegado');
    const sesion = await page.evaluate(() => JSON.parse(sessionStorage.getItem('crm.sesion')!));
    expect((await request.post(`${api}/simular`, { headers: { Authorization: `Bearer ${sesion.token}` }, data: {} })).status()).toBe(403);
  }
});

test('Vista móvil y segmento estimado vacío', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await entrar(page);
  await nuevo(page, 'Sin clientes');
  await page.getByLabel('Mínimo', { exact: true }).fill('4000');
  await page.getByLabel('Máximo', { exact: true }).fill('5000');
  await simular(page);
  const fila = page.getByRole('region', { name: 'Impacto estimado de la regla' }).getByRole('row').filter({ hasText: 'Sin clientes' });
  await expect(fila.locator('td')).toHaveText(['0', '0', '0']);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: info.outputPath('simulacion-mobile.png'), fullPage: true });
});
