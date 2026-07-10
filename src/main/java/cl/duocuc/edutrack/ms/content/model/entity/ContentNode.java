package cl.duocuc.edutrack.ms.content.model.entity;

import cl.duocuc.edutrack.ms.infrastructure.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Nodo del arbol de contenido (BE-CNT-001/002). Cada nodo pertenece a un
 * {@link ContentLevel} (por su {@code depth}) y referencia a su padre; la coherencia
 * padre-hijo (un nodo de profundidad N cuelga de uno de profundidad N-1, salvo el
 * raiz que no tiene padre) la valida el servicio, no el DDL.
 *
 * <p>Se guardan las FKs como {@link UUID} planos (sin {@code @ManyToOne}) para evitar
 * fetch LAZY/proxies en operaciones que solo necesitan los ids; el servicio resuelve
 * padre y nivel por repositorio cuando hace falta.</p>
 */
@Entity
@Table(name = "content_nodes", schema = "content")
public class ContentNode extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 150)
    public String name;

    @Column(name = "description", length = 500)
    public String description;

    /** Orden entre hermanos (BE-CNT-001). */
    @Column(name = "order_index", nullable = false)
    public int orderIndex;

    /** Profundidad del nodo = {@link ContentLevel#depth} al que pertenece. */
    @Column(name = "level_id", columnDefinition = "uuid", nullable = false)
    public UUID levelId;

    /** Padre en el arbol; {@code null} solo para nodos del nivel raiz. */
    @Column(name = "parent_id", columnDefinition = "uuid")
    public UUID parentId;
}
