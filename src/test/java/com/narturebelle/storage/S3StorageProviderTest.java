package com.narturebelle.storage;

import com.narturebelle.model.ScanVerdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** No network: the presigner signs offline with dummy keys, and the S3 client is a mock. */
class S3StorageProviderTest {

    private S3Client s3;
    private S3StorageProvider provider;

    /** Spring must build the real bean (a local run found it couldn't choose between the two constructors). */
    @Test
    void springBuildsTheRealBean_whenS3BucketIsSet_andNotWithoutIt() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(S3StorageProvider.class)
                .withPropertyValues("storage.s3.bucket=nb-files-test", "storage.s3.access-key-id=AKIATESTONLY",
                        "storage.s3.secret-access-key=test-secret")
                .run(ctx -> {
                    org.assertj.core.api.Assertions.assertThat(ctx).hasNotFailed();
                    assertEquals("nb-files-test", ctx.getBean(S3StorageProvider.class).bucket());
                });
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(S3StorageProvider.class)
                .run(ctx -> org.assertj.core.api.Assertions.assertThat(ctx).doesNotHaveBean(S3StorageProvider.class));
    }

    @BeforeEach
    void setUp() {
        s3 = mock(S3Client.class);
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_SOUTH_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATESTONLY", "test-secret")))
                .build();
        provider = new S3StorageProvider("nb-files-test", s3, presigner);
    }

    @Test
    void presignedPut_isMumbai_15Minutes_andSignsTheContentType() {
        SignedUploadUrl url = provider.createSignedUploadUrl("nb-files-test", "files/abc", "application/pdf", Duration.ofMinutes(15));

        assertTrue(url.uploadUrl().startsWith("https://nb-files-test.s3.ap-south-1.amazonaws.com/files/abc?"), url.uploadUrl());
        assertTrue(url.uploadUrl().contains("X-Amz-Expires=900"));
        assertTrue(url.uploadUrl().contains("X-Amz-SignedHeaders=content-type%3Bhost"), url.uploadUrl());
        assertNull(url.token());
    }

    @Test
    void presignedGet_is10Minutes_withDisposition() {
        SignedDownloadUrl url = provider.createSignedDownloadUrl("nb-files-test", "videos/xyz", Duration.ofMinutes(10),
                "video/mp4", "attachment; filename=\"NartureBelle-video-1.mp4\"");

        assertTrue(url.downloadUrl().contains("X-Amz-Expires=600"));
        assertTrue(url.downloadUrl().contains("response-content-disposition=attachment"));
        assertTrue(url.downloadUrl().contains("response-content-type=video%2Fmp4"));
    }

    @Test
    void presignedPart_carriesUploadIdAndPartNumber() {
        SignedUploadUrl url = provider.presignUploadPart("nb-files-test", "videos/xyz", "up-1", 3, Duration.ofMinutes(15));
        assertTrue(url.uploadUrl().contains("partNumber=3"));
        assertTrue(url.uploadUrl().contains("uploadId=up-1"));
    }

    @Test
    void scanTag_mapsGuardDutyResults() {
        assertEquals(ScanVerdict.PENDING, scanWithTags(List.of()));
        assertEquals(ScanVerdict.CLEAN, scanWithTags(List.of(tag("NO_THREATS_FOUND"))));
        assertEquals(ScanVerdict.INFECTED, scanWithTags(List.of(tag("THREATS_FOUND"))));
        assertEquals(ScanVerdict.SCAN_FAILED, scanWithTags(List.of(tag("UNSUPPORTED"))));
        assertEquals(ScanVerdict.SCAN_FAILED, scanWithTags(List.of(tag("FAILED"))));
    }

    @Test
    void describe_missingObject_isEmpty_andExistingGivesSizeAndTime() {
        when(s3.getObjectAttributes(any(GetObjectAttributesRequest.class)))
                .thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertTrue(provider.describe("nb-files-test", "files/none").isEmpty());
        assertFalse(provider.objectExists("nb-files-test", "files/none"));

        Instant created = Instant.parse("2026-11-01T10:00:00Z");
        reset(s3);
        when(s3.getObjectAttributes(any(GetObjectAttributesRequest.class)))
                .thenReturn(GetObjectAttributesResponse.builder().objectSize(1234L).lastModified(created).build());
        Optional<StoredObjectInfo> info = provider.describe("nb-files-test", "files/one");
        assertEquals(1234L, info.orElseThrow().sizeBytes());
        assertEquals(created, info.get().lastModified());
    }

    @Test
    void restore_copiesNewestVersionBackWithoutTags_soItIsScannedAgain() {
        when(s3.listObjectVersions(any(ListObjectVersionsRequest.class))).thenReturn(ListObjectVersionsResponse.builder()
                .versions(
                        ObjectVersion.builder().key("videos/v").versionId("old").size(10L).lastModified(Instant.parse("2026-10-01T00:00:00Z")).build(),
                        ObjectVersion.builder().key("videos/v").versionId("new").size(10L).lastModified(Instant.parse("2026-10-02T00:00:00Z")).build(),
                        ObjectVersion.builder().key("videos/v-other").versionId("x").size(10L).lastModified(Instant.parse("2026-10-03T00:00:00Z")).build())
                .build());
        when(s3.getObjectAttributes(any(GetObjectAttributesRequest.class)))
                .thenReturn(GetObjectAttributesResponse.builder().objectSize(10L).lastModified(Instant.parse("2026-11-10T00:00:00Z")).build());

        Optional<StoredObjectInfo> restored = provider.restoreLatestVersion("nb-files-test", "videos/v", "video/mp4");

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3).copyObject(copy.capture());
        assertEquals("new", copy.getValue().sourceVersionId());
        assertEquals("videos/v", copy.getValue().destinationKey());
        assertEquals(TaggingDirective.REPLACE, copy.getValue().taggingDirective());
        assertTrue(restored.isPresent());
    }

    @Test
    void restore_nothingLeft_isEmpty() {
        when(s3.listObjectVersions(any(ListObjectVersionsRequest.class)))
                .thenReturn(ListObjectVersionsResponse.builder().versions(List.of()).build());
        assertTrue(provider.restoreLatestVersion("nb-files-test", "videos/v", "video/mp4").isEmpty());
        verify(s3, never()).copyObject(any(CopyObjectRequest.class));
    }

    private ScanVerdict scanWithTags(List<Tag> tags) {
        reset(s3);
        when(s3.getObjectTagging(any(GetObjectTaggingRequest.class)))
                .thenReturn(GetObjectTaggingResponse.builder().tagSet(tags).build());
        return provider.readScanStatus("nb-files-test", "files/abc");
    }

    private static Tag tag(String value) {
        return Tag.builder().key(S3StorageProvider.SCAN_TAG).value(value).build();
    }
}
