-- DDL del schema `content`.
-- Mapeo 1:1 con las entidades JPA (Hibernate `validate` en dev debe cuadrar).

CREATE SCHEMA IF NOT EXISTS content;

-- ── Niveles del arbol (jerarquia global configurable, BE-CNT-001) ────────────
-- La profundidad (depth) es la fuente de verdad del orden vertical: raiz = menor
-- depth, hoja = mayor depth. `depth` es UNIQUE ⇒ un solo nivel por profundidad.
CREATE TABLE content.content_levels (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    depth        INTEGER      NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    creator_user UUID         NOT NULL,
    updater_user UUID         NOT NULL
);

-- ── Nodos del arbol (BE-CNT-001/002) ─────────────────────────────────────────
-- level_id apunta al nivel del nodo; parent_id al nodo padre (NULL solo en raiz).
-- La regla depth(nodo) = depth(padre)+1 la valida el servicio (no el DDL), para no
-- acoplar el schema a la jerarquia configurable.
CREATE TABLE content.content_nodes (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(500),
    order_index  INTEGER      NOT NULL DEFAULT 0,
    level_id     UUID         NOT NULL REFERENCES content.content_levels(id),
    parent_id    UUID         REFERENCES content.content_nodes(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    creator_user UUID         NOT NULL,
    updater_user UUID         NOT NULL
);

CREATE INDEX idx_nodes_parent ON content.content_nodes(parent_id);
CREATE INDEX idx_nodes_level  ON content.content_nodes(level_id);

-- ── Archivos en nodos hoja (append-only, BE-CNT-003/004) ─────────────────────
-- El binario vive en el backend de almacenamiento (v1 FileSystem) por storage_key;
-- aqui solo van los metadatos. Borrar un nodo arrastra sus archivos (CASCADE).
CREATE TABLE content.content_files (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id      UUID         NOT NULL REFERENCES content.content_nodes(id) ON DELETE CASCADE,
    filename     VARCHAR(255) NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(512) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    creator_user UUID         NOT NULL
);

CREATE INDEX idx_files_node ON content.content_files(node_id);

-- ── Seed de la jerarquia por defecto: Semestre > Asignatura > Unidad > Clase ──
-- creator/updater_user = usuario noop de bootstrap (columnas NOT NULL).
INSERT INTO content.content_levels (depth, name, description, creator_user, updater_user)
VALUES
    (0, 'Semestre',   'Periodo academico (nivel raiz)',        '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'),
    (1, 'Asignatura', 'Asignatura dentro del semestre',        '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'),
    (2, 'Unidad',     'Unidad tematica de la asignatura',      '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000'),
    (3, 'Clase',      'Clase (nivel hoja: admite archivos)',   '00000000-0000-0000-0000-000000000000', '00000000-0000-0000-0000-000000000000');
