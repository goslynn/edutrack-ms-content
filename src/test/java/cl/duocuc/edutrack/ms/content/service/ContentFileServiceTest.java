package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.model.entity.ContentFile;
import cl.duocuc.edutrack.ms.content.model.entity.ContentNode;
import cl.duocuc.edutrack.ms.content.repository.ContentFileRepository;
import cl.duocuc.edutrack.ms.content.service.storage.FileStorage;
import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Reglas de subida/borrado de archivos (BE-CNT-003/004): solo nodos hoja, limite de
 * tamano (413) y delegacion al {@link FileStorage}. Colaboradores mockeados.
 */
@ExtendWith(MockitoExtension.class)
class ContentFileServiceTest {

    @Mock ContentFileRepository fileRepository;
    @Mock ContentNodeService nodeService;
    @Mock FileStorage storage;
    @Mock DownloadTokenService downloadTokens;
    @InjectMocks ContentFileService service;

    private final UUID nodeId = UUID.randomUUID();

    private InputStream data() {
        return new ByteArrayInputStream("contenido".getBytes(StandardCharsets.UTF_8));
    }

    private ContentNode leafNode() {
        ContentNode n = new ContentNode();
        n.id = nodeId;
        return n;
    }

    @BeforeEach
    void limit() {
        service.maxFileSizeBytes = 524288000L; // 500 MB
    }

    @Test
    void upload_nonLeafNode_throws422_withoutStoring() {
        ContentNode node = leafNode();
        when(nodeService.get(nodeId)).thenReturn(node);
        when(nodeService.isLeaf(node)).thenReturn(false);

        DomainException ex = assertThrows(DomainException.class,
                () -> service.upload(nodeId, "f.pdf", "application/pdf", 10, data()));

        assertEquals(422, ex.status());
        assertEquals("CONTENT.NODE.NOT_LEAF", ex.code());
        verify(storage, never()).store(any(), any());
        verify(fileRepository, never()).persist(any(ContentFile.class));
    }

    @Test
    void upload_tooLarge_throws413_withoutStoring() {
        ContentNode node = leafNode();
        when(nodeService.get(nodeId)).thenReturn(node);
        when(nodeService.isLeaf(node)).thenReturn(true);

        long tooBig = service.maxFileSizeBytes + 1;
        DomainException ex = assertThrows(DomainException.class,
                () -> service.upload(nodeId, "big.zip", "application/zip", tooBig, data()));

        assertEquals(413, ex.status());
        assertEquals("CONTENT.FILE.TOO_LARGE", ex.code());
        verify(storage, never()).store(any(), any());
        verify(fileRepository, never()).persist(any(ContentFile.class));
    }

    @Test
    void upload_leafWithinLimit_storesAndPersistsMetadata() {
        ContentNode node = leafNode();
        when(nodeService.get(nodeId)).thenReturn(node);
        when(nodeService.isLeaf(node)).thenReturn(true);
        when(storage.store(eq("apunte.pdf"), any())).thenReturn("ab/cd/deadbeef.pdf");

        service.upload(nodeId, "apunte.pdf", "application/pdf", 1234, data());

        ArgumentCaptor<ContentFile> captor = ArgumentCaptor.forClass(ContentFile.class);
        verify(fileRepository).persist(captor.capture());
        ContentFile saved = captor.getValue();
        assertEquals(nodeId, saved.nodeId);
        assertEquals("apunte.pdf", saved.filename);
        assertEquals("application/pdf", saved.contentType);
        assertEquals(1234, saved.sizeBytes);
        assertEquals("ab/cd/deadbeef.pdf", saved.storageKey);
    }

    @Test
    void upload_nullContentType_defaultsToOctetStream() {
        ContentNode node = leafNode();
        when(nodeService.get(nodeId)).thenReturn(node);
        when(nodeService.isLeaf(node)).thenReturn(true);
        when(storage.store(any(), any())).thenReturn("k");

        service.upload(nodeId, "f", null, 1, data());

        ArgumentCaptor<ContentFile> captor = ArgumentCaptor.forClass(ContentFile.class);
        verify(fileRepository).persist(captor.capture());
        assertEquals("application/octet-stream", captor.getValue().contentType);
    }

    @Test
    void delete_removesMetadataAndBlob() {
        ContentFile file = new ContentFile();
        file.id = UUID.randomUUID();
        file.storageKey = "ab/cd/ef.pdf";
        when(fileRepository.findById(file.id)).thenReturn(file);

        service.delete(file.id);

        verify(fileRepository).delete(file);
        verify(storage).delete("ab/cd/ef.pdf");
    }

    @Test
    void openForDownload_verifiesTokenThenOpensStream() {
        ContentFile file = new ContentFile();
        file.id = UUID.randomUUID();
        file.storageKey = "ab/cd/ef.pdf";
        when(fileRepository.findById(file.id)).thenReturn(file);
        when(storage.open("ab/cd/ef.pdf")).thenReturn(data());

        ContentFileService.Download dl = service.openForDownload(file.id, "tok");

        verify(downloadTokens).verify(file.id, "tok");
        assertSame(file, dl.file());
        assertNotNull(dl.stream());
    }
}
