# Content Service — v2: GC de binarios huérfanos (mark-and-sweep)

> **Estado:** propuesta para una hipotética **v2**. No implementado en v1.
> **Id de requisito:** `BE-CNT-005`.
> Escrito en el estilo normativo de `openspec/specs/` (Requirement / Scenario) para que,
> cuando se construya, honre los contratos de plataforma sin re-derivar decisiones.

## Contexto

En v1 el binario de un archivo se limpia en dos caminos:

1. Borrado explícito de un archivo (`DELETE /content/files/{id}`) ⇒ `FileStorage.delete`.
2. Borrado de un nodo ⇒ recolección del subárbol + limpieza **asíncrona best-effort**
   (`StorageCleanupService`, evento `OrphanBlobs`).

Ambos son *push*: reaccionan a una acción. Ninguno cubre las fugas que ocurren **sin** una
acción de borrado bien terminada:

- **Limpieza best-effort que omitió un blob** (disco lleno, permisos, red S3): v1 lo
  omite por diseño y **no reintenta** ⇒ queda huérfano para siempre.
- **Subida fallida a mitad**: `FileStorage.store` escribió el binario pero la transacción
  que persiste la fila `content_files` hizo rollback ⇒ blob sin fila.
- **Proceso caído** entre `store` y `persist`, o durante la limpieza asíncrona.

El resultado: binarios en el almacenamiento sin ninguna fila que los referencie,
invisibles para el modelo y que solo consumen espacio. v2 los reclama con un barrido
periódico *pull* (**mark-and-sweep**): la BD es la fuente de verdad de lo "vivo"; todo lo
que esté en el almacenamiento y **no** esté referenciado, y sea suficientemente antiguo, es
basura.

## Requirements

### Requirement: Recolección periódica de binarios huérfanos (BE-CNT-005)

El Content Service SHALL ejecutar, de forma programada y configurable, un recolector de
basura que borre del backend de almacenamiento todo binario cuya `storage_key` **no**
aparezca en `content_files.storage_key`. El recolector SHALL ser **idempotente**,
**best-effort** (un fallo por clave se omite y no detiene el barrido) y NO SHALL borrar
binarios creados dentro de una **ventana de gracia** configurable (para no competir con una
subida en curso cuya fila aún no ha commiteado). SHALL poder correr en **dry-run** (reporta
sin borrar) y SHALL emitir un resumen (candidatos, borrados, omitidos, espacio liberado).

Notas de diseño (no normativas, orientan la implementación):

- **Extensión del Strategy.** `FileStorage` gana una capacidad de **enumeración**
  (`Stream<StoredRef> list()` con `key` + `createdAt`/`lastModified`). FileSystem la
  implementa recorriendo el directorio raíz; S3 con `ListObjectsV2` paginado.
- **Ventana de gracia.** Solo son candidatos los binarios con antigüedad mayor a
  `edutrack.content.storage.gc.grace` (default sugerido `PT1H`). Cubre la ventana
  `store → persist` y la carrera con la limpieza asíncrona.
- **Diff contra la BD.** Se compara el conjunto enumerado contra `content_files.storage_key`
  (índice/consulta por lotes; no cargar todo en memoria si el volumen es grande — barrer por
  páginas y consultar `WHERE storage_key IN (...)`).
- **Programación.** `@Scheduled` (quarkus-scheduler) con cron configurable
  (`edutrack.content.storage.gc.cron`, default sugerido nocturno); **deshabilitado por
  defecto** (`edutrack.content.storage.gc.enabled=false`) — se activa por operación.
- **Exclusión mutua entre instancias.** En Fly.io hay N instancias; dos barridos
  simultáneos son seguros (best-effort, `deleteIfExists`) pero desperdician IO. SHOULD
  usar un lock (advisory lock de Postgres `pg_try_advisory_lock`, o `@Scheduled` en modo
  no-concurrente coordinado) para que solo una instancia barra a la vez.
- **Backends con GC nativo.** Una implementación de `FileStorage` MAY delegar en el GC del
  backend (p. ej. **S3 lifecycle rules** sobre un prefijo, o S3 Inventory) y declarar el
  barrido de la aplicación como no-op. El contrato de v1 (`store`/`open`/`delete`) no cambia.
- **Reutiliza la política best-effort de v1.** El bucle de borrado del barrido SHALL seguir
  la misma política que `StorageCleanupService.deleteAll`: omitir el fallo, a lo sumo
  loguear, continuar.

#### Scenario: Huérfano antiguo se recolecta
- **WHEN** el almacenamiento tiene un binario cuya `storage_key` no está en `content_files`
  y su antigüedad supera la ventana de gracia
- **THEN** el barrido lo borra y lo cuenta en el resumen (`BE-CNT-005`)

#### Scenario: Binario referenciado se conserva
- **WHEN** un binario tiene una fila `content_files` que lo referencia
- **THEN** el barrido lo conserva sin tocarlo

#### Scenario: Huérfano reciente se conserva (anti-carrera)
- **WHEN** un binario huérfano fue creado dentro de la ventana de gracia (p. ej. una subida
  cuya fila aún no commitea)
- **THEN** el barrido NO lo borra en esta pasada; será candidato recién cuando envejezca

#### Scenario: Fallo por clave no detiene el barrido
- **WHEN** el borrado de un binario candidato falla (p. ej. permisos)
- **THEN** el barrido lo omite (a lo sumo loguea), continúa con el resto y reporta el
  omitido en el resumen

#### Scenario: Dry-run no borra
- **WHEN** el barrido corre con `edutrack.content.storage.gc.dry-run=true`
- **THEN** reporta los candidatos y el espacio que liberaría, sin borrar nada

#### Scenario: Backend con GC nativo delega
- **WHEN** el `FileStorage` activo delega la expiración en el backend (p. ej. S3 lifecycle)
- **THEN** el barrido de la aplicación es un no-op y la reclamación la hace el backend

## Configuración propuesta

| Propiedad | Default | Descripción |
|---|---|---|
| `edutrack.content.storage.gc.enabled` | `false` | Activa el barrido programado |
| `edutrack.content.storage.gc.cron` | `0 0 3 * * ?` | Cron del barrido (nocturno) |
| `edutrack.content.storage.gc.grace` | `PT1H` | Ventana de gracia (antigüedad mínima para ser candidato) |
| `edutrack.content.storage.gc.dry-run` | `false` | Reporta sin borrar |
| `edutrack.content.storage.gc.batch-size` | `500` | Tamaño de página al enumerar/diferir contra BD |

## Relación con v1

v2 **no reemplaza** la limpieza push de v1 (sigue siendo el camino rápido y dirigido); la
**complementa** como red de seguridad periódica. Con v2 presente, la política best-effort de
v1 deja de ser una fuga permanente: lo que v1 omita, v2 lo reclama en el siguiente barrido.
