package com.saasbasics.platform.modules.file.controller;

import cn.idev.excel.annotation.ExcelProperty;
import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.excel.ExcelUtils;
import com.saasbasics.platform.common.file.PlatformFileStorageGateway;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
public class FileStorageEngineController {

    private final PlatformFileStorageGateway storageGateway;

    public FileStorageEngineController(PlatformFileStorageGateway storageGateway) {
        this.storageGateway = storageGateway;
    }

    @GetMapping("/storage-status")
    public ApiResponse<PlatformFileStorageGateway.StorageAvailability> storageStatus(
            @RequestParam(required = false, defaultValue = "COMMON") String bizType) {
        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null ? principal.tenantId() : 1L;
        return ApiResponse.success(storageGateway.checkAvailability(tenantId, bizType));
    }

    @PostMapping("/raw-upload")
    public ApiResponse<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "COMMON") String bizType) throws IOException {

        if (file.isEmpty()) {
            return ApiResponse.failure("FILE_EMPTY", "上传文件不能为空");
        }

        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null ? principal.tenantId() : 1L;
        PlatformFileStorageGateway.StoredUploadResult result = storageGateway.uploadBytes(
                tenantId,
                bizType,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );

        return ApiResponse.success(Map.ofEntries(
                Map.entry("fileId", result.fileId() != null ? result.fileId() : 0L),
                Map.entry("storageId", result.storageId()),
                Map.entry("storageCode", result.storageCode()),
                Map.entry("storageType", result.storageType()),
                Map.entry("uploadMode", result.uploadMode()),
                Map.entry("targetDir", result.targetDir()),
                Map.entry("objectKey", result.objectKey()),
                Map.entry("url", result.url()),
                Map.entry("fileName", result.fileName()),
                Map.entry("fileSize", result.fileSize()),
                Map.entry("contentType", result.contentType())
        ));
    }

    @GetMapping("/raw-download/**")
    public ResponseEntity<byte[]> downloadFile(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/api/file/raw-download/";
        int idx = uri.indexOf(prefix);
        String path = (idx >= 0) ? uri.substring(idx + prefix.length()) : uri;

        AuthPrincipal principal = AuthContext.get();
        Long tenantId = principal != null ? principal.tenantId() : 1L;
        byte[] bytes = storageGateway.downloadObject(tenantId, path);

        String filename = path.substring(path.lastIndexOf('/') + 1);
        String guessed = URLConnection.guessContentTypeFromName(filename);
        MediaType mediaType = (guessed != null) ? MediaType.parseMediaType(guessed) : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(bytes);
    }

    /**
     * FastExcel 流式高性能导出示例
     */
    @GetMapping("/export-demo-excel")
    public void exportDemoExcel(HttpServletResponse response) throws IOException {
        List<DemoUserExcelVo> list = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            list.add(new DemoUserExcelVo(
                    (long) i,
                    "user_" + i,
                    "研发专家 " + i,
                    "1380013" + String.format("%04d", i),
                    "tech" + i + "@saasbasics.com",
                    "技术研发部",
                    "NORMAL"
            ));
        }

        ExcelUtils.write(response, "用户档案数据流式导出_" + System.currentTimeMillis(), "用户台账",
                DemoUserExcelVo.class, list);
    }

    public static class DemoUserExcelVo {
        @ExcelProperty("用户ID")
        private Long id;

        @ExcelProperty("登录账号")
        private String username;

        @ExcelProperty("用户昵称")
        private String nickname;

        @ExcelProperty("手机号码")
        private String mobile;

        @ExcelProperty("电子邮箱")
        private String email;

        @ExcelProperty("归属部门")
        private String department;

        @ExcelProperty("账号状态")
        private String status;

        public DemoUserExcelVo() {
        }

        public DemoUserExcelVo(Long id, String username, String nickname, String mobile, String email, String department, String status) {
            this.id = id;
            this.username = username;
            this.nickname = nickname;
            this.mobile = mobile;
            this.email = email;
            this.department = department;
            this.status = status;
        }

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getNickname() { return nickname; }
        public String getMobile() { return mobile; }
        public String getEmail() { return email; }
        public String getDepartment() { return department; }
        public String getStatus() { return status; }
    }
}
