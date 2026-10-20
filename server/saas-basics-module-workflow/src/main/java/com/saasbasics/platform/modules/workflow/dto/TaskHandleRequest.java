package com.saasbasics.platform.modules.workflow.dto;

public record TaskHandleRequest(
        String action, // AGREE, REJECT, TRANSFER, READ, ADD_SIGN, ROLLBACK_PREVIOUS, ROLLBACK_INITIATOR
        String comment,
        Long transferToUserId,
        String transferToUserName,
        Long addSignUserId,
        String addSignUserName
) {
}
