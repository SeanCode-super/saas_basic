package com.saasbasics.platform.modules.iam.dto;

import java.util.List;
import java.util.Map;

public record IamOverviewResponse(List<String> modelLayers, List<String> capabilities, Map<String, Long> counters) {
}
