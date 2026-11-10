import { test, expect, Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

/**
 * SCRUM-501 · Reporte comparativo entre periodos en el navegador, contra el entorno de
 * herramientas/pruebas-reportes/ejecutar.sh. Agosto de 2026 (A): Ana 150 y Bruno 100. Septiembre (B): Ana 400 y
 * 200, Bruno 500 y Carla 80. Todo sembrado por eventos.
 */
const admin = process.env['E2E_USUARIO'] ?? 'admin-reportes';
const password = process.env['E2E_PASSWORD'];
const RUTA_API = '/api/comportamiento/reportes/comparativo';
const AGOSTO_SEPTIEMBRE = 'desdeA=2026-08-01&hastaA=2026-08-31&desdeB=2026-09-01&hastaB=2026-09-30';

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

async function periodos(page: Page, desdeA: string, hastaA: string, desdeB: string, hastaB: string) {
  await page.getByLabel('Desde del periodo A').fill(desdeA);
  await page.getByLabel('Hasta del periodo A').fill(hastaA);
  await page.getByLabel('Desde del periodo B').fill(desdeB);
  await page.getByLabel('Hasta del periodo B').fill(hastaB);
}

function fila(page: Page, codigo: string) {
  return page.locator(`tr[data-codigo="${codigo}"] td`);
}

test.beforeAll(() => {
  if (!password) throw new Error('Ejecute herramientas/pruebas-reportes/ejecutar.sh para preparar el entorno aislado.');
});

// --- Criterio 1: dos periodos válidos → indicadores de ambos y su variación ---

test('Gerente: compara agosto con septiembre y ve la variación de cada indicador', async ({ page }, info) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.getByRole('link', { name: 'Comparativo entre periodos' }).click();
  await expect(page).toHaveURL('/analisis/comparativo');
  await periodos(page, '2026-08-01', '2026-08-31', '2026-09-01', '2026-09-30');
  await page.getByRole('button', { name: 'Comparar', exact: true }).click();

  await expect(page.getByText(/Toda la cartera\s*· A: 2026-08-01 a 2026-08-31 · B: 2026-09-01 a 2026-09-30/)).toBeVisible();
  await expect(fila(page, 'COMPRAS')).toHaveText(['2', '4', '+2', '+100.00 %', '▲ Crece']);
  await expect(fila(page, 'CLIENTES_CON_COMPRAS')).toHaveText(['2', '3', '+1', '+50.00 %', '▲ Crece']);
  await expect(fila(page, 'COMPRAS_POR_CLIENTE')).toHaveText(['1.00', '1.33', '+0.33', '+33.00 %', '▲ Crece']);
  await expect(fila(page, 'VALOR_TOTAL')).toHaveText(['250.00', '1,180.00', '+930.00', '+372.00 %', '▲ Crece']);
  await expect(fila(page, 'TICKET_PROMEDIO')).toHaveText(['125.00', '295.00', '+170.00', '+136.00 %', '▲ Crece']);
  await expect(fila(page, 'VALOR_POR_CLIENTE')).toHaveText(['125.00', '393.33', '+268.33', '+214.66 %', '▲ Crece']);
  await page.screenshot({ path: info.outputPath('comparativo.png'), fullPage: true });
});

test('Gerente: con un segmento y al revés (septiembre como base) la variación decae', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comparativo');
  await periodos(page, '2026-09-01', '2026-09-30', '2026-08-01', '2026-08-31');
  await page.getByLabel('Segmento').selectOption({ label: 'Alto valor (2)' });
  await page.getByRole('button', { name: 'Comparar', exact: true }).click();

  await expect(page.getByText(/Segmento «Alto valor» \(2 clientes\)/)).toBeVisible();
  await expect(fila(page, 'COMPRAS')).toHaveText(['3', '2', '-1', '-33.33 %', '▼ Decae']);
  await expect(fila(page, 'VALOR_TOTAL')).toHaveText(['1,100.00', '250.00', '-850.00', '-77.27 %', '▼ Decae']);
});

test('Un periodo sin compras no tiene base: la variación del promedio queda vacía', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comparativo');
  await periodos(page, '2026-01-01', '2026-01-31', '2026-09-01', '2026-09-30');
  await page.getByRole('button', { name: 'Comparar', exact: true }).click();

  await expect(fila(page, 'COMPRAS')).toHaveText(['0', '4', '+4', '—', '★ Nuevo']);
  await expect(fila(page, 'TICKET_PROMEDIO')).toHaveText(['—', '295.00', '—', '—', 'Sin base']);
});

// --- Criterio 2: periodos superpuestos → se avisa ---

test('Periodos superpuestos: se avisa y no sale ninguna petición al servidor', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comparativo');
  await expect(page.getByRole('region', { name: /Indicadores comparados|Sin datos/ })).toBeVisible();

  const peticiones: string[] = [];
  page.on('request', (solicitud) => { if (solicitud.url().includes(RUTA_API)) peticiones.push(solicitud.url()); });
  await periodos(page, '2026-08-01', '2026-09-10', '2026-09-01', '2026-09-30');
  await page.getByRole('button', { name: 'Comparar', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText(
    'Los periodos se superponen (2026-08-01 a 2026-09-10 y 2026-09-01 a 2026-09-30). Elija periodos que no compartan días.');
  await expect(page.getByRole('region', { name: 'Indicadores comparados' })).toHaveCount(0);

  // Tampoco se exporta.
  await page.getByRole('button', { name: 'Exportar CSV' }).click();
  await page.waitForTimeout(500);
  expect(peticiones).toEqual([]);

  // Un periodo al revés también se avisa sin consultar.
  await periodos(page, '2026-08-31', '2026-08-01', '2026-09-01', '2026-09-30');
  await page.getByRole('button', { name: 'Comparar', exact: true }).click();
  await expect(page.getByRole('alert')).toHaveText('Periodo A: La fecha «desde» no puede ser posterior a «hasta». Corrija el periodo.');
  expect(peticiones).toEqual([]);
});

test('El servidor también rechaza periodos superpuestos', async ({ page, request }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  const respuesta = await request.get(`${RUTA_API}?desdeA=2026-08-01&hastaA=2026-09-10&desdeB=2026-09-01&hastaB=2026-09-30`,
    { headers: { Authorization: `Bearer ${await token(page)}` } });
  expect(respuesta.status()).toBe(400);
  expect((await respuesta.json()).mensaje).toContain('no pueden superponerse');
});

// --- Criterio 3: al exportarlo conserva los mismos valores ---

test('Exportar CSV descarga el archivo con los mismos valores que la pantalla y el API', async ({ page, request }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comparativo');
  await periodos(page, '2026-08-01', '2026-08-31', '2026-09-01', '2026-09-30');
  await page.getByRole('button', { name: 'Comparar', exact: true }).click();
  await expect(fila(page, 'COMPRAS')).toHaveText(['2', '4', '+2', '+100.00 %', '▲ Crece']);

  const [descarga] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Exportar CSV' }).click(),
  ]);
  expect(descarga.suggestedFilename()).toBe('reporte-comparativo_2026-08-01_2026-08-31_vs_2026-09-01_2026-09-30.csv');
  const csv = readFileSync((await descarga.path())!, 'utf-8').replace(/^﻿/, '');
  const lineas = csv.split('\r\n');
  const inicio = lineas.indexOf('Indicador,Codigo,Periodo A,Periodo B,Variacion,Variacion %,Tendencia');
  expect(inicio).toBeGreaterThan(0);
  expect(lineas).toContain('Alcance,Toda la cartera');

  const api = await (await request.get(`${RUTA_API}?${AGOSTO_SEPTIEMBRE}`,
    { headers: { Authorization: `Bearer ${await token(page)}` } })).json();
  const filas = lineas.slice(inicio + 1).filter((l) => l !== '').map((l) => l.split(','));
  expect(filas).toHaveLength(api.indicadores.length);
  const numero = (texto: string) => (texto === '' ? null : Number(texto));
  api.indicadores.forEach((indicador: Record<string, unknown>, i: number) => {
    const [, codigo, a, b, abs, pct, tendencia] = filas[i];
    expect(codigo).toBe(indicador['codigo']);
    expect(numero(a)).toBe(indicador['periodoA']);
    expect(numero(b)).toBe(indicador['periodoB']);
    expect(numero(abs)).toBe(indicador['variacionAbsoluta']);
    expect(numero(pct)).toBe(indicador['variacionPorcentual']);
    expect(tendencia === '' ? null : tendencia).toBe(indicador['tendencia']);
  });
  // Y lo que se ve en la pantalla es lo mismo: valor total de B.
  expect(filas.find((f) => f[1] === 'VALOR_TOTAL')![3]).toBe('1180.00');
  await expect(fila(page, 'VALOR_TOTAL').nth(1)).toHaveText('1,180.00');
});

// --- SCRUM-500: permiso de los reportes ---

test('Agente: sin enlace, URL directa denegada y API y CSV 403', async ({ page, request }) => {
  await ingresar(page, 'reportes-agente-atencion');
  await expect(page.getByRole('link', { name: 'Comparativo entre periodos' })).toHaveCount(0);
  await page.goto('/analisis/comparativo');
  await expect(page).toHaveURL('/acceso-denegado');
  const headers = { Authorization: `Bearer ${await token(page)}` };
  expect((await request.get(`${RUTA_API}?${AGOSTO_SEPTIEMBRE}`, { headers })).status()).toBe(403);
  expect((await request.get(`${RUTA_API}/csv?${AGOSTO_SEPTIEMBRE}`, { headers })).status()).toBe(403);
});

test('Sin sesión: va al login y la API responde 401', async ({ page, request }) => {
  await page.goto('/analisis/comparativo');
  await expect(page).toHaveURL('/login');
  expect((await request.get(`${RUTA_API}?${AGOSTO_SEPTIEMBRE}`)).status()).toBe(401);
});
