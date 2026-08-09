package com.saasbasics.platform.modules.file.service;

import com.saasbasics.platform.modules.file.dto.FileCapabilityResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FileService {

    public FileCapabilityResponse capability() {
        return new FileCapabilityResponse(
                List.of("MINIO", "OSS", "COS", "OBS", "S3"),
                true,
                true,
                true
        );
    }
}
