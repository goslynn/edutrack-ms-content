package cl.duocuc.edutrack.ms.content.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Alta/edicion de un nivel del arbol de contenido (BE-CNT-001). La {@code depth} fija
 * la posicion vertical del nivel (0 = raiz); debe ser unica.
 */
@Schema(description = "Definicion de un nivel de la jerarquia de contenido")
public record LevelRequest(
        @Schema(description = "Profundidad 0-based; 0 es la raiz. Unica.", examples = "1")
        @NotNull @Min(0) Integer depth,

        @Schema(description = "Nombre del nivel", examples = "Asignatura")
        @NotBlank @Size(max = 100) String name,

        @Schema(description = "Descripcion opcional")
        @Size(max = 255) String description) {
}
