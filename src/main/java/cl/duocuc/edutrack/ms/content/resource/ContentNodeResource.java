package cl.duocuc.edutrack.ms.content.resource;

import cl.duocuc.edutrack.ms.content.model.dto.NodeRequest;
import cl.duocuc.edutrack.ms.content.model.dto.NodeResponse;
import cl.duocuc.edutrack.ms.content.model.entity.ContentNode;
import cl.duocuc.edutrack.ms.content.security.ContentResourceId;
import cl.duocuc.edutrack.ms.content.service.ContentNodeService;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

/**
 * CRUD de nodos del arbol respetando la relacion padre-hijo (BE-CNT-001/002). Lectura
 * ⇒ {@code READ}, mutacion ⇒ {@code WRITE} sobre {@code content.nodes}.
 */
@Path("/nodes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Nodes", description = "Arbol de nodos de contenido (validacion padre-hijo)")
public class ContentNodeResource {

    @Inject
    ContentNodeService nodeService;

    @GET
    @Operation(summary = "Lista nodos hijos de 'parentId' (o los nodos raiz si se omite)")
    @RequirePermission(resource = ContentResourceId.NODES, value = Permission.READ)
    public List<NodeResponse> list(@QueryParam("parentId") UUID parentId) {
        return nodeService.children(parentId).stream()
                .map(n -> NodeResponse.fromEntity(n, nodeService.isLeaf(n)))
                .toList();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Obtiene un nodo por id")
    @RequirePermission(resource = ContentResourceId.NODES, value = Permission.READ)
    public NodeResponse get(@PathParam("id") UUID id) {
        ContentNode node = nodeService.get(id);
        return NodeResponse.fromEntity(node, nodeService.isLeaf(node));
    }

    @POST
    @Operation(summary = "Crea un nodo (violacion de jerarquia padre-hijo => 422)")
    @RequirePermission(resource = ContentResourceId.NODES, value = Permission.WRITE)
    public Response create(@Valid NodeRequest req) {
        ContentNode node = nodeService.create(req);
        return Response.status(Response.Status.CREATED)
                .entity(NodeResponse.fromEntity(node, nodeService.isLeaf(node)))
                .build();
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Actualiza atributos de un nodo (no mueve el nodo en el arbol)")
    @RequirePermission(resource = ContentResourceId.NODES, value = Permission.WRITE)
    public NodeResponse update(@PathParam("id") UUID id, @Valid NodeRequest req) {
        ContentNode node = nodeService.update(id, req);
        return NodeResponse.fromEntity(node, nodeService.isLeaf(node));
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Elimina un nodo y su subarbol (cascada)")
    @RequirePermission(resource = ContentResourceId.NODES, value = Permission.WRITE)
    public Response delete(@PathParam("id") UUID id) {
        nodeService.delete(id);
        return Response.noContent().build();
    }
}
