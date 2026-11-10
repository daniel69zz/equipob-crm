import { test, expect, Page } from '@playwright/test';

/**
 * SCRUM-58 · Reporte de comportamiento de compra en el navegador, contra el entorno de
 * herramientas/pruebas-reportes/ejecutar.sh: tres clientes y cuatro compras de septiembre de 2026
 * sembrados por eventos; "Alto valor" tiene a Ana (600) y Bruno (500), Carla (80) queda fuera.
 */
const admin = process.env['E2E_USUARIO'] ?? 'admin-reportes';
const password = process.env['E2E_PASSWORD'];
const RUTA_API = '/api/comportamiento/reportes/comportamiento';

async function ingresar(page: Page, usuario = admin) {
  await page.goto('/login');
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(password!);
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page).toHaveURL('/');
}

async function token(page: Page): Promise<string> {
  return page.evaluate(() => JSON.parse(sessionStorage.getItem('crm.sesion')!).token);
}

async function generar(page: Page, desde: string, hasta: string, segmento: string | null) {
  await page.getByLabel('Desde').fill(desde);
  await page.getByLabel('Hasta').fill(hasta);
  await page.getByLabel('Segmento').selectOption(segmento ? { label: `${segmento} (2)` } : { label: 'Toda la cartera' });
  await page.getByRole('button', { name: 'Generar', exact: true }).click();
}

test.beforeAll(() => {
  if (!password) throw new Error('Ejecute herramientas/pruebas-reportes/ejecutar.sh para preparar el entorno aislado.');
});

// --- Criterio 1: periodo y segmento → frecuencia, valor y categorías del grupo ---

test('Gerente: el reporte de un segmento muestra frecuencia, valor y categorías', async ({ page }, info) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.getByRole('link', { name: 'Comportamiento de compra' }).click();
  await expect(page).toHaveURL('/analisis/comportamiento');

  await generar(page, '2026-09-01', '2026-09-30', 'Alto valor');

  await expect(page.getByText('Segmento «Alto valor» (2 clientes)')).toBeVisible();
  const frecuencia = page.getByRole('region', { name: 'Frecuencia de compra' });
  await expect(frecuencia).toContainText('3 compras');
  await expect(frecuencia).toContainText('2 clientes compraron');
  await expect(frecuencia).toContainText('1.50 compras por cliente');
  const valor = page.getByRole('region', { name: 'Valor de compra' });
  await expect(valor).toContainText('Bs 1,100.00');
  await expect(valor).toContainText('Ticket promedio: Bs 366.67');
  await expect(valor).toContainText('Por cliente: Bs 550.00');
  const categorias = page.getByRole('region', { name: 'Categorías de compra' });
  await expect(categorias.locator('tbody th')).toHaveText(['Hogar', 'Electrónica', 'Accesorios']);
  await expect(categorias.getByRole('row', { name: /Hogar/ })).toContainText('700.00');
  await page.screenshot({ path: info.outputPath('reporte-segmento.png'), fullPage: true });
});

test('Gerente: sin segmento cubre la cartera completa', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comportamiento');
  await generar(page, '2026-09-01', '2026-09-30', null);

  // La línea del alcance, no la opción del selector que lleva el mismo texto.
  await expect(page.getByText(/Toda la cartera\s*· del 2026-09-01 al 2026-09-30/)).toBeVisible();
  await expect(page.getByRole('region', { name: 'Frecuencia de compra' })).toContainText('4 compras');
  await expect(page.getByRole('region', { name: 'Valor de compra' })).toContainText('Bs 1,180.00');
  await expect(page.getByRole('region', { name: 'Categorías de compra' }).locator('tbody th'))
    .toHaveText(['Hogar', 'Electrónica', 'Accesorios', 'Deportes']);
});

test('Gerente: un periodo sin compras dice que no hay datos, sin error', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comportamiento');
  await generar(page, '2026-01-01', '2026-01-31', 'Alto valor');

  await expect(page.getByRole('region', { name: 'Sin datos' })).toContainText('no tienen compras vigentes');
  await expect(page.getByRole('alert')).toHaveCount(0);
});

// --- Criterio 2: un periodo invertido se avisa sin consultar al servidor ---

test('Periodo invertido: se avisa y no sale ninguna petición al servidor', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comportamiento');
  // Espera la carga inicial para que solo cuente lo que pasa después de pulsar Generar.
  await expect(page.getByRole('region', { name: /Frecuencia de compra|Sin datos/ })).toBeVisible();

  const peticiones: string[] = [];
  page.on('request', (solicitud) => {
    if (solicitud.url().includes(RUTA_API)) peticiones.push(solicitud.url());
  });
  await generar(page, '2026-09-30', '2026-09-01', 'Alto valor');

  await expect(page.getByRole('alert')).toHaveText('La fecha «desde» no puede ser posterior a «hasta». Corrija el periodo.');
  await expect(page.getByRole('region', { name: 'Frecuencia de compra' })).toHaveCount(0);
  await page.waitForTimeout(500);
  expect(peticiones).toEqual([]);

  // Al corregirlo, sí consulta.
  await generar(page, '2026-09-01', '2026-09-30', 'Alto valor');
  await expect(page.getByRole('region', { name: 'Frecuencia de compra' })).toContainText('3 compras');
  expect(peticiones).toHaveLength(1);
});

test('Periodo incompleto: se avisa sin consultar', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comportamiento');
  const peticiones: string[] = [];
  await page.getByLabel('Desde').fill('');
  page.on('request', (solicitud) => { if (solicitud.url().includes(RUTA_API)) peticiones.push(solicitud.url()); });
  await page.getByRole('button', { name: 'Generar', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('Indique las fechas desde y hasta del periodo.');
  expect(peticiones).toEqual([]);
});

// --- Criterio 3: los resultados coinciden con los indicadores del cliente ---

test('La cartera coincide con la suma de los indicadores de sus clientes', async ({ page, request }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  const headers = { Authorization: `Bearer ${await token(page)}` };
  const reporte = await (await request.get(`${RUTA_API}?desde=2026-01-01&hasta=2026-12-31`, { headers })).json();

  let compras = 0;
  let valor = 0;
  for (const origen of ['CLI-R1', 'CLI-R2', 'CLI-R3']) {
    const perfil = await (await request.get(`/api/perfil/clientes?idClienteOrigen=${origen}`, { headers })).json();
    const id = perfil.clientes[0].id;
    const indicadores = await (await request.get(
      `/api/comportamiento/clientes/${id}/indicadores?identificador=${origen}`, { headers })).json();
    compras += indicadores.frecuencia;
    valor += Number(indicadores.valorAcumulado.valor);
  }
  expect(reporte.frecuencia.compras).toBe(compras);
  expect(Number(reporte.valor.total)).toBeCloseTo(valor, 2);
});

// --- Permiso de los reportes ---

test('Agente: sin enlace, URL directa denegada y API 403', async ({ page, request }) => {
  await ingresar(page, 'reportes-agente-atencion');
  await expect(page.getByRole('link', { name: 'Comportamiento de compra' })).toHaveCount(0);
  await page.goto('/analisis/comportamiento');
  await expect(page).toHaveURL('/acceso-denegado');
  const headers = { Authorization: `Bearer ${await token(page)}` };
  expect((await request.get(`${RUTA_API}?desde=2026-09-01&hasta=2026-09-30`, { headers })).status()).toBe(403);
});

test('Sin sesión: va al login y la API responde 401', async ({ page, request }) => {
  await page.goto('/analisis/comportamiento');
  await expect(page).toHaveURL('/login');
  expect((await request.get(`${RUTA_API}?desde=2026-09-01&hasta=2026-09-30`)).status()).toBe(401);
});
