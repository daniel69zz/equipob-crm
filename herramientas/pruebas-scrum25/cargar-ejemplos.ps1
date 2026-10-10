param([string]$Gateway = 'http://localhost:8080', [Parameter(Mandatory=$true)][string]$Token)
$ErrorActionPreference = 'Stop'
$cabeceras = @{ Authorization = "Bearer $Token" }
$ejemplos = @(
    @{nombre='Sin compras'; indicador='FRECUENCIA'; condicion='IGUAL'; minimo=0},
    @{nombre='Compradores ocasionales'; indicador='FRECUENCIA'; condicion='ENTRE'; minimo=1; maximo=3},
    @{nombre='Compradores habituales'; indicador='FRECUENCIA'; condicion='ENTRE'; minimo=4; maximo=9},
    @{nombre='Compradores frecuentes'; indicador='FRECUENCIA'; condicion='MAYOR_IGUAL'; minimo=10},
    @{nombre='Valor inicial'; indicador='VALOR'; condicion='MENOR_IGUAL'; maximo=499.99},
    @{nombre='Valor intermedio'; indicador='VALOR'; condicion='ENTRE'; minimo=500; maximo=4999.99},
    @{nombre='Valor alto'; indicador='VALOR'; condicion='MAYOR_IGUAL'; minimo=5000},
    @{nombre='Consumo de electronica'; indicador='CATEGORIA'; condicion='CONTIENE'; categoria='Electronica'},
    @{nombre='Consumo de ropa'; indicador='CATEGORIA'; condicion='CONTIENE'; categoria='Ropa'},
    @{nombre='Consumo de hogar'; indicador='CATEGORIA'; condicion='CONTIENE'; categoria='Hogar'},
    @{nombre='Consumo de alimentos'; indicador='CATEGORIA'; condicion='CONTIENE'; categoria='Alimentos'},
    @{nombre='Consumo de deportes'; indicador='CATEGORIA'; condicion='CONTIENE'; categoria='Deportes'},
    @{nombre='Consumo de libros'; indicador='CATEGORIA'; condicion='CONTIENE'; categoria='Libros'}
)
$existentes = @(Invoke-RestMethod -Uri "$Gateway/api/segmentacion/criterios" -Headers $cabeceras)
foreach ($ejemplo in $ejemplos) {
    if ($existentes | Where-Object nombre -EQ $ejemplo.nombre) { Write-Host "Ya existe: $($ejemplo.nombre)"; continue }
    $ejemplo.descripcion = 'Ejemplo de prueba SCRUM-25; umbral configurable, no regla comercial definitiva.'
    Invoke-RestMethod -Uri "$Gateway/api/segmentacion/criterios" -Headers $cabeceras -Method Post -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes(($ejemplo | ConvertTo-Json))) | Out-Null
    Write-Host "Creado: $($ejemplo.nombre)"
}
