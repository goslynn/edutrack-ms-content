package cl.duocuc.edutrack.ms.content.model.dto;

import cl.duocuc.edutrack.ms.content.model.entity.ContentNode;
import cl.duocuc.edutrack.ms.infrastructure.jackson.Views;
import com.fasterxml.jackson.annotation.JsonView;

import java.time.Instant;
import java.util.UUID;

/**
 * Vista de un nodo. {@code leaf} indica si el nodo esta en el nivel hoja (admite
 * archivos); lo aporta el servicio, que conoce la profundidad maxima de la jerarquia.
 */
public record NodeResponse(
        UUID id,
        String name,
        String description,
        int orderIndex,
        UUID levelId,
        UUID parentId,
        boolean leaf,
        @JsonView({Views.Detailed.class, Views.List.class}) Instant createdAt,
        @JsonView({Views.Detailed.class}) Instant updatedAt) {

    public static NodeResponse fromEntity(ContentNode node, boolean leaf) {
        return new NodeResponse(node.id, node.name, node.description, node.orderIndex,
                node.levelId, node.parentId, leaf, node.createdAt, node.updatedAt);
    }
}
