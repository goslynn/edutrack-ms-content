package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.model.dto.NodeRequest;
import cl.duocuc.edutrack.ms.content.model.entity.ContentLevel;
import cl.duocuc.edutrack.ms.content.model.entity.ContentNode;
import cl.duocuc.edutrack.ms.content.repository.ContentFileRepository;
import cl.duocuc.edutrack.ms.content.repository.ContentLevelRepository;
import cl.duocuc.edutrack.ms.content.repository.ContentNodeRepository;
import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import cl.duocuc.edutrack.ms.infrastructure.exception.NotFoundException;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Validacion padre-hijo del arbol (BE-CNT-002). Jerarquia simulada: depth 0=raiz,
 * 1=intermedio, 3=hoja (maxima). Repositorios mockeados; sin Quarkus ni DB.
 */
@ExtendWith(MockitoExtension.class)
class ContentNodeServiceTest {

    @Mock ContentNodeRepository nodeRepository;
    @Mock ContentLevelRepository levelRepository;
    @Mock ContentFileRepository fileRepository;
    @Mock Event<OrphanBlobs> orphanBlobs;
    @InjectMocks ContentNodeService service;

    private final UUID rootLevelId = UUID.randomUUID();
    private final UUID midLevelId = UUID.randomUUID();
    private final UUID leafLevelId = UUID.randomUUID();

    private ContentLevel level(UUID id, int depth) {
        ContentLevel l = new ContentLevel();
        l.id = id;
        l.depth = depth;
        l.name = "L" + depth;
        return l;
    }

    private ContentNode node(UUID id, UUID levelId) {
        ContentNode n = new ContentNode();
        n.id = id;
        n.levelId = levelId;
        return n;
    }

    @BeforeEach
    void hierarchy() {
        lenient().when(levelRepository.minDepth()).thenReturn(Optional.of(0));
        lenient().when(levelRepository.maxDepth()).thenReturn(Optional.of(3));
        lenient().when(levelRepository.findById(rootLevelId)).thenReturn(level(rootLevelId, 0));
        lenient().when(levelRepository.findById(midLevelId)).thenReturn(level(midLevelId, 1));
        lenient().when(levelRepository.findById(leafLevelId)).thenReturn(level(leafLevelId, 3));
    }

    @Test
    void create_rootNodeWithoutParent_persists() {
        service.create(new NodeRequest("Semestre 2025", null, 0, rootLevelId, null));
        verify(nodeRepository).persist(any(ContentNode.class));
    }

    @Test
    void create_rootNodeWithParent_throws422() {
        NodeRequest req = new NodeRequest("x", null, 0, rootLevelId, UUID.randomUUID());

        DomainException ex = assertThrows(DomainException.class, () -> service.create(req));
        assertEquals(422, ex.status());
        assertEquals("CONTENT.NODE.INVALID_PARENT", ex.code());
        verify(nodeRepository, never()).persist(any(ContentNode.class));
    }

    @Test
    void create_nonRootWithoutParent_throws422() {
        NodeRequest req = new NodeRequest("x", null, 0, midLevelId, null);

        DomainException ex = assertThrows(DomainException.class, () -> service.create(req));
        assertEquals(422, ex.status());
        assertEquals("CONTENT.NODE.INVALID_PARENT", ex.code());
        verify(nodeRepository, never()).persist(any(ContentNode.class));
    }

    @Test
    void create_childWithParentOfDepthMinusOne_persists() {
        UUID parentId = UUID.randomUUID();
        when(nodeRepository.findById(parentId)).thenReturn(node(parentId, rootLevelId)); // padre depth 0

        service.create(new NodeRequest("Matematicas", null, 0, midLevelId, parentId)); // hijo depth 1

        verify(nodeRepository).persist(any(ContentNode.class));
    }

    @Test
    void create_childWithParentOfWrongDepth_throws422() {
        UUID parentId = UUID.randomUUID();
        when(nodeRepository.findById(parentId)).thenReturn(node(parentId, midLevelId)); // padre depth 1

        // hijo depth 3 (hoja) exige padre depth 2, no 1
        NodeRequest req = new NodeRequest("x", null, 0, leafLevelId, parentId);

        DomainException ex = assertThrows(DomainException.class, () -> service.create(req));
        assertEquals(422, ex.status());
        assertEquals("CONTENT.NODE.INVALID_PARENT", ex.code());
        verify(nodeRepository, never()).persist(any(ContentNode.class));
    }

    @Test
    void create_parentNotFound_throws404() {
        UUID parentId = UUID.randomUUID();
        when(nodeRepository.findById(parentId)).thenReturn(null);

        NodeRequest req = new NodeRequest("x", null, 0, midLevelId, parentId);

        NotFoundException ex = assertThrows(NotFoundException.class, () -> service.create(req));
        assertEquals("CONTENT.NODE.PARENT_NOT_FOUND", ex.code());
    }

    @Test
    void isLeaf_trueOnlyAtMaxDepth() {
        assertTrue(service.isLeaf(node(UUID.randomUUID(), leafLevelId)));
        assertFalse(service.isLeaf(node(UUID.randomUUID(), midLevelId)));
        assertFalse(service.isLeaf(node(UUID.randomUUID(), rootLevelId)));
    }

    @Test
    void delete_notFound_throws404_withoutDeletingOrFiring() {
        UUID id = UUID.randomUUID();
        when(nodeRepository.findById(id)).thenReturn(null);

        assertThrows(NotFoundException.class, () -> service.delete(id));

        verify(nodeRepository, never()).delete(any());
        verifyNoInteractions(orphanBlobs);
    }

    @Test
    void delete_collectsSubtreeKeysBeforeDeleting_thenFiresOrphanEvent() {
        UUID id = UUID.randomUUID();
        ContentNode node = node(id, leafLevelId);
        when(nodeRepository.findById(id)).thenReturn(node);
        when(fileRepository.findStorageKeysInSubtree(id))
                .thenReturn(List.of("ab/cd/1.pdf", "ef/gh/2.png"));

        service.delete(id);

        // El orden es la clave de correctitud: recolectar las storage keys ANTES de
        // borrar (el cascade destruye el mapeo), y disparar el evento despues de borrar.
        InOrder ord = inOrder(fileRepository, nodeRepository, orphanBlobs);
        ord.verify(fileRepository).findStorageKeysInSubtree(id);
        ord.verify(nodeRepository).delete(node);
        ArgumentCaptor<OrphanBlobs> captor = ArgumentCaptor.forClass(OrphanBlobs.class);
        ord.verify(orphanBlobs).fire(captor.capture());
        assertEquals(List.of("ab/cd/1.pdf", "ef/gh/2.png"), captor.getValue().storageKeys());
    }

    @Test
    void delete_noFilesInSubtree_deletesNodeWithoutFiringEvent() {
        UUID id = UUID.randomUUID();
        ContentNode node = node(id, leafLevelId);
        when(nodeRepository.findById(id)).thenReturn(node);
        when(fileRepository.findStorageKeysInSubtree(id)).thenReturn(List.of());

        service.delete(id);

        verify(nodeRepository).delete(node);
        verifyNoInteractions(orphanBlobs);
    }
}
