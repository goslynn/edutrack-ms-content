package cl.duocuc.edutrack.ms.content.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.UUID;

/**
 * Alta/edicion de un nodo del arbol (BE-CNT-001/002). {@code levelId} y {@code parentId}
 * los valida el servicio contra la jerarquia activa: un nodo de profundidad N debe
 * colgar de uno de profundidad N-1 (el raiz no lleva {@code parentId}).
 */
@Schema(description = "Nodo del arbol de contenido")
public record NodeRequest(
        @Schema(description = "Nombre del nodo", examples = "Unidad 1: Introduccion")
        @NotBlank @Size(max = 150) String name,

        @Schema(description = "Descripcion opcional")
        @Size(max = 500) String description,

        @Schema(description = "Orden entre hermanos", examples = "0")
        Integer orderIndex,

        @Schema(description = "Nivel al que pertenece el nodo")
        @NotNull UUID levelId,

        @Schema(description = "Nodo padre; omitir/null solo para nodos raiz")
        UUID parentId) {
}
