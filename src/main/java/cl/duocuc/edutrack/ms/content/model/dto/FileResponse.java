package cl.duocuc.edutrack.ms.content.model.dto;

import cl.duocuc.edutrack.ms.content.model.entity.ContentFile;
import cl.duocuc.edutrack.ms.infrastructure.jackson.Views;
import com.fasterxml.jackson.annotation.JsonView;

import java.time.Instant;
import java.util.UUID;

/** Metadatos de un archivo subido a un nodo hoja (BE-CNT-003/004). */
public record FileResponse(
        UUID id,
        UUID nodeId,
        String filename,
        String contentType,
        long sizeBytes,
        @JsonView({Views.Detailed.class, Views.List.class}) Instant createdAt) {

    public static FileResponse fromEntity(ContentFile file) {
        return new FileResponse(file.id, file.nodeId, file.filename, file.contentType,
                file.sizeBytes, file.createdAt);
    }
}
