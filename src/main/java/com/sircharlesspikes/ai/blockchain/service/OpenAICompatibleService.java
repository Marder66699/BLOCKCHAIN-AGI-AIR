/**
 * OpenAI SDK Compatible Service - ChatGPT API for Blockchain AGI
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 * 
 * NO SERVERS, NO COMPUTERS - PURE BLOCKCHAIN INTELLIGENCE!
 * 
 * Provides OpenAI SDK compatible endpoints:
 * - /v1/chat/completions (ChatGPT-like API)
 * - /v1/completions (Text completion)
 * - /v1/models (Available models)
 * - /v1/embeddings (Text embeddings)
 */

package com.sircharlesspikes.ai.blockchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OpenAICompatibleService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAICompatibleService.class);

    private final ObjectMapper objectMapper;
    private final LlamaCppModelLoader modelLoader;
    private final Map<String, String> modelMappings;
    private final Map<String, Object> activeRequests;
    
    // OpenAI API Configuration
    private static final String API_VERSION = "v1";
    private static final String DEFAULT_MODEL = "gguf-model-gemma-3-270m-q4_0";
    
    // Statistics
    private volatile long totalRequests = 0;
    private volatile long totalTokensProcessed = 0;
    private volatile double averageLatency = 0.0;

    public OpenAICompatibleService(LlamaCppModelLoader modelLoader) {
        this.objectMapper = new ObjectMapper();
        this.modelLoader = modelLoader;
        this.activeRequests = new ConcurrentHashMap<>();
        
        // Map OpenAI model names to GGUF models
        this.modelMappings = Map.of(
            "gpt-3.5-turbo", "gguf-model-gemma-3-270m-q4_0",
            "gpt-3.5-turbo-16k", "gguf-model-gemma-3-270m-q4_0",
            "gpt-4", "gguf-model-llama2-7b-q4_0",
            "gpt-4-turbo", "gguf-model-mistral-7b-q4_0",
            "text-davinci-003", "gguf-model-llama2-7b-q4_0",
            "code-davinci-002", "gguf-model-codellama-7b-q4_0",
            "text-embedding-ada-002", "gguf-model-all-minilm-l6-v2"
        );
        
        logger.info("🤖 OpenAI Compatible Service initialized");
        logger.info("🎯 Available model mappings: {}", modelMappings.keySet());
    }

    /**
     * Chat Completions API - ChatGPT compatible
     * POST /v1/chat/completions
     */
    public CompletableFuture<String> createChatCompletion(String requestBody, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // Parse request
                JsonNode request = objectMapper.readTree(requestBody);
                ChatCompletionRequest chatRequest = parseChatCompletionRequest(request);
                
                logger.info("🎯 Chat completion request: model={}, messages={}", 
                    chatRequest.model, chatRequest.messages.size());
                
                // Map OpenAI model to GGUF model
                String ggufModel = modelMappings.getOrDefault(chatRequest.model, DEFAULT_MODEL);
                
                // Process with local GGUF model
                String response = processWithGGUF(chatRequest, ggufModel);
                
                // Create OpenAI compatible response
                ChatCompletionResponse completionResponse = createChatCompletionResponse(
                    chatRequest, response, startTime);
                
                // Update statistics
                updateStatistics(startTime, response.length());
                
                return objectMapper.writeValueAsString(completionResponse);
                
            } catch (Exception e) {
                logger.error("❌ Chat completion failed", e);
                return createErrorResponse("chat_completion_failed", e.getMessage());
            }
        });
    }

    /**
     * Text Completions API
     * POST /v1/completions
     */
    public CompletableFuture<String> createCompletion(String requestBody, Map<String, String> headers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                JsonNode request = objectMapper.readTree(requestBody);
                CompletionRequest completionRequest = parseCompletionRequest(request);
                
                logger.info("🎯 Text completion request: model={}, prompt_length={}", 
                    completionRequest.model, completionRequest.prompt.length());
                
                String ggufModel = modelMappings.getOrDefault(completionRequest.model, DEFAULT_MODEL);
                String response = processTextCompletion(completionRequest, ggufModel);
                
                CompletionResponse completionResponse = createCompletionResponse(
                    completionRequest, response, startTime);
                
                updateStatistics(startTime, response.length());
                
                return objectMapper.writeValueAsString(completionResponse);
                
            } catch (Exception e) {
                logger.error("❌ Text completion failed", e);
                return createErrorResponse("text_completion_failed", e.getMessage());
            }
        });
    }

    /**
     * Models API - List available models
     * GET /v1/models
     */
    public String listModels() {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("object", "list");
            
            ArrayNode models = objectMapper.createArrayNode();
            
            for (String modelName : modelMappings.keySet()) {
                ObjectNode model = objectMapper.createObjectNode();
                model.put("id", modelName);
                model.put("object", "model");
                model.put("created", Instant.now().getEpochSecond());
                model.put("owned_by", "blockchain-agi");
                
                ObjectNode permission = objectMapper.createObjectNode();
                permission.put("id", "modelperm-" + modelName);
                permission.put("object", "model_permission");
                permission.put("created", Instant.now().getEpochSecond());
                permission.put("allow_create_engine", true);
                permission.put("allow_sampling", true);
                permission.put("allow_logprobs", true);
                permission.put("allow_search_indices", true);
                permission.put("allow_view", true);
                permission.put("allow_fine_tuning", false);
                permission.put("organization", "*");
                permission.put("group", null);
                permission.put("is_blocking", false);
                
                ArrayNode permissions = objectMapper.createArrayNode();
                permissions.add(permission);
                model.set("permission", permissions);
                
                models.add(model);
            }
            
            response.set("data", models);
            
            logger.info("📋 Listed {} available models", modelMappings.size());
            return objectMapper.writeValueAsString(response);
            
        } catch (Exception e) {
            logger.error("❌ Failed to list models", e);
            return createErrorResponse("list_models_failed", e.getMessage());
        }
    }

    /**
     * Get specific model
     * GET /v1/models/{model}
     */
    public String getModel(String modelId) {
        try {
            if (!modelMappings.containsKey(modelId)) {
                return createErrorResponse("model_not_found", "Model " + modelId + " not found");
            }
            
            ObjectNode model = objectMapper.createObjectNode();
            model.put("id", modelId);
            model.put("object", "model");
            model.put("created", Instant.now().getEpochSecond());
            model.put("owned_by", "blockchain-agi");
            model.put("root", modelId);
            model.put("parent", null);
            
            // Add GGUF model info
            String ggufModel = modelMappings.get(modelId);
            ObjectNode ggufInfo = objectMapper.createObjectNode();
            ggufInfo.put("gguf_model", ggufModel);
            ggufInfo.put("quantization", "Q4_0");
            ggufInfo.put("format", "GGUF");
            ggufInfo.put("backend", "llama.cpp");
            model.set("gguf_info", ggufInfo);
            
            return objectMapper.writeValueAsString(model);
            
        } catch (Exception e) {
            logger.error("❌ Failed to get model {}", modelId, e);
            return createErrorResponse("get_model_failed", e.getMessage());
        }
    }

    /**
     * Process request with GGUF model
     */
    private String processWithGGUF(ChatCompletionRequest request, String ggufModel) {
        try {
            // Convert messages to prompt
            String prompt = convertMessagesToPrompt(request.messages);
            
            // Process with GGUF model (placeholder - integrate with actual llama.cpp)
            String response = simulateGGUFProcessing(prompt, request);
            
            logger.info("🤖 GGUF processing complete: {} chars", response.length());
            return response;
            
        } catch (Exception e) {
            logger.error("❌ GGUF processing failed", e);
            throw new RuntimeException("GGUF processing failed: " + e.getMessage());
        }
    }

    /**
     * Process text completion with GGUF
     */
    private String processTextCompletion(CompletionRequest request, String ggufModel) {
        try {
            // Process with GGUF model
            String response = simulateGGUFTextCompletion(request.prompt, request);
            
            logger.info("🤖 GGUF text completion: {} chars", response.length());
            return response;
            
        } catch (Exception e) {
            logger.error("❌ GGUF text completion failed", e);
            throw new RuntimeException("GGUF text completion failed: " + e.getMessage());
        }
    }

    /**
     * Convert OpenAI messages to llama.cpp prompt
     */
    private String convertMessagesToPrompt(List<ChatMessage> messages) {
        StringBuilder prompt = new StringBuilder();
        
        for (ChatMessage message : messages) {
            switch (message.role) {
                case "system":
                    prompt.append("### System:\n").append(message.content).append("\n\n");
                    break;
                case "user":
                    prompt.append("### Human:\n").append(message.content).append("\n\n");
                    break;
                case "assistant":
                    prompt.append("### Assistant:\n").append(message.content).append("\n\n");
                    break;
            }
        }
        
        prompt.append("### Assistant:\n");
        return prompt.toString();
    }

    /**
     * Simulate GGUF processing (replace with actual llama.cpp integration)
     */
    private String simulateGGUFProcessing(String prompt, ChatCompletionRequest request) {
        // This is a placeholder - replace with actual llama.cpp integration
        logger.info("🧠 Simulating GGUF processing for prompt: {}", prompt.substring(0, Math.min(50, prompt.length())));
        
        // Simulate processing time
        try {
            Thread.sleep(1000 + (long)(Math.random() * 2000)); // 1-3 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Generate a realistic response
        return "I understand your question about blockchain AI. This system represents a revolutionary approach to artificial intelligence where:\n\n" +
               "1. **No Servers Required**: AI models run locally on volunteer machines\n" +
               "2. **Blockchain Coordination**: Smart contracts trigger and coordinate AI processing\n" +
               "3. **GGUF Efficiency**: Quantized models provide fast, efficient inference\n" +
               "4. **IPFS Storage**: Models stored permanently on decentralized storage\n" +
               "5. **OpenAI Compatibility**: Same API you know and love\n\n" +
               "This creates a truly decentralized AI infrastructure that scales infinitely without any central authority or servers.";
    }

    /**
     * Simulate GGUF text completion
     */
    private String simulateGGUFTextCompletion(String prompt, CompletionRequest request) {
        logger.info("🧠 Processing text completion for: {}", prompt.substring(0, Math.min(50, prompt.length())));
        
        try {
            Thread.sleep(800 + (long)(Math.random() * 1200)); // 0.8-2 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return " continues with blockchain-powered intelligence, providing serverless AI capabilities through decentralized processing nodes.";
    }

    /**
     * Create ChatCompletion response
     */
    private ChatCompletionResponse createChatCompletionResponse(ChatCompletionRequest request, String content, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        ChatCompletionResponse response = new ChatCompletionResponse();
        response.id = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 29);
        response.object = "chat.completion";
        response.created = Instant.now().getEpochSecond();
        response.model = request.model;
        
        ChatChoice choice = new ChatChoice();
        choice.index = 0;
        choice.message = new ChatMessage();
        choice.message.role = "assistant";
        choice.message.content = content;
        choice.finishReason = "stop";
        
        response.choices = List.of(choice);
        
        Usage usage = new Usage();
        usage.promptTokens = estimateTokens(convertMessagesToPrompt(request.messages));
        usage.completionTokens = estimateTokens(content);
        usage.totalTokens = usage.promptTokens + usage.completionTokens;
        response.usage = usage;
        
        logger.info("✅ Chat completion created: {} tokens in {}ms", usage.totalTokens, processingTime);
        return response;
    }

    /**
     * Create Completion response
     */
    private CompletionResponse createCompletionResponse(CompletionRequest request, String content, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        CompletionResponse response = new CompletionResponse();
        response.id = "cmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 29);
        response.object = "text_completion";
        response.created = Instant.now().getEpochSecond();
        response.model = request.model;
        
        TextChoice choice = new TextChoice();
        choice.text = content;
        choice.index = 0;
        choice.logprobs = null;
        choice.finishReason = "stop";
        
        response.choices = List.of(choice);
        
        Usage usage = new Usage();
        usage.promptTokens = estimateTokens(request.prompt);
        usage.completionTokens = estimateTokens(content);
        usage.totalTokens = usage.promptTokens + usage.completionTokens;
        response.usage = usage;
        
        logger.info("✅ Text completion created: {} tokens in {}ms", usage.totalTokens, processingTime);
        return response;
    }

    /**
     * Parse chat completion request
     */
    private ChatCompletionRequest parseChatCompletionRequest(JsonNode json) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.model = json.path("model").asText(DEFAULT_MODEL);
        request.temperature = json.path("temperature").asDouble(0.7);
        request.maxTokens = json.path("max_tokens").asInt(256);
        request.topP = json.path("top_p").asDouble(0.9);
        request.frequencyPenalty = json.path("frequency_penalty").asDouble(0.0);
        request.presencePenalty = json.path("presence_penalty").asDouble(0.0);
        request.stream = json.path("stream").asBoolean(false);
        
        request.messages = new ArrayList<>();
        JsonNode messagesNode = json.path("messages");
        if (messagesNode.isArray()) {
            for (JsonNode messageNode : messagesNode) {
                ChatMessage message = new ChatMessage();
                message.role = messageNode.path("role").asText();
                message.content = messageNode.path("content").asText();
                request.messages.add(message);
            }
        }
        
        return request;
    }

    /**
     * Parse completion request
     */
    private CompletionRequest parseCompletionRequest(JsonNode json) {
        CompletionRequest request = new CompletionRequest();
        request.model = json.path("model").asText(DEFAULT_MODEL);
        request.prompt = json.path("prompt").asText();
        request.maxTokens = json.path("max_tokens").asInt(256);
        request.temperature = json.path("temperature").asDouble(0.7);
        request.topP = json.path("top_p").asDouble(0.9);
        request.frequencyPenalty = json.path("frequency_penalty").asDouble(0.0);
        request.presencePenalty = json.path("presence_penalty").asDouble(0.0);
        request.stream = json.path("stream").asBoolean(false);
        
        return request;
    }

    /**
     * Estimate token count (rough approximation)
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) Math.ceil(text.split("\\s+").length * 1.3); // Rough estimate
    }

    /**
     * Create error response
     */
    private String createErrorResponse(String code, String message) {
        try {
            ObjectNode error = objectMapper.createObjectNode();
            ObjectNode errorDetail = objectMapper.createObjectNode();
            errorDetail.put("message", message);
            errorDetail.put("type", "blockchain_agi_error");
            errorDetail.put("code", code);
            error.set("error", errorDetail);
            
            return objectMapper.writeValueAsString(error);
        } catch (Exception e) {
            return "{\"error\":{\"message\":\"Internal error\",\"type\":\"internal_error\"}}";
        }
    }

    /**
     * Update processing statistics
     */
    private void updateStatistics(long startTime, int responseLength) {
        long processingTime = System.currentTimeMillis() - startTime;
        totalRequests++;
        totalTokensProcessed += estimateTokens(String.valueOf(responseLength));
        averageLatency = (averageLatency * (totalRequests - 1) + processingTime) / totalRequests;
    }

    /**
     * Get service statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_requests", totalRequests);
        stats.put("total_tokens_processed", totalTokensProcessed);
        stats.put("average_latency_ms", averageLatency);
        stats.put("available_models", modelMappings.keySet());
        stats.put("active_requests", activeRequests.size());
        stats.put("service_name", "Blockchain AGI - No Servers, No Computers");
        return stats;
    }

    // Data classes for OpenAI API compatibility
    
    public static class ChatCompletionRequest {
        public String model;
        public List<ChatMessage> messages;
        public double temperature = 0.7;
        public int maxTokens = 256;
        public double topP = 0.9;
        public double frequencyPenalty = 0.0;
        public double presencePenalty = 0.0;
        public boolean stream = false;
    }
    
    public static class ChatMessage {
        public String role;
        public String content;
    }
    
    public static class ChatCompletionResponse {
        public String id;
        public String object;
        public long created;
        public String model;
        public List<ChatChoice> choices;
        public Usage usage;
    }
    
    public static class ChatChoice {
        public int index;
        public ChatMessage message;
        public String finishReason;
    }
    
    public static class CompletionRequest {
        public String model;
        public String prompt;
        public int maxTokens = 256;
        public double temperature = 0.7;
        public double topP = 0.9;
        public double frequencyPenalty = 0.0;
        public double presencePenalty = 0.0;
        public boolean stream = false;
    }
    
    public static class CompletionResponse {
        public String id;
        public String object;
        public long created;
        public String model;
        public List<TextChoice> choices;
        public Usage usage;
    }
    
    public static class TextChoice {
        public String text;
        public int index;
        public Object logprobs;
        public String finishReason;
    }
    
    public static class Usage {
        public int promptTokens;
        public int completionTokens;
        public int totalTokens;
    }
}
