import { test, expect, Page } from '@playwright/test';

const usuario = process.env['E2E_USUARIO'] ?? 'admin-scrum28';
const password = process.env['E2E_PASSWORD'];
let headers: Record<string, string>;
test.describe.configure({ mode: 'serial' });

async function ingresar(page: Page, nombre = usuario) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(nombre);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
}
async function consulta(page: Page) {
  await page.getByRole('button', { name: 'Consultar', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Resultados de clientes' })).toBeVisible();
}

test.beforeAll(async ({ request }) => {
  if (!password) throw new Error('Use herramientas/pruebas-scrum28/ejecutar.ps1 o defina E2E_PASSWORD');
  const login = await request.post('/api/auth/login', { data: { usuario, password } });
  expect(login.ok()).toBeTruthy();
  headers = { Authorization: 'Bearer ' + (await login.json()).token };
  for (const rol of ['GERENTE_COMERCIAL', 'ANALISTA_DATOS', 'AGENTE_ATENCION']) {
    const r = await request.post('/api/admin/usuarios', { headers, data: {
      nombreUsuario: 's28-' + rol.toLowerCase(), nombreCompleto: 'Prueba SCRUM-28 ' + rol, password, rol,
    } });
    expect([201, 409], await r.text()).toContain(r.status());
  }
});

test('flujo integrado: menú, cuatro columnas, filtros, orden y paginación', async ({ page }, info) => {
  await ingresar(page);
  await page.getByRole('link', { name: 'Clientes por segmento', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Clientes por segmento' })).toBeVisible();
  await page.getByLabel('Segmento', { exact: true }).selectOption('id:28001');
  await consulta(page);
  await expect(page.getByText('128 clientes encontrados', { exact: true })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(20);
  const ana = page.getByRole('row').filter({ hasText: 'Ana Unificada' });
  await expect(ana).toContainText('Frecuentes SCRUM-28');
  await expect(ana).toContainText('Medio');
  await expect(ana).toContainText('11/10/2026');
  await page.getByRole('button', { name: 'Siguiente', exact: true }).click();
  await expect(page).toHaveURL(/pagina=1/);
  await expect(page.getByLabel('Segmento', { exact: true })).toHaveValue('id:28001');
  await page.getByRole('button', { name: 'Ordenar por Cliente', exact: true }).click();
  await expect(page.locator('tbody tr').first()).toContainText('Cliente 125');
  await expect(page).toHaveURL(/pagina=0/);
  await page.getByLabel('Clientes por página').selectOption('10');
  await expect(page.locator('tbody tr')).toHaveCount(10);
  await page.getByLabel('Nivel de consumo', { exact: true }).selectOption('MEDIO');
  await page.getByLabel('Nombre del cliente').fill('Ana');
  await consulta(page);
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.getByText('1 cliente encontrado', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Nombre del cliente')).toHaveValue('Ana');
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await page.screenshot({ path: info.outputPath('consulta-integrada.png'), fullPage: true });
});

test('segmento vacío y consulta solamente por nivel', async ({ page }, info) => {
  await ingresar(page);
  await page.goto('/segmentacion/clientes');
  await page.getByLabel('Segmento', { exact: true }).selectOption('id:28002');
  await consulta(page);
  await expect(page.getByText('No se encontraron clientes con los filtros seleccionados.')).toBeVisible();
  await page.screenshot({ path: info.outputPath('segmento-vacio.png'), fullPage: true });
  await page.getByRole('button', { name: 'Limpiar', exact: true }).click();
  await expect(page.getByText('Elija el grupo de clientes que desea consultar')).toBeVisible();
  await page.getByLabel('Nivel de consumo', { exact: true }).selectOption('ALTO');
  await consulta(page);
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.locator('tbody')).toContainText('Elena Segmento anterior');
});

test('sin compras, anulaciones, segmentos anteriores y parámetros inválidos', async ({ page, request }) => {
  await ingresar(page);
  await page.goto('/segmentacion/clientes?nivelConsumo=SIN_NIVEL');
  await expect(page.getByText('2 clientes encontrados', { exact: true })).toBeVisible();
  await expect(page.locator('tbody tr').first()).toContainText('Sin compras');
  await page.goto('/segmentacion/clientes?segmento=Segmento%20anterior%20SCRUM-28');
  await expect(page.locator('tbody')).toContainText('Elena Segmento anterior');
  const invalida = await request.get('/api/segmentacion/clientes?segmentoId=28001&tamanio=101', { headers });
  expect(invalida.status()).toBe(400);
  await page.goto('/segmentacion/clientes?segmentoId=999999');
  await expect(page.getByRole('alert')).toContainText('El segmento no existe');
});

for (const rol of ['GERENTE_COMERCIAL', 'ANALISTA_DATOS']) {
  test('acceso real del rol ' + rol + ' sin configuración ni ficha integral', async ({ page, request }) => {
    await ingresar(page, 's28-' + rol.toLowerCase());
    await expect(page.getByRole('link', { name: 'Criterios de segmentación', exact: true })).toHaveCount(0);
    await page.getByRole('link', { name: 'Clientes por segmento', exact: true }).click();
    await page.getByLabel('Segmento', { exact: true }).selectOption('id:28001');
    await consulta(page);
    await expect(page.getByText('128 clientes encontrados', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver ficha', exact: true })).toHaveCount(0);
    const login = await request.post('/api/auth/login', { data: { usuario: 's28-' + rol.toLowerCase(), password } });
    const h = { Authorization: 'Bearer ' + (await login.json()).token };
    expect((await request.get('/api/segmentacion/criterios', { headers: h })).status()).toBe(403);
  });
}

test('agente rechazado por frontend y API; sin sesión recibe 401', async ({ page, request }, info) => {
  await ingresar(page, 's28-agente_atencion');
  await expect(page.getByRole('link', { name: 'Clientes por segmento', exact: true })).toHaveCount(0);
  await page.goto('/segmentacion/clientes');
  await expect(page).toHaveURL('/acceso-denegado');
  await page.screenshot({ path: info.outputPath('acceso-denegado.png'), fullPage: true });
  const login = await request.post('/api/auth/login', { data: { usuario: 's28-agente_atencion', password } });
  const h = { Authorization: 'Bearer ' + (await login.json()).token };
  expect((await request.get('/api/segmentacion/clientes?segmentoId=28001', { headers: h })).status()).toBe(403);
  expect((await request.get('/api/segmentacion/clientes?segmentoId=28001')).status()).toBe(401);
});

test('auditoría visible con usuario, segmento y nivel sin el nombre buscado', async ({ page, request }, info) => {
  const r = await request.get('/api/segmentacion/clientes?segmentoId=28001&nivelConsumo=MEDIO&nombre=NombrePrivado28', { headers });
  expect(r.status()).toBe(200);
  await ingresar(page);
  await page.getByRole('link', { name: 'Auditoría', exact: true }).click();
  const fila = page.getByRole('row').filter({ hasText: 'segmentoId=28001' }).filter({ hasText: 'nivelConsumo=MEDIO' }).first();
  await expect(fila).toContainText(usuario);
  await expect(fila).not.toContainText('NombrePrivado28');
  await page.screenshot({ path: info.outputPath('auditoria-consulta.png'), fullPage: true });
});

test('vista móvil sin desbordar la página y selección al retroceder', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await ingresar(page);
  await page.goto('/segmentacion/clientes?segmentoId=28001&pagina=0&tamanio=10');
  await expect(page.locator('tbody tr')).toHaveCount(10);
  await page.getByRole('button', { name: 'Siguiente', exact: true }).click();
  await expect(page).toHaveURL(/pagina=1/);
  await page.goBack();
  await expect(page).toHaveURL(/pagina=0/);
  await expect(page.getByLabel('Segmento', { exact: true })).toHaveValue('id:28001');
  await expect(page.locator('tbody tr')).toHaveCount(10);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: info.outputPath('consulta-movil.png'), fullPage: true });
});
