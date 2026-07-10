package cl.duocuc.edutrack.ms.content.service.storage;

import cl.duocuc.edutrack.ms.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backend de almacenamiento FileSystem (v1 del Strategy). Prueba real de disco sobre un
 * {@code @TempDir} — sin Quarkus, sin red.
 */
class FileSystemFileStorageTest {

    private FileSystemFileStorage storage(Path root) {
        return new FileSystemFileStorage(root);
    }

    private InputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void store_thenOpen_returnsSameBytes(@TempDir Path root) throws Exception {
        FileSystemFileStorage fs = storage(root);

        String key = fs.store("apunte.pdf", bytes("hola mundo"));

        try (InputStream in = fs.open(key)) {
            assertEquals("hola mundo", new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void store_preservesExtensionAndShardsKey(@TempDir Path root) {
        String key = storage(root).store("apunte.PDF", bytes("x"));

        assertTrue(key.endsWith(".pdf"), "conserva extension en minuscula: " + key);
        assertTrue(key.matches("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32}\\.pdf"), "clave sharded: " + key);
    }

    @Test
    void store_withoutExtension_producesKeyWithoutExtension(@TempDir Path root) {
        String key = storage(root).store("apunte", bytes("x"));

        assertTrue(key.matches("[0-9a-f]{2}/[0-9a-f]{2}/[0-9a-f]{32}"), "clave sin extension: " + key);
    }

    @Test
    void open_unknownKey_throwsNotFound(@TempDir Path root) {
        assertThrows(NotFoundException.class, () -> storage(root).open("ab/cd/deadbeef"));
    }

    @Test
    void delete_removesBlob_andIsIdempotent(@TempDir Path root) {
        FileSystemFileStorage fs = storage(root);
        String key = fs.store("f.txt", bytes("x"));

        fs.delete(key);
        assertThrows(NotFoundException.class, () -> fs.open(key));
        assertDoesNotThrow(() -> fs.delete(key)); // idempotente
    }
}
