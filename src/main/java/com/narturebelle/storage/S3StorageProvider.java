package com.narturebelle.storage;

import com.narturebelle.model.ScanVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Private AWS S3 bucket in Mumbai (ap-south-1) for mothers' files (F9-S19, D48). Only exists when
 * S3_BUCKET is set, so the app still starts on Supabase alone. Every setting comes from environment
 * variables: no keys in code. Access keys use NB_S3_* names because Vercel reserves the AWS_* ones;
 * without them the SDK's default chain is used (for example an IAM role).
 *
 * Nothing here is ever public: uploads and downloads go through presigned links only, and a bucket
 * policy (S3_SETUP.md) refuses downloads of anything GuardDuty hasn't tagged clean.
 */
@Component
@ConditionalOnExpression("'${storage.s3.bucket:}' != ''")
public class S3StorageProvider implements StorageProvider {

    /** The tag GuardDuty Malware Protection for S3 writes on every object it scans. */
    static final String SCAN_TAG = "GuardDutyMalwareScanStatus";
    /** CopyObject's limit; restoring anything larger would need a multipart copy. */
    static final long MAX_SINGLE_COPY_BYTES = 5L * 1024 * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(S3StorageProvider.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    /** The constructor Spring uses. @Autowired is required: the class also has a test-only constructor. */
    @Autowired
    public S3StorageProvider(@Value("${storage.s3.bucket}") String bucket,
                             @Value("${storage.s3.region:ap-south-1}") String region,
                             @Value("${storage.s3.access-key-id:}") String accessKeyId,
                             @Value("${storage.s3.secret-access-key:}") String secretAccessKey) {
        AwsCredentialsProvider credentials = !accessKeyId.isBlank() && !secretAccessKey.isBlank()
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
                : DefaultCredentialsProvider.builder().build();
        Region awsRegion = Region.of(region);
        this.bucket = bucket;
        this.s3 = S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentials)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
        this.presigner = S3Presigner.builder()
                .region(awsRegion)
                .credentialsProvider(credentials)
                .build();
    }

    /** For tests: a stubbed client and an offline presigner. */
    S3StorageProvider(String bucket, S3Client s3, S3Presigner presigner) {
        this.bucket = bucket;
        this.s3 = s3;
        this.presigner = presigner;
    }

    public String bucket() {
        return bucket;
    }

    // ---- StorageProvider ----

    @Override
    public SignedUploadUrl createSignedUploadUrl(String bucket, String objectPath, Duration expiry) {
        return createSignedUploadUrl(bucket, objectPath, null, expiry);
    }

    @Override
    public SignedDownloadUrl createSignedDownloadUrl(String bucket, String objectPath, Duration expiry) {
        return createSignedDownloadUrl(bucket, objectPath, expiry, null, null);
    }

    @Override
    public boolean objectExists(String bucket, String objectPath) {
        return describe(bucket, objectPath).isPresent();
    }

    @Override
    public void deleteObject(String bucket, String objectPath) {
        // Versioned bucket: this adds a delete marker; the bytes stay as a noncurrent version until the
        // lifecycle rule removes them (S3_SETUP.md).
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectPath).build());
    }

    // ---- S3-only ----

    /** Presigned PUT. When contentType is set it is signed, so the browser must send exactly that Content-Type. */
    public SignedUploadUrl createSignedUploadUrl(String bucket, String objectPath, String contentType, Duration expiry) {
        PutObjectRequest.Builder put = PutObjectRequest.builder().bucket(bucket).key(objectPath);
        if (contentType != null) put.contentType(contentType);
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(put.build())
                .build());
        return new SignedUploadUrl(presigned.url().toString(), null, LocalDateTime.now().plus(expiry));
    }

    /** Presigned GET. contentDisposition "inline" plays in a video tag; "attachment; filename=..." makes phones save it. */
    public SignedDownloadUrl createSignedDownloadUrl(String bucket, String objectPath, Duration expiry,
                                                     String contentType, String contentDisposition) {
        GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(bucket).key(objectPath);
        if (contentType != null) get.responseContentType(contentType);
        if (contentDisposition != null) get.responseContentDisposition(contentDisposition);
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(get.build())
                .build());
        return new SignedDownloadUrl(presigned.url().toString(), LocalDateTime.now().plus(expiry));
    }

    /**
     * Real size and creation time. Uses GetObjectAttributes rather than HEAD, because the bucket policy
     * denies s3:GetObject (and so HEAD) until GuardDuty has tagged the object clean.
     */
    public Optional<StoredObjectInfo> describe(String bucket, String objectPath) {
        try {
            var attrs = s3.getObjectAttributes(GetObjectAttributesRequest.builder()
                    .bucket(bucket).key(objectPath)
                    .objectAttributes(ObjectAttributes.OBJECT_SIZE)
                    .build());
            return Optional.of(new StoredObjectInfo(attrs.objectSize(), attrs.lastModified()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return Optional.empty();
            throw e;
        }
    }

    public String startMultipartUpload(String bucket, String objectPath, String contentType) {
        return s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(objectPath).contentType(contentType).build()).uploadId();
    }

    public SignedUploadUrl presignUploadPart(String bucket, String objectPath, String uploadId, int partNumber, Duration expiry) {
        var presigned = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                .signatureDuration(expiry)
                .uploadPartRequest(UploadPartRequest.builder()
                        .bucket(bucket).key(objectPath).uploadId(uploadId).partNumber(partNumber).build())
                .build());
        return new SignedUploadUrl(presigned.url().toString(), null, LocalDateTime.now().plus(expiry));
    }

    public void completeMultipartUpload(String bucket, String objectPath, String uploadId, List<UploadedPart> parts) {
        List<CompletedPart> completed = parts.stream()
                .sorted(Comparator.comparingInt(UploadedPart::partNumber))
                .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.etag()).build())
                .toList();
        s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucket).key(objectPath).uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                .build());
    }

    public void abortMultipartUpload(String bucket, String objectPath, String uploadId) {
        s3.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                .bucket(bucket).key(objectPath).uploadId(uploadId).build());
    }

    /**
     * Reads the tag GuardDuty Malware Protection writes after scanning. No tag yet means still scanning.
     * UNSUPPORTED, ACCESS_DENIED and FAILED become SCAN_FAILED: the file stays unavailable and support looks at it.
     */
    public ScanVerdict readScanStatus(String bucket, String objectPath) {
        var tags = s3.getObjectTagging(GetObjectTaggingRequest.builder().bucket(bucket).key(objectPath).build()).tagSet();
        return tags.stream()
                .filter(t -> SCAN_TAG.equals(t.key()))
                .<ScanVerdict>map(t -> switch (t.value()) {
                    case "NO_THREATS_FOUND" -> ScanVerdict.CLEAN;
                    case "THREATS_FOUND" -> ScanVerdict.INFECTED;
                    default -> ScanVerdict.SCAN_FAILED;
                })
                .findFirst()
                .orElse(ScanVerdict.PENDING);
    }

    /**
     * Support restore from trash (F9-S19 item 13): copies the newest real version back on top as a new
     * current version. That gives it a new creation date, so the lifecycle rule counts 30 days again, and
     * no tags, so GuardDuty scans it again. Empty if nothing is left (the trash period has passed).
     */
    public Optional<StoredObjectInfo> restoreLatestVersion(String bucket, String objectPath, String contentType) {
        var versions = s3.listObjectVersions(ListObjectVersionsRequest.builder()
                .bucket(bucket).prefix(objectPath).build()).versions();
        Optional<ObjectVersion> newest = versions.stream()
                .filter(v -> objectPath.equals(v.key()))
                .max(Comparator.comparing(ObjectVersion::lastModified));
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        if (newest.get().size() != null && newest.get().size() > MAX_SINGLE_COPY_BYTES) {
            throw new IllegalStateException("Video is over 5 GB; restore it from the AWS console instead.");
        }
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(bucket).sourceKey(objectPath).sourceVersionId(newest.get().versionId())
                .destinationBucket(bucket).destinationKey(objectPath)
                .metadataDirective(MetadataDirective.REPLACE)
                .contentType(contentType)
                .taggingDirective(TaggingDirective.REPLACE)
                .build());
        log.info("Restored a video version from trash");
        return describe(bucket, objectPath);
    }
}
