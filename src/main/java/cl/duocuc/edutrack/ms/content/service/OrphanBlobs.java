package cl.duocuc.edutrack.ms.content.service;

import java.util.List;

/**
 * Evento CDI: al borrar un nodo (y su subarbol por cascada), estas son las
 * {@code storage_key} de los binarios que quedaron sin fila que los referencie. Se
 * dispara dentro de la transaccion del borrado; un observer transaccional
 * ({@code AFTER_SUCCESS}) los limpia solo si el commit tuvo exito, de forma asincrona.
 *
 * @param storageKeys claves de almacenamiento a borrar del backend de archivos
 */
public record OrphanBlobs(List<String> storageKeys) {
}
