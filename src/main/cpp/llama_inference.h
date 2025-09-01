// llama_inference.h - C++ Interface for GGUF Model Inference
// Author: Sir Charles Spikes
// Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI

#ifndef LLAMA_INFERENCE_H
#define LLAMA_INFERENCE_H

#include <string>
#include <vector>
#include <memory>
#include <functional>
#include <unordered_map>
#include <mutex>
#include <atomic>
#include <thread>

// Forward declarations
struct llama_model;
struct llama_context;
struct llama_token_data;
struct llama_token_data_array;

namespace ai_blockchain {

// Configuration for model inference
struct InferenceConfig {
    std::string model_path;
    std::string ipfs_hash;
    int n_threads = 4;
    int n_ctx = 4096;
    int n_batch = 512;
    int n_gpu_layers = 35;
    int n_predict = 256;
    float temperature = 0.8f;
    float top_p = 0.9f;
    int top_k = 40;
    float repeat_penalty = 1.1f;
    bool use_mmap = true;
    bool use_mlock = false;
    bool use_cache = true;
};

// Token usage tracking
struct TokenUsage {
    int prompt_tokens = 0;
    int completion_tokens = 0;
    int total_tokens = 0;
    double inference_time_ms = 0.0;
    double tokens_per_second = 0.0;
};

// API-compatible response format
struct ChatCompletionResponse {
    std::string id;
    std::string object = "chat.completion";
    long created;
    std::string model;
    std::vector<ChatMessage> choices;
    Usage usage;
};

struct ChatMessage {
    int index;
    ChatMessageContent message;
    std::string finish_reason;
};

struct ChatMessageContent {
    std::string role;
    std::string content;
};

struct Usage {
    int prompt_tokens;
    int completion_tokens;
    int total_tokens;
};

// Main inference engine class
class LlamaInference {
public:
    LlamaInference();
    ~LlamaInference();

    // Initialize with model from IPFS
    bool initialize(const InferenceConfig& config);

    // Load model from IPFS hash
    bool loadModelFromIPFS(const std::string& ipfs_hash, const std::string& model_path);

    // Chat completion (OpenAI-compatible)
    ChatCompletionResponse chatCompletion(
        const std::vector<ChatMessageContent>& messages,
        const InferenceConfig& config = InferenceConfig()
    );

    // Streaming completion
    void chatCompletionStream(
        const std::vector<ChatMessageContent>& messages,
        std::function<void(const std::string&)> on_token,
        std::function<void(const ChatCompletionResponse&)> on_complete,
        const InferenceConfig& config = InferenceConfig()
    );

    // Text generation
    std::string generateText(
        const std::string& prompt,
        const InferenceConfig& config = InferenceConfig()
    );

    // Get model information
    std::unordered_map<std::string, std::string> getModelInfo();

    // Check if model is loaded
    bool isModelLoaded() const;

    // Get last token usage
    TokenUsage getLastTokenUsage() const;

    // Unload model
    void unloadModel();

private:
    // llama.cpp model and context
    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;

    // Model metadata
    std::string model_path_;
    std::string ipfs_hash_;
    std::unordered_map<std::string, std::string> model_info_;

    // Token usage tracking
    std::atomic<TokenUsage> last_usage_;
    std::mutex usage_mutex_;

    // Internal methods
    std::vector<int> tokenize(const std::string& text, bool add_bos = true);
    std::string detokenize(const std::vector<int>& tokens);

    std::string formatChatMessages(const std::vector<ChatMessageContent>& messages);
    std::vector<int> sample_token(llama_token_data_array* candidates);

    bool ensureModelLoaded();
    bool downloadFromIPFS(const std::string& ipfs_hash, const std::string& output_path);

    // Thread safety
    std::mutex model_mutex_;
    std::atomic<bool> is_processing_{false};
};

// Model cache for multiple models
class ModelCache {
public:
    static ModelCache& getInstance();

    std::shared_ptr<LlamaInference> getModel(const std::string& model_id);
    void addModel(const std::string& model_id, std::shared_ptr<LlamaInference> model);
    void removeModel(const std::string& model_id);
    void clearCache();

private:
    ModelCache() = default;
    std::unordered_map<std::string, std::shared_ptr<LlamaInference>> models_;
    std::mutex cache_mutex_;
};

// Edge computing coordinator
class EdgeCoordinator {
public:
    EdgeCoordinator();
    ~EdgeCoordinator();

    // Register edge device
    void registerDevice(const std::string& device_id, const DeviceCapabilities& caps);

    // Get optimal device for model
    std::string getOptimalDevice(const std::string& model_id, const InferenceConfig& config);

    // Distribute inference across devices
    std::string distributeInference(
        const std::string& model_id,
        const std::string& prompt,
        const InferenceConfig& config
    );

    // Monitor device health
    void monitorDevices();

private:
    struct DeviceCapabilities {
        std::string device_id;
        int cpu_cores;
        int gpu_cores;
        long memory_mb;
        long vram_mb;
        double performance_score;
        bool is_online;
    };

    std::unordered_map<std::string, DeviceCapabilities> devices_;
    std::mutex devices_mutex_;
    std::thread monitor_thread_;
    std::atomic<bool> monitoring_{true};
};

// JNI interface for Java integration
#ifdef __cplusplus
extern "C" {
#endif

// JNI methods for Java integration
void* create_inference_engine();
void destroy_inference_engine(void* engine);

bool initialize_engine(void* engine, const char* ipfs_hash, const char* config_json);
char* chat_completion(void* engine, const char* messages_json, const char* config_json);
char* generate_text(void* engine, const char* prompt, const char* config_json);
char* get_model_info(void* engine);
char* get_token_usage(void* engine);

#ifdef __cplusplus
}
#endif

} // namespace ai_blockchain

#endif // LLAMA_INFERENCE_H
