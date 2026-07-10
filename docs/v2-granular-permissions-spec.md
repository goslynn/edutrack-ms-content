# Content Service — v2: permisos granulares por instancia (subtree ACL)

> **Estado:** propuesta para una hipotética **v2**. No implementado en v1.
> **Ids de requisito:** `BE-CNT-006` (modelo de acceso), `BE-CNT-007` (administración de grants).
> Estilo normativo de `openspec/specs/` (Requirement / Scenario). Sigue el patrón del
> **Course Service** (`planned-services`): Auth concede el *verbo sobre el tipo*; el MS
> decide la *pertenencia* con su propia regla de negocio.

## Contexto

En v1 la autorización es **por rol y por tipo de recurso**: `@RequirePermission(resource =
"content.nodes", value = WRITE)` pregunta a Auth si algún rol del usuario tiene el bit sobre
la clave `content.nodes`. Si lo tiene, puede tocar **cualquier** nodo. Las columnas
`creator_user`/`updater_user` (de `AuditableEntity`) son **solo auditoría**: registran quién
tocó qué, pero no filtran ni bloquean nada. No hay ownership por instancia.

Eso es correcto para un árbol de contenido **institucional compartido**. v2 cubre el caso en
que el contenido deja de ser global: p. ej. un docente debe ver/editar **solo el subárbol de
sus propias asignaturas**, no el árbol completo. Como Auth es opaco a las instancias (no
conoce nodos ni UUIDs de contenido), la pertenencia se resuelve **dentro del Content
Service**, exactamente como Course resuelve `WHERE docente_id = :userId`.

### Principio de diseño: acceso heredado por el árbol

El contenido es un **árbol**, así que el acceso se modela como grants **sobre nodos que
heredan hacia abajo**: un grant sobre una *Asignatura* concede acceso a todas sus *Unidades*
y *Clases* descendientes, y a sus archivos. Un grant sobre una *Clase* (hoja) concede acceso
solo a esa clase. Esto evita tener que conceder nodo por nodo y refleja cómo se organiza el
material real.

## Modelo de dos compuertas

La autorización de v2 encadena **dos** chequeos; ambos deben pasar:

1. **Compuerta de capacidad (rol/tipo)** — se mantiene `@RequirePermission` sobre
   `content.*`. Responde "¿este rol puede usar la API de contenido en absoluto?". Sin
   cambios respecto de v1.
2. **Compuerta de instancia (pertenencia)** — nueva. Responde "¿este usuario tiene acceso a
   *este* subárbol?". Es una **regla de negocio explícita en la capa de servicio** (no
   Bean Validation, no `@RequirePermission`), en línea con el estándar del proyecto que
   separa los guards de identidad/dominio.

`SUPERUSER`/`ADMIN` cortocircuitan la compuerta 2 vía el `SuperUserResolver` de commons.

## Requirements

### Requirement: Acceso granular heredado por subárbol (BE-CNT-006)

El Content Service SHALL restringir el acceso a nodos y archivos según **grants por
instancia** almacenados en el propio schema `content`, cuando el modo granular esté activo.
El acceso efectivo de un usuario sobre un nodo SHALL derivarse recorriendo la cadena de
**ancestros** del nodo (el nodo y todos sus padres hasta la raíz) y tomando el **mayor nivel
concedido** a cualquiera de los *sujetos* del usuario sobre cualquiera de esos nodos.
`WRITE` SHALL implicar `READ`. Sin ningún grant en la cadena de ancestros ⇒ acceso denegado
(`403`), aunque el rol tenga el permiso de tipo. El acceso a un **archivo** SHALL derivarse
del acceso a su nodo hoja (`content_files.node_id`).

Notas de diseño (no normativas):

- **Sujetos.** Un grant apunta a un `subject_type ∈ {USER, ROLE}` + `subject_id`. Los
  *sujetos* de un request son `{USER:userId} ∪ {ROLE:r | r ∈ X-User-Roles}` — leídos del
  `RequestContext`. Así se soportan grants por usuario (estilo Course) y por rol, con la
  misma tabla.
- **Tabla `content_node_grants`** (schema `content`):

  ```sql
  CREATE TABLE content.content_node_grants (
      id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
      node_id      UUID        NOT NULL REFERENCES content.content_nodes(id) ON DELETE CASCADE,
      subject_type VARCHAR(10) NOT NULL,          -- USER | ROLE
      subject_id   UUID        NOT NULL,
      access_level SMALLINT    NOT NULL,          -- bits Unix-style: READ=4, WRITE=2 (WRITE⊇READ)
      created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      creator_user UUID        NOT NULL,
      CONSTRAINT uq_grant UNIQUE (node_id, subject_type, subject_id)
  );
  CREATE INDEX idx_grants_lookup ON content.content_node_grants(subject_type, subject_id, node_id);
  ```

- **Algoritmo de acceso efectivo** (reusa el CTE de ancestros; se apoya en `idx_nodes_parent`):

  ```sql
  WITH RECURSIVE ancestors AS (
      SELECT id, parent_id FROM content.content_nodes WHERE id = :node
      UNION ALL
      SELECT n.id, n.parent_id FROM content.content_nodes n
      JOIN ancestors a ON n.id = a.parent_id
  )
  SELECT COALESCE(bit_or(g.access_level), 0) AS effective
  FROM content.content_node_grants g
  JOIN ancestors a ON g.node_id = a.id
  WHERE (g.subject_type, g.subject_id) IN (:subjects);
  -- allowed := (effective & required.bit) == required.bit   (con WRITE⊇READ)
  ```

  Es el mismo OR de bits Unix-style que ya usa `@RequirePermission`/`PermissionEvaluator`,
  aplicado a instancias en vez de tipos — el modelo mental no cambia.
- **Punto de enforcement.** Un helper `NodeAccessGuard.require(nodeId, Permission)` en la
  capa de servicio, llamado por `ContentNodeService`/`ContentFileService` antes de leer o
  mutar. Opcionalmente, ergonomía con una anotación name-binding `@RequireNodeAccess` cuyo
  path-param sea el `nodeId`, análoga a `@RequirePermission` pero resuelta contra la ACL
  local (no contra Auth). El helper es la fuente de verdad; la anotación es azúcar.
- **Listados filtrados.** `GET /content/nodes` y `GET /content/files` SHALL devolver solo lo
  accesible: las raíces concedidas al usuario y sus descendientes (subquery contra los
  subárboles de los `node_id` con grant). Nunca se filtra en memoria tras traer todo.
- **Grant implícito del creador.** Al crear un nodo, su creador SHOULD recibir un grant
  `WRITE` implícito sobre él (bootstrap: quien crea puede administrar lo suyo). Configurable
  por `edutrack.content.access.creator-grant=WRITE|NONE`.
- **Modo y compatibilidad.** `edutrack.content.access.granular.enabled` (default `false`)
  activa la compuerta 2. Con el modo **desactivado**, el comportamiento es idéntico a v1
  (solo rol/tipo). Al activarlo sobre datos existentes, la migración SHALL sembrar grants
  para el contenido actual (p. ej. un grant de rol amplio) para no dejar el árbol
  inaccesible de golpe. Regla del conjunto vacío: en modo granular, **sin grants ⇒ deny**;
  el fallback a v1 solo aplica con el modo desactivado.
- **Mover nodos.** v1 no soporta mover un nodo; si v2 lo agrega, mover un subárbol **cambia
  su herencia de acceso** (pasa a heredar del nuevo padre). Debe documentarse como efecto
  esperado.

#### Scenario: Grant en un ancestro se hereda hacia abajo
- **WHEN** un usuario tiene `WRITE` sobre una *Asignatura* y edita una *Clase* descendiente
- **THEN** la edición se permite (el acceso se hereda por la cadena de ancestros) (`BE-CNT-006`)

#### Scenario: Sin grant en la cadena ⇒ 403 pese al permiso de tipo
- **WHEN** un usuario con `WRITE` de rol sobre `content.nodes` intenta editar un nodo sin
  ningún grant en su cadena de ancestros
- **THEN** la respuesta es `403` (la compuerta de instancia deniega)

#### Scenario: Solo lectura no escribe
- **WHEN** un usuario con un grant `READ` sobre un subárbol intenta mutar un nodo dentro de él
- **THEN** la respuesta es `403` (`WRITE` no está en los flags efectivos)

#### Scenario: Listado filtrado a lo accesible
- **WHEN** un usuario lista nodos/archivos
- **THEN** solo aparecen los subárboles a los que tiene grant (directo o heredado), nunca el
  árbol completo

#### Scenario: Acceso a archivo derivado de su nodo
- **WHEN** un usuario solicita el enlace/descarga de un archivo cuyo nodo hoja no está en un
  subárbol que le fue concedido
- **THEN** la respuesta es `403` (el acceso al archivo se hereda del acceso a su nodo)

#### Scenario: SUPERUSER ignora la pertenencia
- **WHEN** un `SUPERUSER`/`ADMIN` accede a cualquier nodo
- **THEN** la compuerta de instancia se cortocircuita (acceso concedido) vía `SuperUserResolver`

#### Scenario: Modo granular desactivado = comportamiento v1
- **WHEN** `edutrack.content.access.granular.enabled=false`
- **THEN** solo aplica la autorización por rol/tipo (v1), sin consultar la ACL

### Requirement: Administración de grants (BE-CNT-007)

El Content Service SHALL exponer endpoints para conceder, listar y revocar grants por nodo,
protegidos por la clave de recurso **`content.grants`** (`READ` para listar, `WRITE` para
conceder/revocar). Conceder un grant sobre un nodo SHALL requerir, además del permiso de
tipo, `WRITE` efectivo del solicitante sobre ese nodo (o ser `SUPERUSER`) — para que la
delegación no escale privilegios fuera del propio subárbol.

- `POST /content/nodes/{nodeId}/grants` — body `{ subjectType, subjectId, accessLevel }` ⇒ `201`.
- `GET /content/nodes/{nodeId}/grants` — lista los grants **directos** del nodo.
- `DELETE /content/grants/{id}` — revoca un grant ⇒ `204`.

#### Scenario: Solo quien tiene WRITE sobre el nodo delega acceso
- **WHEN** un usuario sin `WRITE` efectivo sobre un nodo intenta conceder un grant sobre él
- **THEN** la respuesta es `403` (`BE-CNT-007`)

#### Scenario: Revocar corta el acceso heredado
- **WHEN** se revoca el grant `WRITE` de un usuario sobre una *Asignatura*
- **THEN** ese usuario deja de poder editar las *Clases* descendientes (salvo otro grant en su
  cadena)

## Configuración propuesta

| Propiedad | Default | Descripción |
|---|---|---|
| `edutrack.content.access.granular.enabled` | `false` | Activa la compuerta de instancia (ACL por subárbol) |
| `edutrack.content.access.creator-grant` | `WRITE` | Nivel del grant implícito al creador de un nodo (`WRITE`/`NONE`) |
| `edutrack.content.access.superuser-bypass` | `true` | `SUPERUSER`/`ADMIN` ignoran la pertenencia |

## Piezas nuevas (resumen de implementación)

- **Migración** `V2__node_grants.sql`: tabla `content_node_grants` + índices + seed de
  compatibilidad.
- **Entidad** `ContentNodeGrant` (`CreatableEntity`) + `ContentNodeGrantRepository`.
- **`NodeAccessGuard`** (servicio): resuelve sujetos desde `RequestContext`, corre el CTE de
  ancestros, aplica el OR de bits y el bypass de `SuperUserResolver`. Único lugar con la
  lógica de acceso por instancia — reusado por lectura, mutación, listados y administración
  de grants (no se duplica, igual que el algoritmo de bits en Auth).
- **Nueva clave de recurso** `content.grants` en `ContentResourceId` (+ sembrar el grant en Auth).
- **Recurso** `ContentGrantResource` para BE-CNT-007.
- **Filtro de listados** en `ContentNodeService`/`ContentFileService` (subquery por subárboles
  accesibles).

## Relación con v1

v2 es **aditivo y opt-in**: con el modo desactivado, el Content Service se comporta
exactamente como v1 (rol/tipo). Activarlo introduce la segunda compuerta sin tocar la
primera ni el patrón `@RequirePermission`, y sin pedirle a Auth que conozca instancias de
contenido — la pertenencia vive en el schema `content`, como en Course.
