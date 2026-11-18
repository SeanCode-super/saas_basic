package com.saasbasics.platform.modules.iam.controller;

import com.saasbasics.platform.common.exception.BizException;
import com.saasbasics.platform.modules.iam.dto.ScimDtos;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimError;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimListResponse;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimPatchOp;
import com.saasbasics.platform.modules.iam.dto.ScimDtos.ScimUserResource;
import com.saasbasics.platform.modules.iam.service.ScimProvisioningService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCIM 2.0 (RFC 7643 / RFC 7644) 目录同步接口控制器。
 */
@RestController
@RequestMapping(value = "/scim/v2", produces = {"application/scim+json", "application/json"})
public class ScimV2Controller {

    private final ScimProvisioningService scimProvisioningService;

    public ScimV2Controller(ScimProvisioningService scimProvisioningService) {
        this.scimProvisioningService = scimProvisioningService;
    }

    @GetMapping("/ServiceProviderConfig")
    public Map<String, Object> serviceProviderConfig() {
        return scimProvisioningService.serviceProviderConfig();
    }

    @GetMapping("/Schemas")
    public ScimListResponse<Map<String, Object>> schemas() {
        return scimProvisioningService.schemas();
    }

    @GetMapping("/Users")
    public ScimListResponse<ScimUserResource> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "50") int count
    ) {
        return scimProvisioningService.listUsers(authorization, filter, startIndex, count);
    }

    @GetMapping("/Users/{id}")
    public ScimUserResource getUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id
    ) {
        return scimProvisioningService.getUser(authorization, id);
    }

    @PostMapping(value = "/Users", consumes = {"application/scim+json", "application/json"})
    @ResponseStatus(HttpStatus.CREATED)
    public ScimUserResource createUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ScimUserResource request
    ) {
        return scimProvisioningService.createUser(authorization, request);
    }

    @PatchMapping(value = "/Users/{id}", consumes = {"application/scim+json", "application/json"})
    public ScimUserResource patchUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @RequestBody ScimPatchOp patchOp
    ) {
        return scimProvisioningService.patchUser(authorization, id, patchOp);
    }

    @DeleteMapping("/Users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id
    ) {
        scimProvisioningService.deactivateUser(authorization, id);
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ScimError> handleScimBizException(BizException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "SCIM_UNAUTHORIZED" -> HttpStatus.UNAUTHORIZED;
            case "SCIM_FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "SCIM_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "SCIM_CONFLICT" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(new ScimError(
                List.of(ScimDtos.ERROR_SCHEMA),
                ex.getMessage(),
                String.valueOf(status.value())
        ));
    }
}
