package cl.duocuc.edutrack.ms.content.security;

/**
 * Catalogo de <em>resource keys</em> que el Content Service protege con permisos
 * Unix-style. Claves estables y legibles ({@code "content.<recurso>"}, forma
 * punteada), opacas para Auth (comparadas por igualdad) y que constituyen el
 * contrato cross-servicio: el mismo string nombra el recurso en ambos lados de un
 * grant.
 *
 * <p>Se usan como valor de {@code @RequirePermission(resource = ...)} y deben
 * coincidir con los grants sembrados en Auth. El wildcard global vive en
 * {@code infrastructure.security.ResourceIds#ALL}.</p>
 */
public interface ContentResourceId {

    /** Configuracion de niveles del arbol: {@code /content/levels} (BE-CNT-001). */
    String LEVELS = "content.levels";

    /** CRUD de nodos del arbol: {@code /content/nodes} (BE-CNT-001/002). */
    String NODES = "content.nodes";

    /** Subida/descarga/borrado de archivos en nodos hoja: {@code /content/.../files} (BE-CNT-003/004). */
    String FILES = "content.files";
}
