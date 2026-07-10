package cl.duocuc.edutrack.ms.content.service;

import cl.duocuc.edutrack.ms.infrastructure.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token de descarga firmado y expirante (equivalente agnostico de la URL pre-firmada,
 * BE-CNT-003). Firma HMAC pura, sin Quarkus.
 */
class DownloadTokenServiceTest {

    private DownloadTokenService svc(Duration ttl) {
        return new DownloadTokenService("un-secreto-de-prueba", ttl);
    }

    @Test
    void issueThenVerify_roundtrips() {
        DownloadTokenService svc = svc(Duration.ofMinutes(10));
        UUID fileId = UUID.randomUUID();

        String token = svc.issue(fileId);

        assertDoesNotThrow(() -> svc.verify(fileId, token));
    }

    @Test
    void verify_wrongFileId_throws403() {
        DownloadTokenService svc = svc(Duration.ofMinutes(10));
        String token = svc.issue(UUID.randomUUID());

        DomainException ex = assertThrows(DomainException.class,
                () -> svc.verify(UUID.randomUUID(), token));
        assertEquals(403, ex.status());
    }

    @Test
    void verify_tamperedSignature_throws403() {
        DownloadTokenService svc = svc(Duration.ofMinutes(10));
        UUID fileId = UUID.randomUUID();
        String token = svc.issue(fileId);
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertEquals(403, assertThrows(DomainException.class,
                () -> svc.verify(fileId, tampered)).status());
    }

    @Test
    void verify_expiredToken_throws403() {
        DownloadTokenService svc = svc(Duration.ofSeconds(-1)); // ya expirado al emitir
        UUID fileId = UUID.randomUUID();
        String token = svc.issue(fileId);

        assertEquals(403, assertThrows(DomainException.class,
                () -> svc.verify(fileId, token)).status());
    }

    @Test
    void verify_nullOrMalformed_throws403() {
        DownloadTokenService svc = svc(Duration.ofMinutes(10));
        UUID fileId = UUID.randomUUID();

        assertEquals(403, assertThrows(DomainException.class, () -> svc.verify(fileId, null)).status());
        assertEquals(403, assertThrows(DomainException.class, () -> svc.verify(fileId, "")).status());
        assertEquals(403, assertThrows(DomainException.class, () -> svc.verify(fileId, "sinpunto")).status());
    }
}
