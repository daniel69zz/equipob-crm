import { test, expect, Page } from '@playwright/test';
import { readFileSync } from 'node:fs';

/**
 * SCRUM-485 · Exportación de los reportes de clientes a CSV y PDF (SCRUM-60) en el navegador, contra el entorno de
 * herramientas/pruebas-reportes/ejecutar.sh. Usa los mismos datos sembrados por eventos que SCRUM-58 y SCRUM-62:
 * agosto y septiembre de 2026.
 */
const admin = process.env['E2E_USUARIO'] ?? 'admin-reportes';
const password = process.env['E2E_PASSWORD'];
const COMPORTAMIENTO = '/api/comportamiento/reportes/comportamiento';
const COMPARATIVO = '/api/comportamiento/reportes/comparativo';
const SEPTIEMBRE = 'desde=2026-09-01&hasta=2026-09-30';
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

async function septiembre(page: Page) {
  await page.goto('/analisis/comportamiento');
  await page.getByLabel('Desde', { exact: true }).fill('2026-09-01');
  await page.getByLabel('Hasta', { exact: true }).fill('2026-09-30');
  await page.getByRole('button', { name: 'Generar', exact: true }).click();
  await expect(page.getByText(/del 2026-09-01 al 2026-09-30/)).toBeVisible();
}

test.beforeAll(() => {
  if (!password) throw new Error('Ejecute herramientas/pruebas-reportes/ejecutar.sh para preparar el entorno aislado.');
});

// --- Criterio 1: el archivo contiene los mismos datos que en pantalla ---

test('Gerente: exporta el comportamiento de compra a CSV con los mismos valores que la pantalla', async ({ page, request }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await septiembre(page);
  const pantalla = await (await request.get(`${COMPORTAMIENTO}?${SEPTIEMBRE}`,
    { headers: { Authorization: `Bearer ${await token(page)}` } })).json();

  await page.getByLabel('Formato de exportación').selectOption('csv');
  const [descarga] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Exportar CSV' }).click(),
  ]);
  expect(descarga.suggestedFilename()).toBe('reporte-comportamiento_2026-09-01_2026-09-30.csv');
  const filas = readFileSync((await descarga.path())!, 'utf-8').replace(/^﻿/, '').split('\r\n').map((l) => l.split(','));
  const valor = (codigo: string) => filas.find((f) => f.length === 3 && f[1] === codigo)![2];
  expect(valor('COMPRAS')).toBe(String(pantalla.frecuencia.compras));
  expect(valor('CLIENTES_CON_COMPRAS')).toBe(String(pantalla.frecuencia.clientesConCompras));
  expect(Number(valor('VALOR_TOTAL'))).toBe(pantalla.valor.total);
  expect(Number(valor('TICKET_PROMEDIO'))).toBe(pantalla.valor.ticketPromedio);
  // Y es lo que se ve: las compras del periodo en la tarjeta de frecuencia.
  await expect(page.getByLabel('Frecuencia de compra')).toContainText(`${pantalla.frecuencia.compras} compras`);
});

test('Gerente: exporta el comportamiento de compra a PDF', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await septiembre(page);
  await page.getByLabel('Formato de exportación').selectOption('pdf');
  const [descarga] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Exportar PDF' }).click(),
  ]);
  expect(descarga.suggestedFilename()).toBe('reporte-comportamiento_2026-09-01_2026-09-30.pdf');
  expect(readFileSync((await descarga.path())!).subarray(0, 5).toString('ascii')).toBe('%PDF-');
});

test('Gerente: exporta el comparativo en los dos formatos', async ({ page }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  await page.goto('/analisis/comparativo');
  await page.getByLabel('Desde del periodo A').fill('2026-08-01');
  await page.getByLabel('Hasta del periodo A').fill('2026-08-31');
  await page.getByLabel('Desde del periodo B').fill('2026-09-01');
  await page.getByLabel('Hasta del periodo B').fill('2026-09-30');
  for (const formato of ['csv', 'pdf']) {
    await page.getByLabel('Formato de exportación').selectOption(formato);
    const [descarga] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: `Exportar ${formato.toUpperCase()}` }).click(),
    ]);
    expect(descarga.suggestedFilename()).toBe(`reporte-comparativo_2026-08-01_2026-08-31_vs_2026-09-01_2026-09-30.${formato}`);
  }
});

// --- Criterio 2: un rol sin permiso de exportación no exporta ---

test('Agente: no ve los reportes y la exportación por API responde 403', async ({ page, request }) => {
  await ingresar(page, 'reportes-agente-atencion');
  const headers = { Authorization: `Bearer ${await token(page)}` };
  for (const ruta of [`${COMPORTAMIENTO}/exportar?formato=csv&${SEPTIEMBRE}`,
    `${COMPORTAMIENTO}/exportar?formato=pdf&${SEPTIEMBRE}`,
    `${COMPARATIVO}/exportar?formato=pdf&${AGOSTO_SEPTIEMBRE}`]) {
    expect((await request.get(ruta, { headers })).status()).toBe(403);
  }
});

test('Sin sesión: la exportación responde 401', async ({ request }) => {
  expect((await request.get(`${COMPORTAMIENTO}/exportar?formato=csv&${SEPTIEMBRE}`)).status()).toBe(401);
});

// --- Criterio 3: la auditoría dice quién exportó, qué y cuándo ---

test('Administrador: la exportación del gerente aparece en la auditoría', async ({ page, request }) => {
  await ingresar(page, 'reportes-gerente-comercial');
  const gerente = { Authorization: `Bearer ${await token(page)}` };
  expect((await request.get(`${COMPORTAMIENTO}/exportar?formato=pdf&${SEPTIEMBRE}`, { headers: gerente })).status()).toBe(200);

  await ingresar(page, admin);
  const respuesta = await request.get('/api/admin/auditoria?operacion=REPORTE_EXPORTADO&usuario=reportes-gerente-comercial',
    { headers: { Authorization: `Bearer ${await token(page)}` } });
  expect(respuesta.status()).toBe(200);
  const [ultimo] = (await respuesta.json()).eventos;
  expect(ultimo.usuario).toBe('reportes-gerente-comercial');
  expect(ultimo.entidadId).toBe('COMPORTAMIENTO_COMPRA');
  expect(ultimo.ocurridoEn).toBeTruthy();
  expect(JSON.parse(ultimo.detalle)).toMatchObject({ formato: 'PDF', desde: '2026-09-01', hasta: '2026-09-30' });
});
