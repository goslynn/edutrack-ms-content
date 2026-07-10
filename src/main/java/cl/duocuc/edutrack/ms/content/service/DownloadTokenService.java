package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Emite y verifica <b>tokens de descarga firmados y expirantes</b>: el equivalente,
 * agnostico del backend, a la "URL pre-firmada con expiracion" de la spec (BE-CNT-003).
 * Con FileSystem no existe un presign nativo de S3, asi que el propio MS firma un token
 * HMAC-SHA256 sobre {@code fileId|expiraEpoch} con un secreto de servicio; el endpoint
 * de descarga solo entrega el binario si el token es autentico y no ha expirado — "sin
 * URL valida el archivo es inaccesible".
 *
 * <p>El seam es limpio: al migrar a S3, {@code FileService#buildDownloadUrl} devolveria
 * la URL pre-firmada real del SDK en lugar de este token, sin cambiar el contrato del
 * endpoint que la solicita.</p>
 */
@ApplicationScoped
public class DownloadTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";

    private final byte[] secret;
    private final Duration ttl;

    public DownloadTokenService(
            @ConfigProperty(name = "edutrack.content.download.secret", defaultValue = "dev-content-download-secret")
            String secret,
            @ConfigProperty(name = "edutrack.content.download.ttl", defaultValue = "PT10M")
            Duration ttl) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
    }

    /** TTL configurado de los tokens (para reportar {@code expiresAt} al cliente). */
    public Duration ttl() {
        return ttl;
    }

    /** Instante de expiracion de un token emitido ahora. */
    public Instant expiryFromNow() {
        return Instant.now().plus(ttl);
    }

    /**
     * Firma un token para descargar {@code fileId}, valido hasta {@link #expiryFromNow()}.
     * Formato: {@code <expiraEpochSeconds>.<firmaBase64Url>}.
     */
    public String issue(UUID fileId) {
        long exp = expiryFromNow().getEpochSecond();
        return exp + "." + sign(fileId, exp);
    }

    /**
     * Verifica que {@code token} autoriza la descarga de {@code fileId}: firma valida y
     * no expirado. Lanza {@code 403} en cualquier fallo (token ausente, malformado,
     * adulterado o expirado) — no se distingue el motivo para no filtrar informacion.
     */
    public void verify(UUID fileId, String token) {
        if (token == null || token.isBlank()) {
            throw forbidden();
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            throw forbidden();
        }
        long exp;
        try {
            exp = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException e) {
            throw forbidden();
        }
        String provided = token.substring(dot + 1);
        String expected = sign(fileId, exp);
        if (!constantTimeEquals(expected, provided)) {
            throw forbidden();
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw forbidden();
        }
    }

    private String sign(UUID fileId, long exp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            byte[] raw = mac.doFinal((fileId + "|" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new DomainException(500, "CONTENT.DOWNLOAD.SIGN_FAILED",
                    "No se pudo firmar el token de descarga", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) {
            diff |= x[i] ^ y[i];
        }
        return diff == 0;
    }

    private static DomainException forbidden() {
        return new DomainException(403, "CONTENT.DOWNLOAD.INVALID_TOKEN",
                "Token de descarga invalido o expirado");
    }
}
