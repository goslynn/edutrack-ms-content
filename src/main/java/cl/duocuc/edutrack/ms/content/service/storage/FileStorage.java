package cl.duocuc.edutrack.ms.content.service.storage;

import java.io.InputStream;

/**
 * Contrato de <b>almacenamiento de binarios</b> (Strategy). Abstrae donde y como se
 * guardan los archivos de contenido para que el dominio (nodos, metadatos, permisos)
 * no dependa del backend concreto.
 *
 * <p>La v1 lo implementa {@link FileSystemFileStorage} (disco local sobre un volumen
 * persistente); migrar a S3 u otro objeto-store es escribir una nueva implementacion
 * de esta interfaz y publicarla como el bean por defecto — sin tocar servicios ni
 * endpoints (BE-CNT-003/004). Por eso el contrato es minimo y agnostico: no expone
 * rutas de disco, buckets ni URLs.</p>
 */
public interface FileStorage {

    /**
     * Persiste el binario leido de {@code data} y devuelve una <b>clave opaca</b> con
     * la que recuperarlo o borrarlo despues. El llamante cierra {@code data}? No: la
     * implementacion consume el stream completo; el cierre queda del lado del caller
     * (que suele obtenerlo de un {@code FileUpload}).
     *
     * @param originalFilename nombre original (solo para derivar extension/legibilidad;
     *                         nunca se usa como ruta tal cual)
     * @param data             contenido del archivo
     * @return clave de almacenamiento opaca (se guarda en {@code content_files.storage_key})
     */
    String store(String originalFilename, InputStream data);

    /**
     * Abre el binario identificado por {@code storageKey} para lectura/streaming.
     *
     * @throws cl.duocuc.edutrack.ms.infrastructure.exception.NotFoundException si la
     *         clave no existe en el backend
     */
    InputStream open(String storageKey);

    /** Borra el binario. Idempotente: borrar una clave inexistente no falla. */
    void delete(String storageKey);
}
