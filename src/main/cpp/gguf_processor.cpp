// gguf_processor.cpp - C++ GGUF Model Processor for Hedera AI
// Author: Sir Charles Spikes
// Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI

#include <iostream>
#include <string>
#include <vector>
#include <thread>
#include <mutex>
#include <atomic>
#include <chrono>
#include <random>
#include <sstream>
#include <iomanip>
#include <fstream>
#include <filesystem>

// Include llama.cpp headers (assuming they're in the include path)
// You'll need to build llama.cpp and include its headers
#include "llama.h"

// JSON library for result formatting
#include <nlohmann/json.hpp>

// Filebase/IPFS integration
#include <curl/curl.h>

// For Windows compatibility
#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#endif

namespace ai_blockchain {

// GGUF Processor class
class GGUFProcessor {
private:
    // Model management
    llama_model* model_ = nullptr;
    llama_context* ctx_ = nullptr;
    std::string model_path_;
    std::string model_hash_;

    // Processing statistics
    std::atomic<long long> total_tokens_processed_{0};
    std::atomic<long long> total_requests_processed_{0};
    std::atomic<double> average_processing_time_{0.0};

    // Thread safety
    std::mutex processing_mutex_;

    // Configuration
    struct Config {
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
    } config_;

public:
    GGUFProcessor() = default;
    ~GGUFProcessor() {
        unloadModel();
    }

    // Initialize with Gemma model
    bool initialize(const std::string& model_path = "models/gemma-3-270m-it-qat-Q4_0.gguf") {
        std::cout << "🚀 Initializing GGUF Processor..." << std::endl;
        std::cout << "📁 Model path: " << model_path << std::endl;

        model_path_ = model_path;
        model_hash_ = "QmXT2xkFnG7FP7NTfmDfDFcQLSfCJ3xfPnjCg76gFnq1Hr"; // Gemma hash

        // Initialize llama.cpp
        llama_backend_init();

        // Load model
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = config_.n_gpu_layers;
        model_params.use_mmap = config_.use_mmap;
        model_params.use_mlock = config_.use_mlock;

        std::cout << "📥 Loading GGUF model..." << std::endl;
        model_ = llama_load_model_from_file(model_path_.c_str(), model_params);

        if (model_ == nullptr) {
            std::cerr << "❌ Failed to load model: " << model_path_ << std::endl;
            return false;
        }

        // Create context
        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = config_.n_ctx;
        ctx_params.n_batch = config_.n_batch;
        ctx_params.n_threads = config_.n_threads;

        ctx_ = llama_new_context_with_model(model_, ctx_params);

        if (ctx_ == nullptr) {
            std::cerr << "❌ Failed to create context" << std::endl;
            llama_free_model(model_);
            model_ = nullptr;
            return false;
        }

        // Print model information
        printModelInfo();

        std::cout << "✅ GGUF Processor initialized successfully!" << std::endl;
        std::cout << "🎯 Ready to process AI requests from Hedera blockchain" << std::endl;

        return true;
    }

    // Process AI request
    std::string processRequest(const std::string& prompt, const std::string& request_id) {
        std::lock_guard<std::mutex> lock(processing_mutex_);

        if (!isModelLoaded()) {
            return createErrorResponse("Model not loaded", request_id);
        }

        auto start_time = std::chrono::high_resolution_clock::now();

        try {
            std::cout << "🎯 Processing request: " << request_id << std::endl;
            std::cout << "❓ Prompt: " << prompt.substr(0, 100) << "..." << std::endl;

            // Tokenize prompt
            std::vector<int> tokens = tokenize(prompt);

            std::cout << "🔢 Tokens: " << tokens.size() << std::endl;

            // Generate response
            std::vector<int> response_tokens = generate(tokens);

            // Detokenize response
            std::string response_text = detokenize(response_tokens);

            auto end_time = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(
                end_time - start_time);

            // Update statistics
            total_requests_processed_++;
            total_tokens_processed_ += tokens.size() + response_tokens.size();

            // Calculate moving average processing time
            double current_time = duration.count();
            average_processing_time_ = (average_processing_time_ * 0.99) + (current_time * 0.01);

            std::cout << "✅ Request completed in " << duration.count() << "ms" << std::endl;
            std::cout << "📊 Response tokens: " << response_tokens.size() << std::endl;

            return createSuccessResponse(response_text, request_id, duration.count());

        } catch (const std::exception& e) {
            std::cerr << "❌ Processing error: " << e.what() << std::endl;
            return createErrorResponse(e.what(), request_id);
        }
    }

    // Get processing statistics
    nlohmann::json getStats() {
        nlohmann::json stats;
        stats["model_loaded"] = isModelLoaded();
        stats["model_path"] = model_path_;
        stats["model_hash"] = model_hash_;
        stats["total_requests"] = total_requests_processed_.load();
        stats["total_tokens"] = total_tokens_processed_.load();
        stats["avg_processing_time_ms"] = average_processing_time_.load();
        stats["config"] = {
            {"n_threads", config_.n_threads},
            {"n_ctx", config_.n_ctx},
            {"temperature", config_.temperature},
            {"top_p", config_.top_p}
        };
        return stats;
    }

private:
    // Tokenize text
    std::vector<int> tokenize(const std::string& text) {
        std::vector<int> tokens(text.size() + 4); // Add some padding

        int n_tokens = llama_tokenize(
            model_,
            text.c_str(),
            text.size(),
            tokens.data(),
            tokens.size(),
            true,  // Add BOS
            false  // Special tokens
        );

        tokens.resize(n_tokens);
        return tokens;
    }

    // Generate response tokens
    std::vector<int> generate(const std::vector<int>& prompt_tokens) {
        std::vector<int> response_tokens;

        // Evaluate prompt
        if (llama_eval(ctx_, prompt_tokens.data(), prompt_tokens.size(), 0, config_.n_threads) != 0) {
            throw std::runtime_error("Failed to evaluate prompt");
        }

        // Generate tokens
        for (int i = 0; i < config_.n_predict; ++i) {
            // Sample next token
            int token = sample_token();

            if (token == llama_token_eos(model_)) {
                break; // End of sequence
            }

            response_tokens.push_back(token);

            // Evaluate new token
            if (llama_eval(ctx_, &token, 1, prompt_tokens.size() + response_tokens.size(),
                          config_.n_threads) != 0) {
                throw std::runtime_error("Failed to evaluate token");
            }
        }

        return response_tokens;
    }

    // Sample next token (simplified greedy sampling)
    int sample_token() {
        // Get logits
        auto* logits = llama_get_logits(ctx_);
        int n_vocab = llama_n_vocab(model_);

        // Find token with highest logit (greedy)
        int best_token = 0;
        float best_logit = logits[0];

        for (int i = 1; i < n_vocab; ++i) {
            if (logits[i] > best_logit) {
                best_logit = logits[i];
                best_token = i;
            }
        }

        return best_token;
    }

    // Detokenize tokens to text
    std::string detokenize(const std::vector<int>& tokens) {
        std::string result;

        for (int token : tokens) {
            std::vector<char> buf(256);
            int n_chars = llama_token_to_piece(model_, token, buf.data(), buf.size());

            if (n_chars > 0) {
                result.append(buf.data(), n_chars);
            }
        }

        return result;
    }

    // Create success response
    std::string createSuccessResponse(const std::string& text, const std::string& request_id, long processing_time) {
        nlohmann::json response;
        response["success"] = true;
        response["request_id"] = request_id;
        response["response"] = text;
        response["processing_time_ms"] = processing_time;
        response["model"] = "gemma-3-270m-q4_0";
        response["timestamp"] = std::chrono::system_clock::now().time_since_epoch().count();

        return response.dump();
    }

    // Create error response
    std::string createErrorResponse(const std::string& error, const std::string& request_id) {
        nlohmann::json response;
        response["success"] = false;
        response["request_id"] = request_id;
        response["error"] = error;
        response["timestamp"] = std::chrono::system_clock::now().time_since_epoch().count();

        return response.dump();
    }

    // Print model information
    void printModelInfo() {
        if (!isModelLoaded()) return;

        std::cout << "📊 Model Information:" << std::endl;
        std::cout << "  • Vocab size: " << llama_n_vocab(model_) << std::endl;
        std::cout << "  • Context size: " << llama_n_ctx(ctx_) << std::endl;
        std::cout << "  • Embedding size: " << llama_n_embd(model_) << std::endl;
        std::cout << "  • Threads: " << config_.n_threads << std::endl;
        std::cout << "  • GPU Layers: " << config_.n_gpu_layers << std::endl;
    }

    // Check if model is loaded
    bool isModelLoaded() const {
        return model_ != nullptr && ctx_ != nullptr;
    }

    // Unload model
    void unloadModel() {
        if (ctx_ != nullptr) {
            llama_free(ctx_);
            ctx_ = nullptr;
        }

        if (model_ != nullptr) {
            llama_free_model(model_);
            model_ = nullptr;
        }
    }
};

// Global processor instance
static GGUFProcessor* global_processor = nullptr;
static std::mutex global_mutex;

// JNI interface for Java integration
extern "C" {

// Initialize GGUF processor
bool initialize_processor(const char* model_path) {
    std::lock_guard<std::mutex> lock(global_mutex);

    if (global_processor != nullptr) {
        delete global_processor;
    }

    global_processor = new GGUFProcessor();
    return global_processor->initialize(model_path);
}

// Process AI request
const char* process_request(const char* prompt, const char* request_id) {
    std::lock_guard<std::mutex> lock(global_mutex);

    if (global_processor == nullptr) {
        return "{\"success\":false,\"error\":\"Processor not initialized\"}";
    }

    static std::string last_result;
    last_result = global_processor->processRequest(prompt, request_id);
    return last_result.c_str();
}

// Get processor statistics
const char* get_processor_stats() {
    std::lock_guard<std::mutex> lock(global_mutex);

    if (global_processor == nullptr) {
        return "{\"error\":\"Processor not initialized\"}";
    }

    static std::string stats_result;
    stats_result = global_processor->getStats().dump();
    return stats_result.c_str();
}

// Shutdown processor
void shutdown_processor() {
    std::lock_guard<std::mutex> lock(global_mutex);

    if (global_processor != nullptr) {
        delete global_processor;
        global_processor = nullptr;
    }
}

} // extern "C"

// Main function for standalone testing
int main(int argc, char* argv[]) {
    std::cout << "🤖 GGUF Processor for Hedera AI" << std::endl;
    std::cout << "By Sir Charles Spikes" << std::endl;
    std::cout << std::endl;

    // Default model path
    std::string model_path = "models/gemma-3-270m-it-qat-Q4_0.gguf";

    if (argc > 1) {
        model_path = argv[1];
    }

    // Initialize processor
    GGUFProcessor processor;
    if (!processor.initialize(model_path)) {
        std::cerr << "Failed to initialize processor" << std::endl;
        return 1;
    }

    std::cout << "🎯 Processor ready! Type your prompts (or 'quit' to exit):" << std::endl;
    std::cout << std::endl;

    // Interactive loop
    std::string prompt;
    int request_count = 0;

    while (true) {
        std::cout << "❓ Prompt: ";
        std::getline(std::cin, prompt);

        if (prompt == "quit" || prompt == "exit") {
            break;
        }

        if (prompt.empty()) {
            continue;
        }

        request_count++;
        std::string request_id = "local_" + std::to_string(request_count);

        // Process request
        std::string result = processor.processRequest(prompt, request_id);

        // Parse and display result
        try {
            nlohmann::json response = nlohmann::json::parse(result);

            if (response["success"]) {
                std::cout << "🤖 Response: " << response["response"] << std::endl;
                std::cout << "⚡ Processing time: " << response["processing_time_ms"] << "ms" << std::endl;
            } else {
                std::cout << "❌ Error: " << response["error"] << std::endl;
            }
        } catch (const std::exception& e) {
            std::cout << "❌ Failed to parse response: " << e.what() << std::endl;
        }

        std::cout << std::endl;
    }

    std::cout << "👋 Goodbye!" << std::endl;
    return 0;
}

} // namespace ai_blockchain
