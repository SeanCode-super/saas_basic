package com.saasbasics.platform.modules.erp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ErpMasterDataRequest(@NotBlank @Size(max = 64) String code,
                                   @NotBlank @Size(max = 128) String name) {}
