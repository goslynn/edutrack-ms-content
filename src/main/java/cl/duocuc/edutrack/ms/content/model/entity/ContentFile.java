package cl.duocuc.edutrack.ms.content.model.entity;

import cl.duocuc.edutrack.ms.infrastructure.persistence.CreatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Metadatos de un archivo subido a un nodo <b>hoja</b> (BE-CNT-003/004). El binario
 * vive en el backend de almacenamiento (v1: FileSystem; futuro: S3) referenciado por
 * {@link #storageKey}; esta fila solo guarda los metadatos consultables.
 *
 * <p>Es <b>append-only</b> (extiende {@link CreatableEntity}): un archivo se sube una
 * vez y no se edita; reemplazarlo es borrar y volver a subir. De ahi que no lleve
 * {@code updated_at}.</p>
 */
@Entity
@Table(name = "content_files", schema = "content")
public class ContentFile extends CreatableEntity {

    /** Nodo hoja al que pertenece el archivo. */
    @Column(name = "node_id", columnDefinition = "uuid", nullable = false)
    public UUID nodeId;

    /** Nombre original del archivo tal como lo subio el usuario. */
    @Column(name = "filename", nullable = false, length = 255)
    public String filename;

    @Column(name = "content_type", nullable = false, length = 150)
    public String contentType;

    @Column(name = "size_bytes", nullable = false)
    public long sizeBytes;

    /** Clave opaca que devuelve el {@code FileStorage} para recuperar/borrar el binario. */
    @Column(name = "storage_key", nullable = false, length = 512)
    public String storageKey;
}
