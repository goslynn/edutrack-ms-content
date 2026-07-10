package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.model.dto.LevelRequest;
import cl.duocuc.edutrack.ms.content.model.entity.ContentLevel;
import cl.duocuc.edutrack.ms.content.repository.ContentLevelRepository;
import cl.duocuc.edutrack.ms.infrastructure.exception.ConflictException;
import cl.duocuc.edutrack.ms.infrastructure.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD de los niveles de la jerarquia de contenido (BE-CNT-001). La profundidad
 * ({@code depth}) es unica: dos niveles no pueden ocupar la misma posicion vertical.
 */
@ApplicationScoped
public class ContentLevelService {

    @Inject
    ContentLevelRepository levelRepository;

    @Transactional
    public ContentLevel create(LevelRequest req) {
        if (levelRepository.existsByDepth(req.depth())) {
            throw new ConflictException("CONTENT.LEVEL.DEPTH_EXISTS",
                    "Ya existe un nivel con profundidad %d".formatted(req.depth()))
                    .with("depth", req.depth());
        }
        ContentLevel level = new ContentLevel();
        level.depth = req.depth();
        level.name = req.name();
        level.description = req.description();
        levelRepository.persist(level);
        return level;
    }

    public List<ContentLevel> list() {
        return levelRepository.listOrdered();
    }

    public ContentLevel get(UUID id) {
        ContentLevel level = levelRepository.findById(id);
        if (level == null) {
            throw new NotFoundException("CONTENT.LEVEL.NOT_FOUND",
                    "No existe el nivel %s".formatted(id));
        }
        return level;
    }

    @Transactional
    public ContentLevel update(UUID id, LevelRequest req) {
        ContentLevel level = get(id);
        if (req.depth() != level.depth && levelRepository.existsByDepth(req.depth())) {
            throw new ConflictException("CONTENT.LEVEL.DEPTH_EXISTS",
                    "Ya existe un nivel con profundidad %d".formatted(req.depth()))
                    .with("depth", req.depth());
        }
        level.depth = req.depth();
        level.name = req.name();
        level.description = req.description();
        return level;
    }

    @Transactional
    public void delete(UUID id) {
        if (!levelRepository.deleteById(id)) {
            throw new NotFoundException("CONTENT.LEVEL.NOT_FOUND",
                    "No existe el nivel %s".formatted(id));
        }
    }
}
