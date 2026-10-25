package com.saasbasics.platform.modules.workflow.dto;

public record TaskHandleRequest(
        String action, // AGREE, REJECT, TRANSFER, READ, ADD_SIGN, DELEGATE, DELEGATE_RESOLVE, ROLLBACK_PREVIOUS, ROLLBACK_INITIATOR, ROLLBACK_TARGET, URGE
        String comment,
        Long transferToUserId,
        String transferToUserName,
        Long addSignUserId,
        String addSignUserName,
        String addSignType, // BEFORE, AFTER
        Long delegateUserId,
        String delegateUserName,
        String targetNodeId,
        String targetNodeName
) {
}
