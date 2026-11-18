package com.saasbasics.platform.modules.iam.dto;

import java.util.List;
import java.util.Map;

public final class ScimDtos {

    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    public static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
    public static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";
    public static final String SPC_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig";

    private ScimDtos() {
    }

    public record ScimName(
            String formatted,
            String givenName,
            String familyName
    ) {
    }

    public record ScimEmail(
            String value,
            String type,
            boolean primary
    ) {
    }

    public record ScimMeta(
            String resourceType,
            String created,
            String lastModified,
            String location
    ) {
    }

    public record ScimUserResource(
            List<String> schemas,
            String id,
            String externalId,
            String userName,
            String displayName,
            ScimName name,
            List<ScimEmail> emails,
            boolean active,
            ScimMeta meta
    ) {
    }

    public record ScimListResponse<T>(
            List<String> schemas,
            int totalResults,
            int startIndex,
            int itemsPerPage,
            List<T> Resources
    ) {
    }

    public record ScimPatchOp(
            List<String> schemas,
            List<Map<String, Object>> Operations
    ) {
    }

    public record ScimError(
            List<String> schemas,
            String detail,
            String status
    ) {
    }
}
