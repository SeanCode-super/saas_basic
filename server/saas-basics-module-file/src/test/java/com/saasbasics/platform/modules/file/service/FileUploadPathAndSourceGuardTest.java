package com.saasbasics.platform.modules.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import com.saasbasics.platform.modules.file.entity.FileStorageEntity;
import com.saasbasics.platform.modules.file.entity.FileUploadRouteEntity;
import com.saasbasics.platform.modules.file.mapper.FileObjectMapper;
import com.saasbasics.platform.modules.file.mapper.FileStorageMapper;
import com.saasbasics.platform.modules.file.mapper.FileUploadRouteMapper;
import com.saasbasics.platform.modules.file.storage.FileClientFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class FileUploadPathAndSourceGuardTest {

    @TempDir
    Path tempDir;

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    @Test
    void uploadFailsWithPromptWhenNoActiveStorageSourceConfigured() {
        FileStorageMapper storageMapper = mock(FileStorageMapper.class);
        when(storageMapper.selectList(any())).thenReturn(List.of());
        FileUploadRouteMapper routeMapper = mock(FileUploadRouteMapper.class);
        when(routeMapper.selectOne(any())).thenReturn(null);
        FileObjectMapper objectMapper = mock(FileObjectMapper.class);

        FileClientFactory clientFactory = new FileClientFactory(providerOf(storageMapper), "");
        FileStorageService storageService = new FileStorageService(providerOf(storageMapper), providerOf(clientFactory));
        FileUploadRouteService routeService = new FileUploadRouteService(providerOf(routeMapper));
        FileObjectService objectService = new FileObjectService(providerOf(objectMapper));
        FileAccessLogService logService = new FileAccessLogService(providerOf(null));

        PlatformFileStorageGatewayImpl gateway = new PlatformFileStorageGatewayImpl(
                storageService, routeService, clientFactory, objectService, logService
        );

        PlatformFileStorageGateway.StorageAvailability availability = gateway.checkAvailability(1L, "COMMON");
        assertThat(availability.configured()).isFalse();
        assertThat(availability.reasonCode()).isEqualTo("FILE_STORAGE_SOURCE_NOT_CONFIGURED");
        assertThat(availability.message()).contains("存储源");

        assertThatThrownBy(() -> gateway.uploadBytes(1L, "COMMON", "hello.txt", "text/plain", "hi".getBytes()))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("FILE_STORAGE_SOURCE_NOT_CONFIGURED"));
    }

    @Test
    void routesToDefaultFolderWhenUnmappedAndConfiguredFolderWhenRouteExists() throws Exception {
        FileStorageEntity storage = new FileStorageEntity();
        storage.setId(10L);
        storage.setTenantId(1L);
        storage.setStorageCode("local_test");
        storage.setStorageName("本地测试盘");
        storage.setStorageType("LOCAL");
        storage.setBasePath(tempDir.toString());
        storage.setDefaultFolder("default-space");
        storage.setPathPattern("{folder}/{filename}");
        storage.setUploadMode("DIRECT_SERVER");
        storage.setMaxFileMb(10);
        storage.setStatus("ENABLED");
        storage.setIsDefault(true);
        storage.setDeleted(0);

        FileStorageMapper storageMapper = mock(FileStorageMapper.class);
        when(storageMapper.selectList(any())).thenReturn(List.of(storage));

        FileUploadRouteEntity avatarRoute = new FileUploadRouteEntity();
        avatarRoute.setId(20L);
        avatarRoute.setTenantId(1L);
        avatarRoute.setBizType("USER_AVATAR");
        avatarRoute.setRouteName("头像目录");
        avatarRoute.setUploadMode("LOCAL_DISK");
        avatarRoute.setTargetDir("avatars/custom");
        avatarRoute.setPathPattern("{folder}/{filename}");
        avatarRoute.setStatus("ENABLED");
        avatarRoute.setDeleted(0);

        FileUploadRouteMapper routeMapper = mock(FileUploadRouteMapper.class);
        when(routeMapper.selectOne(any())).thenReturn(null).thenReturn(avatarRoute);

        FileObjectMapper objectMapper = mock(FileObjectMapper.class);
        when(objectMapper.insert(any(FileObjectEntity.class))).thenReturn(1);

        FileClientFactory clientFactory = new FileClientFactory(providerOf(storageMapper), "");
        FileStorageService storageService = new FileStorageService(providerOf(storageMapper), providerOf(clientFactory));
        FileUploadRouteService routeService = new FileUploadRouteService(providerOf(routeMapper));
        FileObjectService objectService = new FileObjectService(providerOf(objectMapper));
        FileAccessLogService logService = new FileAccessLogService(providerOf(null));

        PlatformFileStorageGatewayImpl gateway = new PlatformFileStorageGatewayImpl(
                storageService, routeService, clientFactory, objectService, logService
        );

        // 1) Unmapped bizType -> defaultFolder ("default-space")
        PlatformFileStorageGateway.StoredUploadResult defaultRes = gateway.uploadBytes(
                1L, "CONTRACT", "doc.txt", "text/plain", "contract-body".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(defaultRes.targetDir()).isEqualTo("default-space");
        assertThat(defaultRes.objectKey()).isEqualTo("default-space/doc.txt");
        assertThat(defaultRes.uploadMode()).isEqualTo("DIRECT_SERVER");
        assertThat(Files.readString(tempDir.resolve("default-space/doc.txt"))).isEqualTo("contract-body");

        // 2) Configured bizType ("USER_AVATAR") -> configured targetDir ("avatars/custom") & uploadMode ("LOCAL_DISK")
        PlatformFileStorageGateway.StoredUploadResult avatarRes = gateway.uploadBytes(
                1L, "USER_AVATAR", "me.png", "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8)
        );
        assertThat(avatarRes.targetDir()).isEqualTo("avatars/custom");
        assertThat(avatarRes.objectKey()).isEqualTo("avatars/custom/me.png");
        assertThat(avatarRes.uploadMode()).isEqualTo("LOCAL_DISK");
        assertThat(Files.readString(tempDir.resolve("avatars/custom/me.png"))).isEqualTo("png-bytes");
    }

    @Test
    void rejectsPathTraversalInConfiguredDirectory() {
        assertThatThrownBy(() -> FilePathPolicyHelper.sanitizeFolder("../../etc/passwd", "uploads"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getCode()).isEqualTo("FILE_INVALID_PATH"));
    }
}
