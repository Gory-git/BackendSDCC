package org.backendsdcc.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.time.Duration;
import java.util.UUID;

@Service
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;


    public S3Service(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    /** Chiave nuova per un PDF che non ne ha ancora una. */
    public static String newKey(String prefix) {
        return prefix + "/" + UUID.randomUUID() + ".pdf";
    }

    /**
     * Carica il PDF sulla chiave indicata. Passando la chiave già associata alla
     * ricevuta si sovrascrive l'oggetto esistente invece di lasciarne uno orfano
     * nel bucket a ogni download.
     */
    public String uploadPDF(byte[] pdfBytes, String key) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/pdf")
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(pdfBytes));
        return key;
    }

    /**
     * L'URL porta con sé il nome con cui il browser salverà il file: la chiave S3
     * resta un UUID (nessuna collisione, nessun carattere da sanificare in un
     * identificatore di oggetto), ma chi scarica vede il codice della ricevuta.
     */
    public String generatePresignedUrl(String s3Key, int minutes, String downloadFileName) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .responseContentDisposition(
                        "attachment; filename=\"" + safeFileName(downloadFileName) + ".pdf\"")
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(minutes))
                .getObjectRequest(getRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url()
                .toString();
    }

    /**
     * Il codice della ricevuta finisce in un header HTTP: tutto ciò che non è
     * alfanumerico, punto, trattino o underscore viene sostituito, per non
     * lasciare che un codice scritto dall'utente inietti roba nel Content-Disposition.
     */
    private static String safeFileName(String value) {
        if (value == null || value.isBlank()) return "ricevuta";
        String cleaned = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "ricevuta" : cleaned;
    }

    public void deletePDF(String s3Key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        s3Client.deleteObject(request);
    }


}