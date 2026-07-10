package cl.duocuc.edutrack.ms.content.model.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Enlace de descarga temporal (BE-CNT-003): equivalente a una URL pre-firmada. El
 * cliente hace {@code GET} sobre {@link #url} antes de {@link #expiresAt}; pasado ese
 * instante el enlace deja de servir el archivo.
 */
@Schema(description = "Enlace de descarga temporal (URL pre-firmada)")
public record DownloadLinkResponse(
        @Schema(description = "URL relativa de descarga con token firmado")
        String url,
        @Schema(description = "Instante de expiracion del enlace (UTC)")
        Instant expiresAt) {

    public static DownloadLinkResponse of(String url, Instant expiresAt) {
        return new DownloadLinkResponse(url, expiresAt);
    }
}
