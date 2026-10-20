package com.saasbasics.platform.modules.codegen.dto;

public record CodegenPreviewFileResponse(
        String path,
        String name,
        String layer,
        String language,
        String code
) {}
