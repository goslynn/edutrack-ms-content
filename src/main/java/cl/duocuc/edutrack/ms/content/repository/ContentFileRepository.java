package cl.duocuc.edutrack.ms.content.repository;

import cl.duocuc.edutrack.ms.content.model.entity.ContentFile;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/** Metadatos de archivos en nodos hoja (BE-CNT-003/004). */
@ApplicationScoped
public class ContentFileRepository implements PanacheRepositoryBase<ContentFile, UUID> {

    /** Archivos de un nodo, mas recientes primero. */
    public List<ContentFile> findByNode(UUID nodeId) {
        return list("nodeId = ?1 ORDER BY createdAt DESC", nodeId);
    }

    /**
     * Claves de almacenamiento de todos los archivos del subarbol que cuelga de
     * {@code rootNodeId} (incluido el propio nodo). Se resuelve con un CTE recursivo para
     * juntarlas en una sola query <b>antes</b> de borrar el nodo — el cascade de la BD
     * destruye estas filas y con ellas el mapeo a los binarios.
     */
    @SuppressWarnings("unchecked")
    public List<String> findStorageKeysInSubtree(UUID rootNodeId) {
        return getEntityManager().createNativeQuery("""
                WITH RECURSIVE subtree AS (
                    SELECT id FROM content.content_nodes WHERE id = :root
                    UNION ALL
                    SELECT n.id FROM content.content_nodes n
                    JOIN subtree s ON n.parent_id = s.id
                )
                SELECT f.storage_key
                FROM content.content_files f
                JOIN subtree s ON f.node_id = s.id
                """)
                .setParameter("root", rootNodeId)
                .getResultList();
    }
}
