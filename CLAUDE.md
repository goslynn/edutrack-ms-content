# CLAUDE.md — Content Service

Guía para trabajar en el **Content Service** de EduTrack. Ver `../CLAUDE.md` para el
contexto del monorepo y el estándar transversal (commons, gateway, permisos, errores).

## Qué es

Content (`/content`, schema `content`) modela el contenido como un **árbol de nodos
configurable globalmente** y adjunta **archivos** en los nodos hoja (BE-CNT-001..004).

- **Jerarquía configurable** (`content_levels`): cada nivel es una fila con una
  `depth` (profundidad 0-based, única). El **raíz** es el de menor `depth` (sus nodos
  no tienen padre) y la **hoja** el de mayor `depth` (único nivel con archivos). Raíz y
  hoja son "configurables" porque se derivan del conjunto de niveles, sin flags. Seed:
  `0 Semestre > 1 Asignatura > 2 Unidad > 3 Clase`.
- **Nodos** (`content_nodes`): `name`, `description`, `orderIndex`, `levelId`,
  `parentId`. La regla padre-hijo (nodo de profundidad N cuelga de uno N-1; el raíz sin
  padre) la valida `ContentNodeService` ⇒ `422 CONTENT.NODE.INVALID_PARENT` (BE-CNT-002).
- **Archivos** (`content_files`): metadatos append-only; el binario vive en el backend
  de almacenamiento. Límite 500 MB ⇒ `413 CONTENT.FILE.TOO_LARGE` (BE-CNT-004); solo en
  nodos hoja ⇒ `422 CONTENT.NODE.NOT_LEAF`.

## Capa de archivos = Strategy (`service/storage`)

El binario se aísla tras la interfaz **`FileStorage`** (`store` / `open` / `delete`) para
que el dominio no dependa del backend. **v1: `FileSystemFileStorage`** (disco local bajo
`edutrack.content.storage.fs.root`, respaldado por un volumen persistente en
Docker/Fly.io). La clave de almacenamiento es un UUID sharded (`ab/cd/<uuid>.ext`), opaca.

Migrar a **S3** es escribir otra implementación de `FileStorage` y publicarla como el bean
por defecto — sin tocar servicios ni endpoints.

### Limpieza de binarios huérfanos (borrado de nodo)

Borrar un nodo cascada las filas de su subárbol en la BD (`ON DELETE CASCADE`), pero el
`FileStorage` no se entera: los binarios quedarían huérfanos. `ContentNodeService.delete`
recolecta las `storage_key` del subárbol (CTE recursivo) **antes** de borrar —el cascade
destruye ese mapeo— y dispara el evento `OrphanBlobs`. `StorageCleanupService` lo observa
en fase transaccional `AFTER_SUCCESS` (solo si el commit tuvo éxito) y **encola** el
borrado en un hilo virtual, respondiendo rápido. La limpieza es **best-effort**: un blob
que falla se omite (a lo sumo se loguea) y el trabajo continúa hasta agotar la lista. Un
huérfano residual nunca detiene la limpieza ni afecta el request.

> **Futuro (v2):** la limpieza push de v1 es best-effort y no reintenta, así que un blob
> que falle repetidamente queda huérfano de verdad. La red de seguridad planificada es un
> **GC mark-and-sweep** periódico (`BE-CNT-005`) que reclama todo binario sin fila que lo
> referencie. Spec completa en [`docs/v2-storage-gc-spec.md`](docs/v2-storage-gc-spec.md).

## Descarga = URL pre-firmada agnóstica del backend

`DownloadTokenService` firma un token **HMAC-SHA256 expirante** sobre `fileId|exp`. El
endpoint `GET /content/files/{id}/link` entrega `{ url, expiresAt }`; `GET
/content/files/{id}/download?token=...` valida el token (⇒ `403` si es inválido/expirado)
y hace streaming desde `FileStorage.open`. Este endpoint **no** lleva `@RequirePermission`:
lo autoriza el token (equivalente a la URL pre-firmada de S3). Al migrar a S3, `link`
devolvería la URL pre-firmada real del SDK sin cambiar el contrato.

## Permisos (`security/ContentResourceId`)

`content.levels`, `content.nodes`, `content.files`. Lectura ⇒ `READ`, mutación ⇒ `WRITE`.
Sembrar los grants correspondientes en Auth.

Autorización **por rol y por tipo de recurso** (no hay ownership por instancia en v1): quien
tiene el bit sobre `content.nodes` puede tocar cualquier nodo. `creator_user`/`updater_user`
son solo auditoría, no se enforcean. El árbol se trata como contenido institucional compartido.

> **Futuro (v2):** para acceso granular por usuario/instancia (p. ej. un docente gestiona solo
> el subárbol de sus asignaturas) el patrón es el del Course Service: Auth concede el verbo
> sobre el tipo, el MS decide la pertenencia con una ACL de subárbol heredada (`BE-CNT-006/007`).
> Plan completo en [`docs/v2-granular-permissions-spec.md`](docs/v2-granular-permissions-spec.md).

## Endpoints

- `/content/levels` — CRUD de niveles.
- `/content/nodes` (`?parentId=`) — CRUD de nodos (valida jerarquía).
- `/content/files` — subida multipart (`nodeId` + `file`), listado (`?nodeId=`),
  metadatos, `link`, `download`, borrado.

## Comandos

```bash
./mvnw quarkus:dev      # dev (necesita Postgres; Hibernate validate contra Flyway)
./mvnw test             # unit tests (JUnit5 + Mockito, sin Quarkus/DB)
./mvnw package          # build (target/quarkus-app/)
```

## Convenciones (heredadas del estándar)

- Entidades extienden `AuditableEntity`/`CreatableEntity` de commons; DDL por Flyway,
  `database.generation=validate` en dev.
- 2 DTOs por recurso (`XxxRequest`/`XxxResponse`), granularidad con `@JsonView`,
  `fromEntity`/`of` en el response. Validación con Bean Validation, nunca `if` en el
  endpoint. Errores de dominio con `DomainException` + sugar. Swagger con
  `@Tag`/`@Operation`/`@Schema`.
