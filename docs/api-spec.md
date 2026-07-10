# Content Service — Especificación de API (para frontend)

> Documento orientado a quien diseña/implementa la pantalla que consume este
> microservicio. Describe **qué se puede hacer**, **con qué forma de datos** y
> **qué errores esperar**, sin asumir conocimiento previo del backend.

## 1. Qué modela este servicio

Content organiza el material de clases como un **árbol jerárquico configurable**:

```
Semestre (nivel 0, raíz)
  └── Asignatura (nivel 1)
        └── Unidad (nivel 2)
              └── Clase (nivel 3, hoja — aquí van los archivos)
```

Esta jerarquía **no está hardcodeada**: es un CRUD sobre `content_levels`. El
front no debe asumir 4 niveles fijos ni los nombres "Semestre/Asignatura/...";
debe leerlos de `GET /content/levels` y renderizar el árbol dinámicamente en
base a esa cantidad de niveles.

Dos roles se derivan automáticamente del conjunto de niveles (no son un flag,
son el nivel de menor/mayor `depth`):

- **Raíz**: el nivel de `depth` más baja. Sus nodos no tienen padre.
- **Hoja**: el nivel de `depth` más alta. **Solo estos nodos admiten archivos.**

Tres tipos de recurso:

| Recurso | Qué es | Endpoint base |
|---|---|---|
| **Level** (nivel) | Una fila de la jerarquía configurable (p. ej. "Unidad", `depth=2`) | `/content/levels` |
| **Node** (nodo) | Un ítem concreto del árbol (p. ej. "Unidad 1: Introducción") | `/content/nodes` |
| **File** (archivo) | Un archivo adjunto a un nodo hoja | `/content/files` |

## 2. Convenciones generales

- **Base path del gateway:** todos los endpoints van bajo `/content/...`
  (el gateway resuelve el primer segmento del path al microservicio).
- **Formato:** JSON (`application/json`), salvo la subida de archivos
  (`multipart/form-data`) y la descarga (binario streameado).
- **IDs:** UUID en todos los recursos.
- **Autenticación:** la maneja el API Gateway (JWT); el front no interactúa
  con tokens de sesión al llamar a Content — solo con el token de descarga
  de archivos (ver §5.3), que es un mecanismo aparte y de corta duración.
- **Permisos:** cada operación exige un permiso `READ` (lecturas) o `WRITE`
  (altas/bajas/cambios) sobre uno de tres recursos: `content.levels`,
  `content.nodes`, `content.files`. Si el usuario no lo tiene, el backend
  responde `403`. El front debería ocultar/deshabilitar acciones de escritura
  según el rol del usuario para no mostrar botones que van a fallar.

### 2.1 Formato de error

Cualquier error de negocio o validación responde con este envelope JSON
(status HTTP real + body espejado):

```json
{
  "timestamp": "2026-05-20T14:32:11.123Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "CONTENT.NODE.INVALID_PARENT",
  "message": "El padre debe ser de profundidad 2, pero es de profundidad 1",
  "metadata": { "levelDepth": 3, "parentDepth": 1 }
}
```

- `code` es estable y apto para lógica de UI (switch/case), `message` es para
  mostrar al usuario o loguear.
- `metadata` es opcional y trae contexto estructurado adicional.
- Errores de validación de campos (Bean Validation, p. ej. `name` vacío)
  llegan como `400` con `code` ausente y `message` describiendo el/los
  campos inválidos.

### 2.2 Vistas de campos (`@JsonView`)

Algunos campos de las respuestas solo aparecen en ciertos endpoints (ver
detalle por recurso más abajo): por ejemplo `createdAt` no viaja en el
`GET` de detalle simple de un nivel salvo que el endpoint lo declare. Esto ya
está resuelto en el backend — el front solo necesita saber que **un campo
ausente en la respuesta no es un bug**, es la vista aplicada a ese endpoint.

## 3. Niveles — `/content/levels`

Pantalla típica: configuración/administración de la jerarquía (probablemente
solo accesible a un rol admin, dado que `WRITE` sobre `content.levels`
reestructura todo el árbol).

| Método | Path | Permiso | Descripción |
|---|---|---|---|
| `GET` | `/content/levels` | `content.levels:READ` | Lista todos los niveles ordenados por `depth` |
| `GET` | `/content/levels/{id}` | `content.levels:READ` | Obtiene un nivel por id |
| `POST` | `/content/levels` | `content.levels:WRITE` | Crea un nivel |
| `PUT` | `/content/levels/{id}` | `content.levels:WRITE` | Actualiza un nivel |
| `DELETE` | `/content/levels/{id}` | `content.levels:WRITE` | Elimina un nivel |

**Request** (`POST`/`PUT`):

```json
{
  "depth": 2,
  "name": "Unidad",
  "description": "Unidad temática de la asignatura"
}
```

- `depth` (int, requerido, ≥ 0): posición vertical. **Única** — no puede
  repetirse entre niveles. `0` es la raíz por definición (el mínimo `depth`
  presente, no necesariamente literal `0` si se borra el nivel 0).
- `name` (string, requerido, máx. 100).
- `description` (string, opcional, máx. 255).

**Response:**

```json
{
  "id": "b3f1...",
  "depth": 2,
  "name": "Unidad",
  "description": "Unidad temática de la asignatura",
  "createdAt": "2026-05-20T14:00:00Z"
}
```

`createdAt` solo viaja en el detalle (`GET /{id}`) y en el listado.

**Errores específicos:**
- `409 CONTENT.LEVEL.DEPTH_EXISTS` — ya existe un nivel con esa `depth`.
- `404 CONTENT.LEVEL.NOT_FOUND` — id inexistente.

**Notas de diseño para el front:**
- Borrar o insertar un nivel en medio de la jerarquía **no reajusta** los
  `depth` de los demás automáticamente ni migra los nodos existentes — es una
  operación delicada. Conviene advertir al usuario antes de permitirlo, o
  restringir la UI a agregar niveles al final.
- No hay endpoint para "mover" nodos entre niveles al cambiar la jerarquía;
  eso es fuera de alcance de v1.

## 4. Nodos — `/content/nodes`

Pantalla típica: navegación del árbol (breadcrumb + lista de hijos, tipo
explorador de archivos) y formularios de alta/edición de cada ítem.

| Método | Path | Permiso | Descripción |
|---|---|---|---|
| `GET` | `/content/nodes?parentId={uuid}` | `content.nodes:READ` | Hijos directos de `parentId`; **si se omite `parentId`, devuelve los nodos raíz** |
| `GET` | `/content/nodes/{id}` | `content.nodes:READ` | Obtiene un nodo por id |
| `POST` | `/content/nodes` | `content.nodes:WRITE` | Crea un nodo |
| `PUT` | `/content/nodes/{id}` | `content.nodes:WRITE` | Actualiza atributos de un nodo (no lo mueve de lugar) |
| `DELETE` | `/content/nodes/{id}` | `content.nodes:WRITE` | Elimina el nodo **y todo su subárbol** (cascada) |

Esto significa que **para pintar el árbol completo el front navega nivel por
nivel**: primero `GET /content/nodes` (sin `parentId`) para los nodos raíz
(Semestres), luego `GET /content/nodes?parentId={semestreId}` para sus
Asignaturas, y así sucesivamente. No hay un endpoint que traiga el árbol
completo de una sola llamada.

**Request** (`POST`/`PUT`):

```json
{
  "name": "Unidad 1: Introducción",
  "description": "Contenidos introductorios del curso",
  "orderIndex": 0,
  "levelId": "b3f1...",
  "parentId": "a21c..."
}
```

- `name` (string, requerido, máx. 150).
- `description` (string, opcional, máx. 500).
- `orderIndex` (int, opcional, default `0`): orden entre hermanos — úsalo
  para el `sort` de la lista de hijos en la UI.
- `levelId` (UUID, requerido): a qué nivel de la jerarquía pertenece.
- `parentId` (UUID, requerido **salvo** en nodos del nivel raíz, donde debe
  omitirse/ir `null`).

**Regla de coherencia que valida el backend (no el front):** un nodo de
`depth` N debe colgar de un padre de `depth` N-1; el nodo raíz no lleva
padre. Si el formulario permite elegir `levelId` y `parentId` libremente,
el front debería al menos filtrar las opciones de padre a nodos del nivel
inmediatamente superior, para evitar que el usuario dispare el `422` de
abajo por UX — pero el backend es quien manda.

**Response:**

```json
{
  "id": "a21c...",
  "name": "Unidad 1: Introducción",
  "description": "Contenidos introductorios del curso",
  "orderIndex": 0,
  "levelId": "b3f1...",
  "parentId": "9f02...",
  "leaf": false,
  "createdAt": "2026-05-20T14:00:00Z",
  "updatedAt": "2026-05-20T14:00:00Z"
}
```

- `leaf` (bool): `true` si este nodo pertenece al nivel hoja — **el front
  debe usar este flag para decidir si mostrar la sección de archivos** en la
  pantalla de detalle del nodo (en vez de inferirlo comparando `depth`).
- `updatedAt` solo viaja en el detalle (`GET /{id}`); `createdAt` viaja en
  detalle y listado.

**Errores específicos:**
- `404 CONTENT.NODE.NOT_FOUND` — id inexistente.
- `404 CONTENT.NODE.PARENT_NOT_FOUND` — `parentId` no existe.
- `404 CONTENT.LEVEL.NOT_FOUND` — `levelId` no existe.
- `422 CONTENT.NODE.INVALID_PARENT` — viola la regla de jerarquía padre-hijo
  (p. ej. raíz con padre, o padre de profundidad incorrecta). `metadata`
  trae `levelDepth`/`parentDepth` para armar un mensaje útil.
- `422 CONTENT.HIERARCHY.EMPTY` — no hay niveles configurados aún (la
  jerarquía de `/content/levels` está vacía); no se puede crear ningún nodo.

**Notas de diseño para el front:**
- Editar (`PUT`) **solo cambia atributos propios** (`name`, `description`,
  `orderIndex`); no permite mover el nodo de padre o de nivel. Si se necesita
  "mover" un ítem en el árbol, no hay endpoint — habría que borrar y recrear
  (perdiendo el subárbol, ver punto siguiente).
- Borrar un nodo **borra en cascada todo su subárbol** (sub-nodos y archivos
  de todos los niveles debajo) sin confirmación adicional del backend. La UI
  debería mostrar una advertencia fuerte ("esto eliminará N unidades, M
  clases y sus archivos") antes de confirmar un delete en un nodo no-hoja.

## 5. Archivos — `/content/files`

Pantalla típica: dentro del detalle de un nodo hoja ("Clase"), una lista de
adjuntos con botones de descarga y borrado, y una zona de subida.

| Método | Path | Permiso | Descripción |
|---|---|---|---|
| `POST` | `/content/files` (multipart) | `content.files:WRITE` | Sube un archivo a un nodo hoja |
| `GET` | `/content/files?nodeId={uuid}` | `content.files:READ` | Lista los archivos de un nodo |
| `GET` | `/content/files/{id}` | `content.files:READ` | Metadatos de un archivo |
| `GET` | `/content/files/{id}/link` | `content.files:READ` | Genera un enlace de descarga temporal |
| `GET` | `/content/files/{id}/download?token=...` | **sin permiso** (autoriza el token) | Descarga el binario |
| `DELETE` | `/content/files/{id}` | `content.files:WRITE` | Elimina metadatos + binario |

### 5.1 Subir un archivo

`POST /content/files`, `Content-Type: multipart/form-data`, con dos campos:

- `nodeId` (UUID, requerido): el nodo hoja destino.
- `file` (archivo, requerido): el binario.

```
POST /content/files
Content-Type: multipart/form-data; boundary=...

--boundary
Content-Disposition: form-data; name="nodeId"

a21c...
--boundary
Content-Disposition: form-data; name="file"; filename="apunte.pdf"
Content-Type: application/pdf

<binario>
--boundary--
```

Response `201 Created`:

```json
{
  "id": "f001...",
  "nodeId": "a21c...",
  "filename": "apunte.pdf",
  "contentType": "application/pdf",
  "sizeBytes": 204800,
  "createdAt": "2026-05-20T14:10:00Z"
}
```

**Límites y errores:**
- **Tamaño máximo por archivo: 500 MB.** Si se supera ⇒ `413 CONTENT.FILE.TOO_LARGE`
  (`metadata.sizeBytes` / `metadata.maxBytes`). Conviene validar el tamaño
  en el cliente antes de subir para no gastar ancho de banda en un upload
  que el backend va a rechazar.
- Si el nodo destino **no es hoja** ⇒ `422 CONTENT.NODE.NOT_LEAF`. El front
  no debería mostrar la opción de "subir archivo" en nodos donde
  `leaf: false`.
- `400 CONTENT.FILE.NODE_REQUIRED` / `CONTENT.FILE.FILE_REQUIRED` — falta
  alguno de los dos campos del formulario.
- `404 CONTENT.NODE.NOT_FOUND` — `nodeId` no existe.
- Los archivos son **append-only**: no existe "reemplazar" un archivo. Para
  actualizar un adjunto, el flujo es borrar el existente y subir uno nuevo.

### 5.2 Listar / metadatos

`GET /content/files?nodeId={uuid}` — **`nodeId` es obligatorio** (si se
omite, `400 CONTENT.FILE.NODE_REQUIRED`). Devuelve un array de `FileResponse`
como el de arriba (sin `createdAt` en la vista base — ver §2.2; si necesitas
la fecha en la lista, ya viene incluida porque el listado usa la vista
`List`).

`GET /content/files/{id}` — un solo archivo, mismo shape. `404
CONTENT.FILE.NOT_FOUND` si no existe.

### 5.3 Descargar un archivo (flujo en dos pasos)

La descarga **no es un link directo**. Es un patrón de URL pre-firmada:

**Paso 1 — pedir el enlace:**

```
GET /content/files/{id}/link
```

```json
{
  "url": "/content/files/f001.../download?token=1780000000.Xy7f...",
  "expiresAt": "2026-05-20T14:20:00Z"
}
```

**Paso 2 — usar el `url` devuelto** (dentro del tiempo de vida, ~10 minutos
por defecto) para el `GET` real:

```
GET /content/files/f001.../download?token=1780000000.Xy7f...
```

Esto responde el binario con `Content-Disposition: attachment` y
`Content-Type` correcto — en el front basta con hacer `window.location =
url` o un `<a href={url} download>` con el `url` completo devuelto en el
paso 1 (ya incluye el `token` como query param, no hay que armarlo a mano).

**Por qué dos pasos:** el endpoint de descarga en sí **no exige el permiso
`READ` sobre `content.files`** — solo exige un token válido y no expirado.
Es decir, la seguridad de la descarga vive en el token, no en la sesión del
usuario (igual que una signed URL de S3). Practicamente esto significa que
el link es "compartible" mientras no expire, y que tras `expiresAt` hay que
volver a pedir uno nuevo (`403 CONTENT.DOWNLOAD.INVALID_TOKEN` si se usa
vencido o adulterado).

### 5.4 Eliminar

`DELETE /content/files/{id}` — borra metadatos y binario. `204 No Content`
en éxito, `404 CONTENT.FILE.NOT_FOUND` si no existe.

## 6. Flujos de UI sugeridos

**Explorador de contenido (lectura):**
1. `GET /content/levels` → saber cuántos niveles hay y sus nombres (para
   breadcrumbs/tabs).
2. `GET /content/nodes` (sin `parentId`) → nodos raíz.
3. Click en un nodo → `GET /content/nodes?parentId={id}` para sus hijos,
   hasta llegar a un nodo con `leaf: true`.
4. En un nodo hoja → `GET /content/files?nodeId={id}` para listar adjuntos.
5. Click en "descargar" → `GET .../link` y luego navegar al `url` recibido.

**Administración (escritura), condicionada a que el usuario tenga `WRITE`
sobre el recurso correspondiente:**
- Formularios de alta/edición de niveles: solo pantalla de configuración
  global, probablemente fuera del flujo normal de navegación.
- Alta de nodo: formulario con `name`/`description`/`orderIndex`, con
  `levelId`/`parentId` ya resueltos por el contexto de navegación (el nodo
  padre donde el usuario hizo click "Agregar").
- Borrado de nodo no-hoja: modal de confirmación explícito mencionando la
  cascada.
- Subida de archivo: solo visible/habilitada cuando el nodo activo tiene
  `leaf: true`; validar tamaño (500 MB) en cliente antes de subir.

## 7. Referencia rápida de códigos de error

| Code | HTTP | Cuándo |
|---|---|---|
| `CONTENT.LEVEL.NOT_FOUND` | 404 | Nivel inexistente |
| `CONTENT.LEVEL.DEPTH_EXISTS` | 409 | `depth` duplicado al crear/editar un nivel |
| `CONTENT.NODE.NOT_FOUND` | 404 | Nodo inexistente |
| `CONTENT.NODE.PARENT_NOT_FOUND` | 404 | `parentId` no existe |
| `CONTENT.NODE.INVALID_PARENT` | 422 | Viola la regla padre-hijo de la jerarquía |
| `CONTENT.NODE.NOT_LEAF` | 422 | Se intenta subir un archivo a un nodo que no es hoja |
| `CONTENT.HIERARCHY.EMPTY` | 422 | No hay niveles configurados |
| `CONTENT.FILE.NOT_FOUND` | 404 | Archivo inexistente |
| `CONTENT.FILE.NODE_REQUIRED` | 400 | Falta `nodeId` en upload o en el listado |
| `CONTENT.FILE.FILE_REQUIRED` | 400 | Falta el binario en el upload |
| `CONTENT.FILE.TOO_LARGE` | 413 | Archivo > 500 MB |
| `CONTENT.DOWNLOAD.INVALID_TOKEN` | 403 | Token de descarga ausente/inválido/expirado |

## 8. Fuera de alcance en v1 (no esperar estos endpoints)

- Mover un nodo de padre o de nivel.
- Reemplazar/versionar un archivo (solo borrar + volver a subir).
- Permisos por instancia (p. ej. "solo puedo editar mis propias unidades") —
  en v1 el permiso es por tipo de recurso completo (`content.nodes`, etc.),
  no por nodo individual.
- Un endpoint que devuelva el árbol completo en una sola llamada — la
  navegación es nivel por nivel vía `parentId`.
