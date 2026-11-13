package com.saasbasics.platform.modules.ai.web;

import com.saasbasics.platform.common.api.ApiResponse;
import com.saasbasics.platform.common.auth.RequirePermission;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.DocumentReviewRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.DocumentReviewResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeAskResponse;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeBaseDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeBaseSaveRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeChunkDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeCitationDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeDocDTO;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeQueryRequest;
import com.saasbasics.platform.modules.ai.application.dto.AiPlatformDtos.KnowledgeTextIngestRequest;
import com.saasbasics.platform.modules.ai.application.service.KnowledgeRagService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/knowledge")
@RequirePermission("system:config:query")
public class KnowledgeBaseController {

    private final KnowledgeRagService knowledgeRagService;

    public KnowledgeBaseController(KnowledgeRagService knowledgeRagService) {
        this.knowledgeRagService = knowledgeRagService;
    }

    @GetMapping("/bases")
    public ApiResponse<List<KnowledgeBaseDTO>> listBases() {
        return ApiResponse.success(knowledgeRagService.listKnowledgeBases());
    }

    @PostMapping("/bases")
    @RequirePermission("system:config:write")
    public ApiResponse<KnowledgeBaseDTO> saveBase(@RequestBody KnowledgeBaseSaveRequest request) {
        return ApiResponse.success(knowledgeRagService.saveKnowledgeBase(request));
    }

    @GetMapping("/docs")
    public ApiResponse<List<KnowledgeDocDTO>> listDocs(@RequestParam(name = "kbId", required = false) Long kbId) {
        return ApiResponse.success(knowledgeRagService.listDocs(kbId));
    }

    @GetMapping("/docs/{docId}/chunks")
    public ApiResponse<List<KnowledgeChunkDTO>> listChunks(@PathVariable("docId") Long docId) {
        return ApiResponse.success(knowledgeRagService.listChunks(docId));
    }

    @PostMapping(value = "/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("system:config:write")
    public ApiResponse<KnowledgeDocDTO> uploadDoc(
            @RequestParam("kbId") Long kbId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success(knowledgeRagService.uploadDocument(kbId, file));
    }

    @PostMapping("/docs/text")
    @RequirePermission("system:config:write")
    public ApiResponse<KnowledgeDocDTO> ingestText(@RequestBody KnowledgeTextIngestRequest request) {
        return ApiResponse.success(knowledgeRagService.ingestText(request));
    }

    @DeleteMapping("/docs/{docId}")
    @RequirePermission("system:config:write")
    public ApiResponse<Void> deleteDoc(@PathVariable("docId") Long docId) {
        knowledgeRagService.deleteDoc(docId);
        return ApiResponse.success(null);
    }

    @PostMapping("/search")
    public ApiResponse<List<KnowledgeCitationDTO>> hybridSearch(@RequestBody KnowledgeQueryRequest request) {
        List<Long> kbIds = request.kbId() != null ? List.of(request.kbId()) : List.of();
        int topK = request.topK() != null ? request.topK() : 5;
        return ApiResponse.success(knowledgeRagService.hybridSearch(kbIds, request.question(), topK));
    }

    @PostMapping("/ask")
    public ApiResponse<KnowledgeAskResponse> ask(@RequestBody KnowledgeQueryRequest request) {
        return ApiResponse.success(knowledgeRagService.askKnowledgeBase(request));
    }

    @PostMapping("/review-document")
    public ApiResponse<DocumentReviewResponse> reviewDocument(@RequestBody DocumentReviewRequest request) {
        return ApiResponse.success(knowledgeRagService.reviewDocument(request));
    }
}
