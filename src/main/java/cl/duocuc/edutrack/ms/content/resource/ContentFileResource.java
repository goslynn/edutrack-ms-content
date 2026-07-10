package cl.duocuc.edutrack.ms.content.resource;

import cl.duocuc.edutrack.ms.content.model.dto.DownloadLinkResponse;
import cl.duocuc.edutrack.ms.content.model.dto.FileResponse;
import cl.duocuc.edutrack.ms.content.security.ContentResourceId;
import cl.duocuc.edutrack.ms.content.service.ContentFileService;
import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import cl.duocuc.edutrack.ms.infrastructure.security.Permission;
import cl.duocuc.edutrack.ms.infrastructure.security.RequirePermission;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/**
 * Archivos de nodos hoja (BE-CNT-003/004): subida multipart con limite de tamano
 * (⇒ {@code 413}), descarga mediante enlace temporal firmado (URL pre-firmada) y
 * borrado. La descarga en si ({@code /download}) no exige permiso Unix-style: la
 * autoriza el token del enlace ("sin URL valida el archivo es inaccesible").
 */
@Path("/files")
@Tag(name = "Content Files", description = "Archivos en nodos hoja con descarga por URL pre-firmada")
public class ContentFileResource {

    @Inject
    ContentFileService fileService;

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Sube un archivo a un nodo hoja (nodo no hoja => 422; > tamano max => 413)")
    @RequirePermission(resource = ContentResourceId.FILES, value = Permission.WRITE)
    public Response upload(@RestForm("nodeId") UUID nodeId, @RestForm("file") FileUpload file) {
        if (nodeId == null) {
            throw new DomainException(400, "CONTENT.FILE.NODE_REQUIRED",
                    "Falta el campo 'nodeId' en el formulario");
        }
        if (file == null) {
            throw new DomainException(400, "CONTENT.FILE.FILE_REQUIRED",
                    "Falta el campo 'file' en el formulario");
        }
        try (InputStream data = Files.newInputStream(file.uploadedFile())) {
            var stored = fileService.upload(nodeId, file.fileName(), file.contentType(),
                    file.size(), data);
            return Response.status(Response.Status.CREATED)
                    .entity(FileResponse.fromEntity(stored))
                    .build();
        } catch (IOException e) {
            throw new DomainException(500, "CONTENT.FILE.READ_UPLOAD_FAILED",
                    "No se pudo leer el archivo subido", e);
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lista los archivos de un nodo")
    @RequirePermission(resource = ContentResourceId.FILES, value = Permission.READ)
    public List<FileResponse> listByNode(@QueryParam("nodeId") UUID nodeId) {
        if (nodeId == null) {
            throw new DomainException(400, "CONTENT.FILE.NODE_REQUIRED",
                    "Falta el parametro 'nodeId'");
        }
        return fileService.listByNode(nodeId).stream().map(FileResponse::fromEntity).toList();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Metadatos de un archivo")
    @RequirePermission(resource = ContentResourceId.FILES, value = Permission.READ)
    public FileResponse get(@PathParam("id") UUID id) {
        return FileResponse.fromEntity(fileService.get(id));
    }

    @GET
    @Path("/{id}/link")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Genera un enlace de descarga temporal (URL pre-firmada)")
    @RequirePermission(resource = ContentResourceId.FILES, value = Permission.READ)
    public DownloadLinkResponse link(@PathParam("id") UUID id) {
        return fileService.buildDownloadLink(id);
    }

    @GET
    @Path("/{id}/download")
    @Operation(summary = "Descarga el binario con un token firmado (token invalido/expirado => 403)")
    public Response download(@PathParam("id") UUID id, @QueryParam("token") String token) {
        ContentFileService.Download dl = fileService.openForDownload(id, token);
        return Response.ok(dl.stream(), dl.file().contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s\"".formatted(dl.file().filename))
                .header(HttpHeaders.CONTENT_LENGTH, dl.file().sizeBytes)
                .build();
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Elimina un archivo (metadatos + binario)")
    @RequirePermission(resource = ContentResourceId.FILES, value = Permission.WRITE)
    public Response delete(@PathParam("id") UUID id) {
        fileService.delete(id);
        return Response.noContent().build();
    }
}
