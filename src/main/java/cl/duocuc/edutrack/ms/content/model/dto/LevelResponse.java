package cl.duocuc.edutrack.ms.content.model.dto;

import cl.duocuc.edutrack.ms.content.model.entity.ContentLevel;
import cl.duocuc.edutrack.ms.infrastructure.jackson.Views;
import com.fasterxml.jackson.annotation.JsonView;

import java.time.Instant;
import java.util.UUID;

public record LevelResponse(
        UUID id,
        int depth,
        String name,
        String description,
        @JsonView({Views.Detailed.class, Views.List.class}) Instant createdAt) {

    public static LevelResponse fromEntity(ContentLevel level) {
        return new LevelResponse(level.id, level.depth, level.name, level.description, level.createdAt);
    }
}
