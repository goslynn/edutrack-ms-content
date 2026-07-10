package cl.duocuc.edutrack.ms.content.model.entity;

import cl.duocuc.edutrack.ms.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Definicion de un <b>nivel</b> del arbol de contenido (BE-CNT-001). La jerarquia es
 * global y totalmente configurable: cada nivel se identifica por su {@link #depth}
 * (profundidad 0-based). El seed define {@code 0=Semestre, 1=Asignatura, 2=Unidad,
 * 3=Clase}, pero agregar/quitar niveles es solo CRUD sobre esta tabla.
 *
 * <p>Del conjunto de niveles se derivan dos roles estructurales sin columnas extra:
 * el <b>raiz</b> es el de menor {@code depth} (sus nodos no tienen padre) y la
 * <b>hoja</b> es el de mayor {@code depth} (unico nivel donde se adjuntan archivos).
 * Asi "raiz y hoja tambien son configurables" (BE-CNT-001): cambian solos al agregar
 * o quitar niveles.</p>
 */
@Entity
@Table(name = "content_levels", schema = "content")
public class ContentLevel extends AuditableEntity {

    /** Profundidad 0-based del nivel; unica. 0 = raiz. */
    @Column(name = "depth", nullable = false, unique = true)
    public int depth;

    /** Nombre legible del nivel (p. ej. "Semestre", "Clase"). */
    @Column(name = "name", nullable = false, length = 100)
    public String name;

    @Column(name = "description", length = 255)
    public String description;
}
