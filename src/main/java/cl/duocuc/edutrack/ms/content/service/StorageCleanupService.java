package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.content.service.storage.FileStorage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Limpieza <b>asincrona y best-effort</b> de los binarios que quedan huerfanos al borrar
 * un nodo. El borrado de un nodo cascada las filas de {@code content_files} en la BD, pero
 * el {@link FileStorage} (disco/S3) no se entera: sin esta limpieza los binarios quedarian
 * para siempre en el almacenamiento.
 *
 * <h3>Flujo</h3>
 * <ol>
 *   <li>{@code ContentNodeService.delete} (transaccional) recolecta las {@code storage_key}
 *       del subarbol <b>antes</b> de borrar y dispara {@link OrphanBlobs}.</li>
 *   <li>{@link #onNodeSubtreeDeleted} observa el evento en fase
 *       {@link TransactionPhase#AFTER_SUCCESS}: corre <b>solo si el commit tuvo exito</b>
 *       (si el borrado hace rollback, no se toca el disco).</li>
 *   <li>El observer solo <b>encola</b> el trabajo en un hilo ({@link #submitDeletion}) y
 *       retorna de inmediato ⇒ el request responde rapido; el IO de disco ocurre fuera del
 *       hilo del request.</li>
 * </ol>
 *
 * <h3>Politica de error</h3>
 * <p>El borrado es <b>best-effort</b>: si un binario falla, se <b>omite</b> (a lo sumo se
 * loguea un warning) y el trabajo <b>continua</b> con el resto de la lista hasta agotarla.
 * Un binario que no se pudo borrar simplemente queda como huerfano — nunca detiene la
 * limpieza de los demas ni propaga el error.</p>
 */
@ApplicationScoped
public class StorageCleanupService {

    private static final Logger LOG = Logger.getLogger(StorageCleanupService.class);

    @Inject
    FileStorage storage;

    private ExecutorService executor;

    @PostConstruct
    void start() {
        // Un hilo virtual por tarea (Java 21): ideal para IO bloqueante de borrado de
        // binarios (disco hoy, red con S3 manana) sin dimensionar un pool.
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    /**
     * Observer transaccional: se dispara despues del commit del borrado del nodo y delega
     * la limpieza a un hilo. No hace IO en linea para no bloquear el hilo que commitea.
     */
    void onNodeSubtreeDeleted(@Observes(during = TransactionPhase.AFTER_SUCCESS) OrphanBlobs event) {
        submitDeletion(event.storageKeys());
    }

    /** Encola el borrado best-effort de los binarios en un hilo; retorna de inmediato. */
    public void submitDeletion(Collection<String> storageKeys) {
        if (storageKeys == null || storageKeys.isEmpty()) {
            return;
        }
        executor.submit(() -> deleteAll(storageKeys));
    }

    /**
     * Borra los binarios uno a uno. <b>Best-effort:</b> un fallo se omite (a lo sumo se
     * loguea) y NO detiene el trabajo — se continua hasta agotar la lista. Paquete-privado
     * para poder ejercitar la politica de forma sincrona en tests.
     */
    void deleteAll(Collection<String> storageKeys) {
        int deleted = 0;
        int skipped = 0;
        for (String key : storageKeys) {
            try {
                storage.delete(key);
                deleted++;
            } catch (RuntimeException e) {
                skipped++;
                LOG.warnf(e, "No se pudo borrar el binario huerfano '%s'; se omite y se continua", key);
            }
        }
        LOG.infof("Limpieza de binarios huerfanos finalizada: %d borrados, %d omitidos", deleted, skipped);
    }
}
