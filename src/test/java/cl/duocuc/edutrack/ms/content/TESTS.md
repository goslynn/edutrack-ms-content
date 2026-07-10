# Pruebas unitarias — content-ms

## ¿Tocan base de datos / red / Docker?

**No.** Pruebas unitarias puras: no levantan Quarkus, no abren conexiones, no ejecutan
Flyway. Los colaboradores que llegarían a BD (repositorios) se reemplazan por mocks de
Mockito; el backend de almacenamiento FileSystem se prueba sobre un `@TempDir` real (disco
local temporal, sin red ni Docker). Corren en ~2s en cualquier runner de CI.

## Estándar

| Decisión | Detalle |
|---|---|
| Framework | JUnit 5 (Jupiter) |
| Mocks | Mockito (`@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`) |
| Nombres | `metodo_escenario_resultadoEsperado` (camelCase) |
| Sin Quarkus / BD / red | Ningún `@QuarkusTest`; repos mockeados; storage sobre `@TempDir` |

## Qué cubre cada archivo

### `service/storage`
- **`FileSystemFileStorageTest`** (5) — `store`+`open` roundtrip; conserva extensión y
  clave sharded `ab/cd/<uuid>.ext`; sin extensión; `open` de clave inexistente ⇒ 404;
  `delete` idempotente.

### `service`
- **`DownloadTokenServiceTest`** (5) — token firmado: roundtrip; fileId equivocado ⇒ 403;
  firma adulterada ⇒ 403; token expirado ⇒ 403; nulo/malformado ⇒ 403 (BE-CNT-003).
- **`ContentLevelServiceTest`** (2) — alta persiste; profundidad duplicada ⇒ 409.
- **`ContentNodeServiceTest`** (10) — **validación padre-hijo (BE-CNT-002)**: raíz sin
  padre persiste; raíz con padre ⇒ 422; no-raíz sin padre ⇒ 422; hijo con padre de
  profundidad N-1 persiste; padre de profundidad equivocada ⇒ 422; padre inexistente ⇒
  404; `isLeaf` verdadero solo en la profundidad máxima. **Borrado + limpieza de
  huérfanos**: inexistente ⇒ 404 sin borrar ni disparar; recolecta las `storage_key` del
  subárbol *antes* de borrar y *después* dispara `OrphanBlobs` (orden verificado); sin
  archivos ⇒ borra sin disparar evento.
- **`StorageCleanupServiceTest`** (3) — política **best-effort**: un fallo se omite y se
  intentan TODAS las claves; con todas fallando no propaga excepción; `submitDeletion`
  vacío/null es no-op.
- **`ContentFileServiceTest`** (6) — nodo no hoja ⇒ 422 sin almacenar; archivo > 500 MB ⇒
  **413** sin almacenar (BE-CNT-004); hoja dentro del límite ⇒ delega en `FileStorage` y
  persiste metadatos; content-type nulo ⇒ `application/octet-stream`; `delete` borra
  metadatos + binario; `openForDownload` verifica el token antes de abrir el stream.

## Qué NO cubren (requerirían `@QuarkusTest` + DB)

- Endpoints HTTP reales (status 201/413/422/403), el `RequirePermissionFilter` (es de
  commons, probado allá), la subida multipart real y la coherencia entidad↔DDL de Flyway.

Esa capa se verifica con el stack completo (`docker compose up`) — validada en esta
entrega arrancando el servicio contra Postgres (Hibernate `validate` cuadra, Flyway aplica
el DDL, OpenAPI expone todos los endpoints).
