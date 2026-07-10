package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.model.dto.DownloadLinkResponse;
import cl.duocuc.edutrack.ms.content.model.entity.ContentFile;
import cl.duocuc.edutrack.ms.content.model.entity.ContentNode;
import cl.duocuc.edutrack.ms.content.repository.ContentFileRepository;
import cl.duocuc.edutrack.ms.content.service.storage.FileStorage;
import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import cl.duocuc.edutrack.ms.infrastructure.exception.NotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * Sube, lista, entrega y borra archivos en nodos hoja (BE-CNT-003/004). Delega el
 * binario al {@link FileStorage} (Strategy; v1 FileSystem) y guarda solo metadatos.
 * Aplica las dos reglas de la spec: los archivos solo cuelgan de nodos <b>hoja</b>
 * (⇒ {@code 422}) y no pueden superar el tamano maximo configurado (⇒ {@code 413},
 * BE-CNT-004).
 */
@ApplicationScoped
public class ContentFileService {

    @Inject
    ContentFileRepository fileRepository;

    @Inject
    ContentNodeService nodeService;

    @Inject
    FileStorage storage;

    @Inject
    DownloadTokenService downloadTokens;

    /** Tamano maximo por archivo en bytes. Default 500 MB (BE-CNT-004). */
    @ConfigProperty(name = "edutrack.content.max-file-size-bytes", defaultValue = "524288000")
    long maxFileSizeBytes;

    /**
     * Sube un archivo a un nodo hoja. Valida existencia del nodo (⇒ {@code 404}), que
     * sea hoja (⇒ {@code 422}) y el tamano (⇒ {@code 413}); recien entonces escribe el
     * binario y persiste los metadatos.
     */
    @Transactional
    public ContentFile upload(UUID nodeId, String filename, String contentType,
                              long sizeBytes, InputStream data) {
        ContentNode node = nodeService.get(nodeId);
        if (!nodeService.isLeaf(node)) {
            throw new DomainException(422, "CONTENT.NODE.NOT_LEAF",
                    "Solo se pueden subir archivos a nodos hoja").with("nodeId", nodeId);
        }
        if (sizeBytes > maxFileSizeBytes) {
            throw new DomainException(413, "CONTENT.FILE.TOO_LARGE",
                    "El archivo supera el tamano maximo de %d bytes".formatted(maxFileSizeBytes))
                    .with("sizeBytes", sizeBytes)
                    .with("maxBytes", maxFileSizeBytes);
        }

        String storageKey = storage.store(filename, data);

        ContentFile file = new ContentFile();
        file.nodeId = nodeId;
        file.filename = filename;
        file.contentType = contentType == null ? "application/octet-stream" : contentType;
        file.sizeBytes = sizeBytes;
        file.storageKey = storageKey;
        fileRepository.persist(file);
        return file;
    }

    public List<ContentFile> listByNode(UUID nodeId) {
        // Valida que el nodo exista para no devolver una lista vacia silenciosa.
        nodeService.get(nodeId);
        return fileRepository.findByNode(nodeId);
    }

    public ContentFile get(UUID id) {
        ContentFile file = fileRepository.findById(id);
        if (file == null) {
            throw new NotFoundException("CONTENT.FILE.NOT_FOUND",
                    "No existe el archivo %s".formatted(id));
        }
        return file;
    }

    /**
     * Genera el enlace de descarga temporal (URL pre-firmada, BE-CNT-003): valida que
     * el archivo exista y firma un token expirante que el endpoint de descarga exige.
     */
    public DownloadLinkResponse buildDownloadLink(UUID id) {
        ContentFile file = get(id);
        String token = downloadTokens.issue(file.id);
        String url = "/content/files/%s/download?token=%s".formatted(file.id, token);
        return DownloadLinkResponse.of(url, downloadTokens.expiryFromNow());
    }

    /**
     * Abre un archivo para streaming tras validar el token de descarga (⇒ {@code 403}
     * si es invalido/expirado). Devuelve metadatos + stream ya abierto del binario.
     */
    public Download openForDownload(UUID id, String token) {
        ContentFile file = get(id);
        downloadTokens.verify(file.id, token);
        InputStream stream = storage.open(file.storageKey);
        return new Download(file, stream);
    }

    @Transactional
    public void delete(UUID id) {
        ContentFile file = get(id);
        fileRepository.delete(file);
        storage.delete(file.storageKey);
    }

    /** Metadatos + binario abierto de una descarga autorizada. */
    public record Download(ContentFile file, InputStream stream) {
    }
}
