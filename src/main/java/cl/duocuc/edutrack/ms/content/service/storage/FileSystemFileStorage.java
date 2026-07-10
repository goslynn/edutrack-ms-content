package cl.duocuc.edutrack.ms.content.service.storage;

import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import cl.duocuc.edutrack.ms.infrastructure.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Implementacion v1 de {@link FileStorage} sobre el <b>sistema de archivos</b> local
 * (BE-CNT-003/004). El binario se guarda bajo un directorio raiz configurable
 * ({@code edutrack.content.storage.fs.root}) que en Docker/Fly.io mapea a un volumen
 * persistente; asi los archivos sobreviven reinicios del contenedor.
 *
 * <p>La clave de almacenamiento es un UUID <b>sharded</b> en dos niveles de
 * directorios ({@code ab/cd/abcd...ext}) para no acumular millones de entradas en un
 * unico directorio. Es opaca: el resto del servicio la trata como string sin asumir
 * que es una ruta.</p>
 *
 * <p>Bean por defecto: al ser la unica implementacion {@code @ApplicationScoped} de
 * {@link FileStorage}, CDI la inyecta directamente. Swappear a S3 es publicar otra
 * implementacion (y marcar esta como {@code @Alternative} o retirarla).</p>
 */
@ApplicationScoped
public class FileSystemFileStorage implements FileStorage {

    private final Path root;

    @Inject
    public FileSystemFileStorage(
            @ConfigProperty(name = "edutrack.content.storage.fs.root", defaultValue = "/data/content")
            String rootDir) {
        this.root = Path.of(rootDir);
    }

    /** Constructor directo para tests (sin CDI): inyecta el root como {@link Path}. */
    FileSystemFileStorage(Path root) {
        this.root = root;
    }

    @Override
    public String store(String originalFilename, InputStream data) {
        String key = newKey(originalFilename);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new DomainException(500, "CONTENT.STORAGE.WRITE_FAILED",
                    "No se pudo almacenar el archivo", e).with("storageKey", key);
        }
        return key;
    }

    @Override
    public InputStream open(String storageKey) {
        Path source = resolve(storageKey);
        if (!Files.exists(source)) {
            throw new NotFoundException("CONTENT.FILE.BLOB_NOT_FOUND",
                    "El binario no existe en el almacenamiento").with("storageKey", storageKey);
        }
        try {
            return Files.newInputStream(source);
        } catch (IOException e) {
            throw new DomainException(500, "CONTENT.STORAGE.READ_FAILED",
                    "No se pudo leer el archivo", e).with("storageKey", storageKey);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new DomainException(500, "CONTENT.STORAGE.DELETE_FAILED",
                    "No se pudo borrar el archivo", e).with("storageKey", storageKey);
        }
    }

    /** Deriva {@code ab/cd/<uuid><.ext>} desde un UUID nuevo, preservando la extension. */
    private static String newKey(String originalFilename) {
        String hex = UUID.randomUUID().toString().replace("-", "");
        String ext = extension(originalFilename);
        return hex.substring(0, 2) + "/" + hex.substring(2, 4) + "/" + hex + ext;
    }

    private static String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (dot > slash && dot < filename.length() - 1) {
            String ext = filename.substring(dot + 1);
            // Solo extensiones alfanumericas simples (evita inyeccion de rutas via nombre).
            if (ext.matches("[A-Za-z0-9]{1,10}")) {
                return "." + ext.toLowerCase();
            }
        }
        return "";
    }

    /**
     * Resuelve la clave contra el root garantizando que no escapa del directorio
     * (defensa ante {@code ../} en una clave manipulada).
     */
    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root.normalize())) {
            throw new DomainException(400, "CONTENT.STORAGE.INVALID_KEY",
                    "Clave de almacenamiento invalida").with("storageKey", storageKey);
        }
        return resolved;
    }
}
