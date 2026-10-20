package com.saasbasics.platform.modules.workflow.dto;

public record TaskHandleRequest(
        String action, // AGREE, REJECT, TRANSFER, READ
        String comment,
        Long transferToUserId,
        String transferToUserName
) {
}
