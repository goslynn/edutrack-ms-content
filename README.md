# EduTrack — Content Service

Microservicio de **contenido**: árbol jerárquico de nodos configurable globalmente y
archivos adjuntos en los nodos hoja. Parte del monorepo EduTrack (Quarkus 3 · Java 21 ·
PostgreSQL · Flyway).

## Modelo

- **Niveles** (`content_levels`): jerarquía global configurable por `depth` (0 = raíz).
  Seed: `Semestre > Asignatura > Unidad > Clase`.
- **Nodos** (`content_nodes`): árbol con validación padre-hijo (profundidad N cuelga de
  N-1; raíz sin padre).
- **Archivos** (`content_files`): metadatos; el binario vive en el backend de
  almacenamiento. Máx. 500 MB, solo en nodos hoja.

## Almacenamiento de archivos (Strategy)

La interfaz **`FileStorage`** aísla el backend. **v1: FileSystem** sobre un directorio
raíz configurable (`edutrack.content.storage.fs.root`), respaldado por un **volumen
persistente** en Docker. Cambiar a S3 = otra implementación de `FileStorage`, sin tocar
el resto.

La descarga usa un **enlace temporal firmado** (token HMAC expirante) — el equivalente
agnóstico a la URL pre-firmada de S3.

## Endpoints (bajo `/content`)

| Método | Path | Descripción |
|---|---|---|
| GET/POST | `/content/levels` | Listar / crear niveles |
| GET/PUT/DELETE | `/content/levels/{id}` | Detalle / editar / borrar nivel |
| GET/POST | `/content/nodes` | Listar hijos (`?parentId=`) / crear nodo |
| GET/PUT/DELETE | `/content/nodes/{id}` | Detalle / editar / borrar nodo |
| POST | `/content/files` | Subir archivo (multipart: `nodeId`, `file`) |
| GET | `/content/files?nodeId=` | Listar archivos de un nodo |
| GET/DELETE | `/content/files/{id}` | Metadatos / borrar archivo |
| GET | `/content/files/{id}/link` | Enlace de descarga temporal (URL pre-firmada) |
| GET | `/content/files/{id}/download?token=` | Descargar binario (token firmado) |

Swagger UI: `/content/q/swagger-ui` · OpenAPI: `/content/q/openapi`.

## Comandos

```bash
./mvnw test        # tests unitarios (sin Quarkus/DB)
./mvnw quarkus:dev # dev mode (requiere Postgres)
./mvnw package     # build
```

Requiere `edutrack-ms-commons` instalado en `~/.m2` (ver `../commons/install.sh`).
