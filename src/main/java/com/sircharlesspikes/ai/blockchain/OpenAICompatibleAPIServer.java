/**
 * OpenAI Compatible API Server - Main entry point for ChatGPT-like API
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain;

import com.sircharlesspikes.ai.blockchain.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class OpenAICompatibleAPIServer {
    private static final Logger logger = LoggerFactory.getLogger(OpenAICompatibleAPIServer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAIApiService apiService;
    private final EdgeCoordinator edgeCoordinator;
    private final LlamaCppModelLoader modelLoader;
    private final QuantizedModelManager quantizedManager;

    private ServerSocket serverSocket;
    private ExecutorService requestExecutor;
    private volatile boolean running = false;

    public OpenAICompatibleAPIServer(int port, Properties config) throws Exception {
        logger.info("===============================================");
        logger.info("AI ON BLOCKCHAIN - OPENAI API SERVER");
        logger.info("By Sir Charles Spikes");
        logger.info("Cincinnati, Ohio");
        logger.info("Contact: SirCharlesspikes5@gmail.com");
        logger.info("Telegram: @SirGODSATANAGI");
        logger.info("===============================================\n");

        // Initialize Web3
        String rpcUrl = config.getProperty("ethereum.rpc.url", "http://localhost:8545");
        Web3j web3j = Web3j.build(new HttpService(rpcUrl));

        String privateKey = config.getProperty("ethereum.private.key");
        Credentials credentials = Credentials.create(privateKey);

        // Initialize services
        this.modelLoader = new LlamaCppModelLoader(
            config.getProperty("models.directory", "./models"),
            config.getProperty("ipfs.gateway", "https://ipfs.io"),
            web3j,
            config.getProperty("contract.nft.address")
        );

        this.quantizedManager = new QuantizedModelManager(
            config.getProperty("llamacpp.path", "./llama.cpp"),
            config.getProperty("models.directory", "./models")
        );

        this.edgeCoordinator = new EdgeCoordinator();
        this.apiService = new OpenAIApiService(modelLoader, quantizedManager, edgeCoordinator, web3j, credentials);

        // Register edge devices (in production, this would be dynamic)
        registerSampleDevices();

        // Start API server
        startServer(port);
    }

    private void registerSampleDevices() {
        // Register local device
        EdgeCoordinator.DeviceCapabilities localCaps = new EdgeCoordinator.DeviceCapabilities();
        localCaps.cpuCores = Runtime.getRuntime().availableProcessors();
        localCaps.memoryMB = (int) (Runtime.getRuntime().totalMemory() / 1024 / 1024);
        localCaps.vramMB = 0; // Would detect actual VRAM in production
        localCaps.gpuCores = 0;
        localCaps.performanceScore = 1.0;
        localCaps.supportedModels.addAll(Arrays.asList("llama2-7b", "mistral-7b", "codellama-7b"));

        edgeCoordinator.registerDevice("local-device", "127.0.0.1", 8081, localCaps);
        logger.info("Registered local edge device");
    }

    private void startServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        requestExecutor = Executors.newCachedThreadPool();
        running = true;

        logger.info("OpenAI Compatible API Server starting on port: {}", port);
        logger.info("Endpoints:");
        logger.info("  POST /v1/chat/completions - Chat completions");
        logger.info("  GET  /v1/models          - List models");
        logger.info("  GET  /v1/models/{id}     - Get model info");
        logger.info("  POST /v1/completions     - Text completions");
        logger.info("  GET  /health             - Health check");
        logger.info("");

        Thread serverThread = new Thread(this::acceptConnections);
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                requestExecutor.submit(() -> handleRequest(clientSocket));
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    private void handleRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Parse HTTP request
            String requestLine = in.readLine();
            if (requestLine == null) return;

            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts[1];

            // Read headers
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                String[] headerParts = line.split(": ", 2);
                if (headerParts.length == 2) {
                    headers.put(headerParts[0].toLowerCase(), headerParts[1]);
                }
            }

            // Read body if present
            StringBuilder bodyBuilder = new StringBuilder();
            if (headers.containsKey("content-length")) {
                int contentLength = Integer.parseInt(headers.get("content-length"));
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                bodyBuilder.append(bodyChars);
            }

            String body = bodyBuilder.toString();

            // Route request
            handleRoute(method, path, headers, body, out);

        } catch (Exception e) {
            logger.error("Error handling request", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error closing client socket", e);
            }
        }
    }

    private void handleRoute(String method, String path, Map<String, String> headers,
                           String body, PrintWriter out) throws Exception {

        // Extract API key from Authorization header
        String apiKey = extractApiKey(headers);

        try {
            switch (path) {
                case "/v1/chat/completions":
                    if (!"POST".equals(method)) {
                        sendMethodNotAllowed(out);
                        return;
                    }
                    handleChatCompletions(apiKey, body, out);
                    break;

                case "/v1/completions":
                    if (!"POST".equals(method)) {
                        sendMethodNotAllowed(out);
                        return;
                    }
                    handleCompletions(apiKey, body, out);
                    break;

                case "/v1/models":
                    if ("GET".equals(method)) {
                        handleListModels(out);
                    } else {
                        sendMethodNotAllowed(out);
                    }
                    break;

                case "/health":
                    if ("GET".equals(method)) {
                        handleHealthCheck(out);
                    } else {
                        sendMethodNotAllowed(out);
                    }
                    break;

                default:
                    if (path.startsWith("/v1/models/")) {
                        if ("GET".equals(method)) {
                            String modelId = path.substring("/v1/models/".length());
                            handleGetModel(modelId, out);
                        } else {
                            sendMethodNotAllowed(out);
                        }
                    } else {
                        sendNotFound(out);
                    }
                    break;
            }

        } catch (SecurityException e) {
            sendError(out, 401, "Unauthorized", e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(out, 400, "Bad Request", e.getMessage());
        } catch (Exception e) {
            logger.error("Request error", e);
            sendError(out, 500, "Internal Server Error", "An unexpected error occurred");
        }
    }

    private void handleChatCompletions(String apiKey, String body, PrintWriter out) throws Exception {
        var response = apiService.handleChatCompletion(apiKey, body);
        sendJsonResponse(out, 200, objectMapper.writeValueAsString(response));
    }

    private void handleCompletions(String apiKey, String body, PrintWriter out) throws Exception {
        // Parse completion request
        Map<String, Object> request = objectMapper.readValue(body, Map.class);
        String model = (String) request.get("model");
        String prompt = (String) request.get("prompt");
        Integer maxTokens = (Integer) request.getOrDefault("max_tokens", 256);

        // Create chat completion format
        Map<String, Object> chatRequest = new HashMap<>();
        chatRequest.put("model", model);
        chatRequest.put("messages", Arrays.asList(
            Map.of("role", "user", "content", prompt)
        ));
        chatRequest.put("max_tokens", maxTokens);

        var response = apiService.handleChatCompletion(apiKey, objectMapper.writeValueAsString(chatRequest));
        sendJsonResponse(out, 200, objectMapper.writeValueAsString(response));
    }

    private void handleListModels(PrintWriter out) throws Exception {
        Map<String, Object> modelsResponse = new HashMap<>();
        modelsResponse.put("object", "list");

        List<Map<String, Object>> modelList = new ArrayList<>();

        // Add available models
        List<String> availableModels = Arrays.asList(
            "gguf-model-llama2-7b-q4_0",
            "gguf-model-llama2-13b-q4_0",
            "gguf-model-mistral-7b-q4_0",
            "gguf-model-codellama-7b-q4_0",
            "gguf-model-llama2-7b-q8_0",
            "gguf-model-llama2-13b-q8_0"
        );

        for (String modelId : availableModels) {
            Map<String, Object> model = new HashMap<>();
            model.put("id", modelId);
            model.put("object", "model");
            model.put("created", System.currentTimeMillis() / 1000);
            model.put("owned_by", "ai-blockchain");

            // Add model capabilities based on name
            List<String> capabilities = new ArrayList<>();
            if (modelId.contains("code")) {
                capabilities.add("code-generation");
                capabilities.add("code-completion");
            } else {
                capabilities.add("text-generation");
                capabilities.add("chat");
            }
            model.put("capabilities", capabilities);

            modelList.add(model);
        }

        modelsResponse.put("data", modelList);
        sendJsonResponse(out, 200, objectMapper.writeValueAsString(modelsResponse));
    }

    private void handleGetModel(String modelId, PrintWriter out) throws Exception {
        Map<String, Object> model = new HashMap<>();
        model.put("id", modelId);
        model.put("object", "model");
        model.put("created", System.currentTimeMillis() / 1000);
        model.put("owned_by", "ai-blockchain");

        sendJsonResponse(out, 200, objectMapper.writeValueAsString(model));
    }

    private void handleHealthCheck(PrintWriter out) throws Exception {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("timestamp", System.currentTimeMillis() / 1000);
        health.put("version", "1.0.0");

        // Add system info
        Map<String, Object> system = new HashMap<>();
        system.put("cpu_cores", Runtime.getRuntime().availableProcessors());
        system.put("total_memory_mb", Runtime.getRuntime().totalMemory() / 1024 / 1024);
        system.put("free_memory_mb", Runtime.getRuntime().freeMemory() / 1024 / 1024);

        health.put("system", system);

        // Add edge coordinator stats
        health.put("edge_devices", edgeCoordinator.getDeviceStats());

        sendJsonResponse(out, 200, objectMapper.writeValueAsString(health));
    }

    private String extractApiKey(Map<String, String> headers) {
        String auth = headers.get("authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    private void sendJsonResponse(PrintWriter out, int statusCode, String jsonBody) {
        out.println("HTTP/1.1 " + statusCode + " OK");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonBody.length());
        out.println("Access-Control-Allow-Origin: *");
        out.println("Access-Control-Allow-Methods: GET, POST, OPTIONS");
        out.println("Access-Control-Allow-Headers: Content-Type, Authorization");
        out.println();
        out.println(jsonBody);
    }

    private void sendError(PrintWriter out, int statusCode, String errorType, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", Map.of(
            "type", errorType,
            "message", message
        ));

        try {
            sendJsonResponse(out, statusCode, objectMapper.writeValueAsString(error));
        } catch (Exception e) {
            out.println("HTTP/1.1 " + statusCode + " Error");
            out.println("Content-Type: application/json");
            out.println();
            out.println("{\"error\":{\"type\":\"" + errorType + "\",\"message\":\"" + message + "\"}}");
        }
    }

    private void sendNotFound(PrintWriter out) {
        sendError(out, 404, "not_found", "The requested resource was not found");
    }

    private void sendMethodNotAllowed(PrintWriter out) {
        sendError(out, 405, "method_not_allowed", "Method not allowed for this endpoint");
    }

    public void shutdown() {
        logger.info("Shutting down OpenAI API Server...");

        running = false;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                logger.error("Error closing server socket", e);
            }
        }

        if (requestExecutor != null) {
            requestExecutor.shutdown();
            try {
                if (!requestExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    requestExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                requestExecutor.shutdownNow();
            }
        }

        // Shutdown services
        edgeCoordinator.shutdown();

        logger.info("OpenAI API Server shutdown complete");
    }

    public static void main(String[] args) throws Exception {
        // Load configuration
        Properties config = new Properties();
        config.load(new FileInputStream("config/node.properties"));

        // Override with environment variables
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("AI_NODE_")) {
                String propKey = key.substring(8).toLowerCase().replace('_', '.');
                config.setProperty(propKey, value);
            }
        });

        int port = Integer.parseInt(System.getProperty("server.port", "8080"));

        OpenAICompatibleAPIServer server = new OpenAICompatibleAPIServer(port, config);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

        // Keep main thread alive
        Thread.currentThread().join();
    }
}
