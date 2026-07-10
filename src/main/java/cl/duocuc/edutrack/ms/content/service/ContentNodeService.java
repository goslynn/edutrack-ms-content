package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.model.dto.NodeRequest;
import cl.duocuc.edutrack.ms.content.model.entity.ContentLevel;
import cl.duocuc.edutrack.ms.content.model.entity.ContentNode;
import cl.duocuc.edutrack.ms.content.repository.ContentFileRepository;
import cl.duocuc.edutrack.ms.content.repository.ContentLevelRepository;
import cl.duocuc.edutrack.ms.content.repository.ContentNodeRepository;
import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import cl.duocuc.edutrack.ms.infrastructure.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD de nodos del arbol respetando la coherencia padre-hijo de la jerarquia activa
 * (BE-CNT-001/002). Regla central: un nodo de profundidad N debe colgar de un padre de
 * profundidad N-1; el nodo del nivel raiz (menor {@code depth}) no lleva padre. Toda
 * violacion estructural se rechaza con {@code 422} (BE-CNT-002).
 */
@ApplicationScoped
public class ContentNodeService {

    @Inject
    ContentNodeRepository nodeRepository;

    @Inject
    ContentLevelRepository levelRepository;

    @Inject
    ContentFileRepository fileRepository;

    /** Dispara la limpieza asincrona de binarios huerfanos tras el commit del borrado. */
    @Inject
    Event<OrphanBlobs> orphanBlobs;

    @Transactional
    public ContentNode create(NodeRequest req) {
        ContentLevel level = requireLevel(req.levelId());
        int rootDepth = rootDepth();

        if (level.depth == rootDepth) {
            // Nodo raiz: no admite padre.
            if (req.parentId() != null) {
                throw invalidParent(
                        "Un nodo del nivel raiz (profundidad %d) no puede tener padre".formatted(rootDepth))
                        .with("levelDepth", level.depth);
            }
        } else {
            // Nodo intermedio/hoja: exige padre de profundidad N-1.
            if (req.parentId() == null) {
                throw invalidParent(
                        "Un nodo de profundidad %d requiere un padre de profundidad %d"
                                .formatted(level.depth, level.depth - 1))
                        .with("levelDepth", level.depth);
            }
            ContentNode parent = nodeRepository.findById(req.parentId());
            if (parent == null) {
                throw new NotFoundException("CONTENT.NODE.PARENT_NOT_FOUND",
                        "No existe el nodo padre %s".formatted(req.parentId()))
                        .with("parentId", req.parentId());
            }
            ContentLevel parentLevel = requireLevel(parent.levelId);
            if (parentLevel.depth != level.depth - 1) {
                throw invalidParent(
                        "El padre debe ser de profundidad %d, pero es de profundidad %d"
                                .formatted(level.depth - 1, parentLevel.depth))
                        .with("levelDepth", level.depth)
                        .with("parentDepth", parentLevel.depth);
            }
        }

        ContentNode node = new ContentNode();
        node.name = req.name();
        node.description = req.description();
        node.orderIndex = req.orderIndex() == null ? 0 : req.orderIndex();
        node.levelId = req.levelId();
        node.parentId = req.parentId();
        nodeRepository.persist(node);
        return node;
    }

    public ContentNode get(UUID id) {
        ContentNode node = nodeRepository.findById(id);
        if (node == null) {
            throw new NotFoundException("CONTENT.NODE.NOT_FOUND",
                    "No existe el nodo %s".formatted(id));
        }
        return node;
    }

    /** Hijos directos de un nodo (o raices si {@code parentId} es null). */
    public List<ContentNode> children(UUID parentId) {
        return parentId == null ? nodeRepository.findRoots() : nodeRepository.findChildren(parentId);
    }

    @Transactional
    public ContentNode update(UUID id, NodeRequest req) {
        ContentNode node = get(id);
        // Solo se editan atributos propios; mover un nodo (cambiar padre/nivel) no se
        // soporta en v1 para no reabrir la validacion de arbol en pleno reordenamiento.
        node.name = req.name();
        node.description = req.description();
        if (req.orderIndex() != null) {
            node.orderIndex = req.orderIndex();
        }
        return node;
    }

    /**
     * Borra un nodo y su subarbol (cascada en BD). Como el cascade limpia las filas de
     * archivos pero no los binarios en el almacenamiento, primero recolecta las
     * {@code storage_key} del subarbol y, tras el commit, delega su borrado a
     * {@link StorageCleanupService} de forma asincrona (via el evento {@link OrphanBlobs}).
     * El request responde apenas se persiste el borrado; la limpieza de disco es
     * fire-and-forget y best-effort.
     */
    @Transactional
    public void delete(UUID id) {
        ContentNode node = get(id);
        // Recolectar ANTES de borrar: el cascade destruye las filas (y con ellas el mapeo
        // a los binarios).
        List<String> orphanKeys = fileRepository.findStorageKeysInSubtree(id);
        nodeRepository.delete(node);
        if (!orphanKeys.isEmpty()) {
            // Observer transaccional AFTER_SUCCESS ⇒ solo se limpia el disco si commitea.
            orphanBlobs.fire(new OrphanBlobs(orphanKeys));
        }
    }

    /**
     * Un nodo es <b>hoja</b> si esta en el nivel de mayor profundidad configurado; solo
     * los nodos hoja admiten archivos (BE-CNT-003).
     */
    public boolean isLeaf(ContentNode node) {
        ContentLevel level = requireLevel(node.levelId);
        return level.depth == leafDepth();
    }

    private ContentLevel requireLevel(UUID levelId) {
        ContentLevel level = levelRepository.findById(levelId);
        if (level == null) {
            throw new NotFoundException("CONTENT.LEVEL.NOT_FOUND",
                    "No existe el nivel %s".formatted(levelId)).with("levelId", levelId);
        }
        return level;
    }

    private int rootDepth() {
        return levelRepository.minDepth().orElseThrow(this::noLevels);
    }

    private int leafDepth() {
        return levelRepository.maxDepth().orElseThrow(this::noLevels);
    }

    private DomainException noLevels() {
        return new DomainException(422, "CONTENT.HIERARCHY.EMPTY",
                "No hay niveles configurados en la jerarquia de contenido");
    }

    private static DomainException invalidParent(String message) {
        return new DomainException(422, "CONTENT.NODE.INVALID_PARENT", message);
    }
}
