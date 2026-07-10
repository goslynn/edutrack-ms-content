package cl.duocuc.edutrack.ms.content;

import cl.duocuc.edutrack.ms.infrastructure.discovery.ServiceIds;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Info;

/**
 * Punto de montaje JAX-RS del Content Service. El {@link ApplicationPath} es el
 * primer segmento del path y, por el contrato de discovery del Gateway, debe
 * coincidir con el nombre logico del servicio ({@link ServiceIds#CONTENT}) y con el
 * nombre de la app en Fly.io ({@code edutrack-content}).
 */
@ApplicationPath("/" + ServiceIds.CONTENT)
@OpenAPIDefinition(info = @Info(
        title = "Content Service API",
        version = "1.0.0-SNAPSHOT",
        description = "Arbol jerarquico de contenido configurable + archivos en nodos hoja "
                + "(almacenamiento por Strategy; v1: FileSystem)"))
public class ContentApplication extends Application {
}
