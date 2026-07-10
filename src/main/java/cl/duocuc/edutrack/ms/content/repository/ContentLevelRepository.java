package cl.duocuc.edutrack.ms.content.repository;

import cl.duocuc.edutrack.ms.content.model.entity.ContentLevel;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Niveles del arbol de contenido (BE-CNT-001). */
@ApplicationScoped
public class ContentLevelRepository implements PanacheRepositoryBase<ContentLevel, UUID> {

    /** Todos los niveles ordenados por profundidad ascendente (raiz primero). */
    public List<ContentLevel> listOrdered() {
        return list("ORDER BY depth ASC");
    }

    public Optional<ContentLevel> findByDepth(int depth) {
        return find("depth", depth).firstResultOptional();
    }

    public boolean existsByDepth(int depth) {
        return count("depth", depth) > 0;
    }

    /** Profundidad maxima configurada (nivel hoja); vacio si no hay niveles. */
    public Optional<Integer> maxDepth() {
        ContentLevel deepest = find("ORDER BY depth DESC").firstResult();
        return Optional.ofNullable(deepest).map(l -> l.depth);
    }

    /** Profundidad minima configurada (nivel raiz); vacio si no hay niveles. */
    public Optional<Integer> minDepth() {
        ContentLevel shallowest = find("ORDER BY depth ASC").firstResult();
        return Optional.ofNullable(shallowest).map(l -> l.depth);
    }
}
