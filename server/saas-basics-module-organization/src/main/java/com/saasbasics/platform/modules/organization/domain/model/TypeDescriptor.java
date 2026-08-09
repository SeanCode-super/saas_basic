package com.saasbasics.platform.modules.organization.domain.model;

import com.saasbasics.platform.common.exception.BizException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

public record TypeDescriptor(String namespace, String code, String version) {

    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9._:-]{0,127}");
    private static final String PRERELEASE_IDENTIFIER =
            "(?:0|[1-9]\\d*|\\d*[A-Za-z-][0-9A-Za-z-]*)";
    private static final Pattern SEMANTIC_VERSION = Pattern.compile(
            "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-(?:" + PRERELEASE_IDENTIFIER + ")(?:\\.(?:" + PRERELEASE_IDENTIFIER + "))*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
    );

    public TypeDescriptor {
        namespace = requireNamespace(namespace);
        code = requireCode(code);
        version = requireVersion(version);
    }

    private static String requireNamespace(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException("ORG_TYPE_NAMESPACE_REQUIRED", "A type namespace is required");
        }
        String normalized = value.trim();
        try {
            URI uri = new URI(normalized);
            if (!uri.isAbsolute()) {
                throw new BizException("ORG_TYPE_NAMESPACE_INVALID", "The type namespace must be an absolute URI");
            }
        } catch (URISyntaxException exception) {
            throw new BizException("ORG_TYPE_NAMESPACE_INVALID", "The type namespace must be a valid URI");
        }
        return normalized;
    }

    private static String requireCode(String value) {
        if (value == null || !CODE.matcher(value.trim()).matches()) {
            throw new BizException("ORG_TYPE_CODE_INVALID", "The type code does not match the standard code profile");
        }
        return value.trim();
    }

    private static String requireVersion(String value) {
        if (value == null || !SEMANTIC_VERSION.matcher(value.trim()).matches()) {
            throw new BizException("ORG_TYPE_VERSION_INVALID", "The type version must follow Semantic Versioning 2.0");
        }
        return value.trim();
    }
}
