package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.service.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Politica best-effort de la limpieza de binarios huerfanos: un fallo se omite y el
 * trabajo continua hasta agotar la lista, sin propagar la excepcion. Se ejercita el
 * bucle sincrono ({@code deleteAll}); el despacho asincrono es glue trivial sobre este.
 */
@ExtendWith(MockitoExtension.class)
class StorageCleanupServiceTest {

    @Mock FileStorage storage;
    @InjectMocks StorageCleanupService service;

    @Test
    void deleteAll_continuesPastFailure_attemptingEveryKey() {
        doThrow(new RuntimeException("io")).when(storage).delete("b");

        service.deleteAll(List.of("a", "b", "c"));

        // El fallo en "b" no impide intentar "a" ni "c": se intentan TODAS.
        verify(storage).delete("a");
        verify(storage).delete("b");
        verify(storage).delete("c");
    }

    @Test
    void deleteAll_allFailing_doesNotPropagateAndAttemptsAll() {
        doThrow(new RuntimeException()).when(storage).delete(anyString());

        assertDoesNotThrow(() -> service.deleteAll(List.of("x", "y", "z")));

        verify(storage, times(3)).delete(anyString());
    }

    @Test
    void submitDeletion_emptyOrNull_isNoop() {
        assertDoesNotThrow(() -> service.submitDeletion(List.of()));
        assertDoesNotThrow(() -> service.submitDeletion(null));
        verifyNoInteractions(storage);
    }
}
