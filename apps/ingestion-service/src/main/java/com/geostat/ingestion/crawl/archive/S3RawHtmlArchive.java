package com.geostat.ingestion.crawl.archive;

import com.geostat.ingestion.config.IngestionProperties;
import java.net.URI;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** B-28 — S3-compatible raw HTML archive (MinIO, AWS S3). */
@Component
@ConditionalOnProperty(prefix = "geostat.ingestion.archive", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "geostat.ingestion.archive", name = "provider", havingValue = "s3")
public class S3RawHtmlArchive implements RawHtmlArchivePort {

    private final S3Client s3;
    private final String bucket;

    public S3RawHtmlArchive(IngestionProperties properties) {
        IngestionProperties.Archive archive = properties.archive();
        this.bucket = archive.bucket();
        var creds = AwsBasicCredentials.create(archive.accessKey(), archive.secretKey());
        var builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(creds))
                .region(Region.of(archive.region()));
        if (archive.endpoint() != null && !archive.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(archive.endpoint()))
                    .forcePathStyle(true);
        }
        this.s3 = builder.build();
    }

    @Override
    public Optional<String> store(String corpusName, String url, byte[] html) {
        String key = corpusName + "/" + Integer.toHexString(url.hashCode()) + ".html";
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/html").build(),
                RequestBody.fromBytes(html));
        return Optional.of(key);
    }

    @Override
    public Optional<byte[]> load(String archiveKey) {
        if (archiveKey == null || archiveKey.isBlank()) {
            return Optional.empty();
        }
        byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(archiveKey).build())
                .asByteArray();
        return Optional.of(bytes);
    }
}
