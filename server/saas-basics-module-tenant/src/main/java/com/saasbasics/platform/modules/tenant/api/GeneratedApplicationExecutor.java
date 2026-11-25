package com.saasbasics.platform.modules.tenant.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.saasbasics.platform.common.auth.AuthPrincipal;
import java.util.Map;
import java.util.UUID;

/** Executes an installed project revision after the workspace has checked tenant, page and action permissions. */
public interface GeneratedApplicationExecutor {
    JsonNode execute(String reviewedDesign, String action, Map<String,String> input, UUID requestId, AuthPrincipal principal);
}
