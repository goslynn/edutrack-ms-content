package cl.duocuc.edutrack.ms.content.resource;

import cl.duocuc.edutrack.ms.content.model.dto.LevelRequest;
import cl.duocuc.edutrack.ms.content.model.dto.LevelResponse;
import cl.duocuc.edutrack.ms.content.model.entity.ContentLevel;
import cl.duocuc.edutrack.ms.content.security.ContentResourceId;
import cl.duocuc.edutrack.ms.content.service.ContentLevelService;
import cl.duocuc.edutrack.ms.infrastructure.security.Permission;
import cl.duocuc.edutrack.ms.infrastructure.security.RequirePermission;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * CRUD de la jerarquia de niveles del arbol de contenido (BE-CNT-001). Lectura ⇒
 * {@code READ}, mutacion ⇒ {@code WRITE} sobre {@code content.levels}.
 */
@Path("/levels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Levels", description = "Jerarquia global configurable (Semestre > Asignatura > Unidad > Clase)")
public class ContentLevelResource {

    @Inject
    ContentLevelService levelService;

    @GET
    @Operation(summary = "Lista los niveles ordenados por profundidad")
    @RequirePermission(resource = ContentResourceId.LEVELS, value = Permission.READ)
    public List<LevelResponse> list() {
        return levelService.list().stream().map(LevelResponse::fromEntity).toList();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Obtiene un nivel por id")
    @RequirePermission(resource = ContentResourceId.LEVELS, value = Permission.READ)
    public LevelResponse get(@PathParam("id") UUID id) {
        return LevelResponse.fromEntity(levelService.get(id));
    }

    @POST
    @Operation(summary = "Crea un nivel (profundidad duplicada => 409)")
    @RequirePermission(resource = ContentResourceId.LEVELS, value = Permission.WRITE)
    public Response create(@Valid LevelRequest req) {
        ContentLevel level = levelService.create(req);
        return Response.status(Response.Status.CREATED)
                .entity(LevelResponse.fromEntity(level))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualiza un nivel")
    @RequirePermission(resource = ContentResourceId.LEVELS, value = Permission.WRITE)
    public LevelResponse update(@PathParam("id") UUID id, @Valid LevelRequest req) {
        return LevelResponse.fromEntity(levelService.update(id, req));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Elimina un nivel")
    @RequirePermission(resource = ContentResourceId.LEVELS, value = Permission.WRITE)
    public Response delete(@PathParam("id") UUID id) {
        levelService.delete(id);
        return Response.noContent().build();
    }
}
