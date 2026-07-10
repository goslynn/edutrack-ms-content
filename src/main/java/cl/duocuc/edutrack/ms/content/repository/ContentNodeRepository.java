package cl.duocuc.edutrack.ms.content.repository;

import cl.duocuc.edutrack.ms.content.model.entity.ContentNode;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/** Nodos del arbol de contenido (BE-CNT-001/002). */
@ApplicationScoped
public class ContentNodeRepository implements PanacheRepositoryBase<ContentNode, UUID> {

    /** Hijos directos de un nodo, ordenados por {@code orderIndex}. */
    public List<ContentNode> findChildren(UUID parentId) {
        return list("parentId = ?1 ORDER BY orderIndex ASC", parentId);
    }

    /** Nodos raiz (sin padre), ordenados por {@code orderIndex}. */
    public List<ContentNode> findRoots() {
        return list("parentId IS NULL ORDER BY orderIndex ASC");
    }

    public boolean hasChildren(UUID parentId) {
        return count("parentId", parentId) > 0;
    }
}
