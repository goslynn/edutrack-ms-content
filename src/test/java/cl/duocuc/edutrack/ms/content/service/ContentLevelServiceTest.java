package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.model.dto.LevelRequest;
import cl.duocuc.edutrack.ms.content.model.entity.ContentLevel;
import cl.duocuc.edutrack.ms.content.repository.ContentLevelRepository;
import cl.duocuc.edutrack.ms.infrastructure.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** CRUD de niveles: unicidad de profundidad (BE-CNT-001). */
@ExtendWith(MockitoExtension.class)
class ContentLevelServiceTest {

    @Mock ContentLevelRepository levelRepository;
    @InjectMocks ContentLevelService service;

    @Test
    void create_persistsLevel() {
        when(levelRepository.existsByDepth(1)).thenReturn(false);

        service.create(new LevelRequest(1, "Asignatura", "desc"));

        ArgumentCaptor<ContentLevel> captor = ArgumentCaptor.forClass(ContentLevel.class);
        verify(levelRepository).persist(captor.capture());
        assertEquals(1, captor.getValue().depth);
        assertEquals("Asignatura", captor.getValue().name);
    }

    @Test
    void create_duplicateDepth_throws409_withoutPersisting() {
        when(levelRepository.existsByDepth(1)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.create(new LevelRequest(1, "Otro", null)));

        assertEquals(409, ex.status());
        assertEquals("CONTENT.LEVEL.DEPTH_EXISTS", ex.code());
        verify(levelRepository, never()).persist(any(ContentLevel.class));
    }
}
