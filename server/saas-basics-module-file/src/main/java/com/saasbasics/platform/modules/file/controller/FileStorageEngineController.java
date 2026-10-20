package com.saasbasics.platform.modules.file.controller;

import cn.idev.excel.annotation.ExcelProperty;
import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.AuthContext;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import com.saasbasics.platform.common.excel.ExcelUtils;
import com.saasbasics.platform.modules.file.entity.FileObjectEntity;
import com.saasbasics.platform.modules.file.mapper.FileObjectMapper;
import com.saasbasics.platform.modules.file.storage.FileClient;
import com.saasbasics.platform.modules.file.storage.FileClientFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/file")
public class FileStorageEngineController {

    private final FileClientFactory fileClientFactory;
    private final ObjectProvider<FileObjectMapper> fileObjectMapperProvider;

    public FileStorageEngineController(FileClientFactory fileClientFactory,
                                       ObjectProvider<FileObjectMapper> fileObjectMapperProvider) {
        this.fileClientFactory = fileClientFactory;
        this.fileObjectMapperProvider = fileObjectMapperProvider;
    }

    @PostMapping("/raw-upload")
    public ApiResponse<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "COMMON") String bizType) throws IOException {

        if (file.isEmpty()) {
            return ApiResponse.failure("FILE_EMPTY", "上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "unnamed.bin";
        }

        String ext = "";
        int dotIdx = originalFilename.lastIndexOf('.');
        if (dotIdx >= 0) {
            ext = originalFilename.substring(dotIdx + 1);
        }

        String datePrefix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniqueName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "_" + originalFilename;
        String objectKey = "uploads/" + datePrefix + "/" + uniqueName;

        FileClient client = fileClientFactory.getDefaultClient();
        String publicUrl = client.upload(file.getBytes(), objectKey, file.getContentType());

        // Save file_object metadata
        FileObjectMapper mapper = fileObjectMapperProvider.getIfAvailable();
        if (mapper != null) {
            AuthPrincipal principal = AuthContext.get();
            FileObjectEntity entity = new FileObjectEntity();
            entity.setTenantId(principal != null ? principal.tenantId() : 1L);
            entity.setBucketId(1L);
            entity.setObjectKey(objectKey);
            entity.setFileName(originalFilename);
            entity.setFileExt(ext);
            entity.setContentType(file.getContentType());
            entity.setFileSize(file.getSize());
            entity.setStoragePath(objectKey);
            entity.setVisibility("PUBLIC");
            entity.setBizType(bizType);
            entity.setVersionNo(1);
            entity.setStatus("ACTIVE");
            mapper.insert(entity);
        }

        return ApiResponse.success(Map.of(
                "objectKey", objectKey,
                "url", publicUrl,
                "fileName", originalFilename,
                "fileSize", file.getSize(),
                "contentType", file.getContentType() != null ? file.getContentType() : "application/octet-stream"
        ));
    }

    @GetMapping("/raw-download/**")
    public ResponseEntity<byte[]> downloadFile(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = "/api/file/raw-download/";
        int idx = uri.indexOf(prefix);
        String path = (idx >= 0) ? uri.substring(idx + prefix.length()) : uri;

        FileClient client = fileClientFactory.getDefaultClient();
        byte[] bytes = client.download(path);

        String filename = path.substring(path.lastIndexOf('/') + 1);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    /**
     * FastExcel 流式高性能导出示例 (对标芋道 EasyExcel 导出能力并超越)
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
