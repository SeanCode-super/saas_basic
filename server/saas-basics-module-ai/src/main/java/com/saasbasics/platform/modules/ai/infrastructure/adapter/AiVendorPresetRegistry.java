package com.saasbasics.platform.modules.ai.infrastructure.adapter;

import java.util.List;

/**
 * Standardized catalog of all major global, Chinese, and local LLM vendor presets.
 */
public final class AiVendorPresetRegistry {

    private AiVendorPresetRegistry() {}

    public record VendorModelTemplate(
            String modelId,
            String modelName,
            String category,
            String contextWindow,
            int maxTokens,
            double defaultTemperature
    ) {}

    public record VendorPreset(
            String vendorCode,
            String vendorName,
            String providerType,
            String defaultBaseUrl,
            boolean apiKeyRequired,
            String authGuide,
            String badgeColor,
            List<VendorModelTemplate> suggestedModels
    ) {}

    private static final List<VendorPreset> PRESETS = List.of(
            new VendorPreset("deepseek", "DeepSeek 开放平台", "OPENAI_COMPATIBLE", "https://api.deepseek.com", true,
                    "Bearer API Key (sk-...)", "#2563eb", List.of(
                    new VendorModelTemplate("deepseek-chat", "DeepSeek-V3 通用旗舰", "CHAT", "64k", 8192, 0.7),
                    new VendorModelTemplate("deepseek-reasoner", "DeepSeek-R1 深度推理", "REASONING", "64k", 8192, 0.6)
            )),
            new VendorPreset("openai", "OpenAI 官方平台", "OPENAI_COMPATIBLE", "https://api.openai.com/v1", true,
                    "Bearer API Key (sk-...)", "#10a37f", List.of(
                    new VendorModelTemplate("gpt-4o", "OpenAI GPT-4o 旗舰", "CHAT", "128k", 4096, 0.7),
                    new VendorModelTemplate("gpt-4o-mini", "OpenAI GPT-4o Mini 轻量", "CHAT", "128k", 4096, 0.7),
                    new VendorModelTemplate("o3-mini", "OpenAI o3-mini 深度思考", "REASONING", "200k", 16384, 1.0)
            )),
            new VendorPreset("anthropic", "Anthropic Claude", "ANTHROPIC", "https://api.anthropic.com/v1", true,
                    "x-api-key (sk-ant-...)", "#d97706", List.of(
                    new VendorModelTemplate("claude-sonnet-4-20250514", "Claude Sonnet 4", "REASONING", "200k", 8192, 0.3),
                    new VendorModelTemplate("claude-3-7-sonnet-20250219", "Claude 3.7 Sonnet", "CODER", "200k", 8192, 0.3),
                    new VendorModelTemplate("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", "CHAT", "200k", 4096, 0.5)
            )),
            new VendorPreset("gemini", "Google Gemini", "GEMINI", "https://generativelanguage.googleapis.com/v1beta", true,
                    "x-goog-api-key (AIza...)", "#4285f4", List.of(
                    new VendorModelTemplate("gemini-2.5-pro", "Gemini 2.5 Pro", "REASONING", "1M", 8192, 0.4),
                    new VendorModelTemplate("gemini-2.5-flash", "Gemini 2.5 Flash", "CHAT", "1M", 8192, 0.5)
            )),
            new VendorPreset("azure_openai", "Microsoft Azure OpenAI", "AZURE_OPENAI", "https://<resource>.openai.azure.com", true,
                    "api-key Header（部署名填在 Model ID）", "#0078d4", List.of(
                    new VendorModelTemplate("gpt-4o", "Azure GPT-4o Deployment", "CHAT", "128k", 4096, 0.7)
            )),
            new VendorPreset("qwen", "阿里云百炼 / 通义千问", "OPENAI_COMPATIBLE", "https://dashscope.aliyuncs.com/compatible-mode/v1", true,
                    "DashScope API Key", "#6366f1", List.of(
                    new VendorModelTemplate("qwen-max", "通义千问 Qwen-Max", "CHAT", "32k", 4096, 0.7),
                    new VendorModelTemplate("qwen-plus", "通义千问 Qwen-Plus", "CHAT", "128k", 8192, 0.7),
                    new VendorModelTemplate("qwen-coder-plus", "Qwen Coder Plus", "CODER", "128k", 8192, 0.2)
            )),
            new VendorPreset("zhipu", "智谱 AI (BigModel GLM)", "OPENAI_COMPATIBLE", "https://open.bigmodel.cn/api/paas/v4", true,
                    "智谱开放平台 API Key", "#0ea5e9", List.of(
                    new VendorModelTemplate("glm-4-plus", "智谱 GLM-4 Plus", "CHAT", "128k", 4096, 0.6),
                    new VendorModelTemplate("glm-zero-preview", "智谱 GLM-Zero 推理", "REASONING", "32k", 8192, 0.4)
            )),
            new VendorPreset("moonshot", "月之暗面 Kimi", "OPENAI_COMPATIBLE", "https://api.moonshot.cn/v1", true,
                    "Moonshot API Key", "#0f172a", List.of(
                    new VendorModelTemplate("moonshot-v1-128k", "Moonshot v1 128K", "CHAT", "128k", 4096, 0.3),
                    new VendorModelTemplate("kimi-latest", "Kimi Latest", "CHAT", "128k", 8192, 0.3)
            )),
            new VendorPreset("volcengine", "火山引擎方舟 (豆包 Doubao)", "OPENAI_COMPATIBLE", "https://ark.cn-beijing.volces.com/api/v3", true,
                    "方舟 API Key（Model ID 填接入点 ep-... 或模型 ID）", "#ef4444", List.of(
                    new VendorModelTemplate("doubao-1-5-pro-32k", "豆包 1.5 Pro 32K", "CHAT", "32k", 4096, 0.7)
            )),
            new VendorPreset("openrouter", "OpenRouter 聚合网关", "OPENAI_COMPATIBLE", "https://openrouter.ai/api/v1", true,
                    "OpenRouter Key (sk-or-...)", "#7c3aed", List.of(
                    new VendorModelTemplate("anthropic/claude-3.7-sonnet", "OpenRouter Claude 3.7", "REASONING", "200k", 8192, 0.3),
                    new VendorModelTemplate("google/gemini-2.5-pro-exp-03-25", "OpenRouter Gemini 2.5 Pro", "REASONING", "1M", 8192, 0.4)
            )),
            new VendorPreset("groq", "Groq 极速推理云", "OPENAI_COMPATIBLE", "https://api.groq.com/openai/v1", true,
                    "Groq API Key (gsk_...)", "#f97316", List.of(
                    new VendorModelTemplate("llama-3.3-70b-versatile", "Llama 3.3 70B (Groq)", "CHAT", "128k", 8192, 0.6)
            )),
            new VendorPreset("xai", "xAI Grok", "OPENAI_COMPATIBLE", "https://api.x.ai/v1", true,
                    "xAI API Key", "#111827", List.of(
                    new VendorModelTemplate("grok-3-beta", "xAI Grok 3", "REASONING", "131k", 8192, 0.5)
            )),
            new VendorPreset("ollama", "Ollama 本地私有部署", "OLLAMA_NATIVE", "http://localhost:11434", false,
                    "本地免密（支持 /api/chat 原生或 /v1 兼容）", "#16a34a", List.of(
                    new VendorModelTemplate("llama3.1:8b", "Llama 3.1 8B (Local)", "CHAT", "128k", 4096, 0.7),
                    new VendorModelTemplate("qwen2.5:14b", "Qwen 2.5 14B (Local)", "CODER", "32k", 4096, 0.3)
            )),
            new VendorPreset("vllm", "vLLM / LM Studio 自建集群", "OPENAI_COMPATIBLE", "http://localhost:8000/v1", false,
                    "私有 GPU OpenAI 兼容服务（可选 Bearer Token）", "#475569", List.of(
                    new VendorModelTemplate("Qwen/Qwen2.5-32B-Instruct", "Qwen2.5-32B vLLM", "CHAT", "32k", 4096, 0.5)
            ))
    );

    public static List<VendorPreset> all() {
        return PRESETS;
    }

    public static boolean requiresApiKey(String providerType) {
        return !"OLLAMA".equalsIgnoreCase(providerType)
                && !"OLLAMA_NATIVE".equalsIgnoreCase(providerType)
                && !"VLLM".equalsIgnoreCase(providerType)
                && !"LOCAL".equalsIgnoreCase(providerType);
    }
}
