/**
 * Local GGUF Runner - Runs GGUF models locally triggered by blockchain events
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class LocalGGUFRunner {
    private static final Logger logger = LoggerFactory.getLogger(LocalGGUFRunner.class);

    private final String nodeId;
    private final Web3j web3j;
    private final Credentials credentials;
    private final String contractAddress;
    private final String modelsDirectory;
    private final String filebaseGateway;
    private final ExecutorService processingExecutor;
    private final Map<String, ProcessingTask> activeTasks;

    // Filebase/IPFS integration
    private final String filebaseApiKey;
    private final String filebaseApiSecret;

    public LocalGGUFRunner(Properties config) {
        this.nodeId = config.getProperty("node.id", "local-node-" + System.currentTimeMillis());
        this.modelsDirectory = config.getProperty("models.directory", "./models");
        this.filebaseGateway = config.getProperty("filebase.gateway", "https://ipfs.filebase.io/ipfs/");
        this.contractAddress = config.getProperty("contract.gguf.address");
        this.filebaseApiKey = config.getProperty("filebase.api.key");
        this.filebaseApiSecret = config.getProperty("filebase.api.secret");

        // Initialize Web3
        String rpcUrl = config.getProperty("ethereum.rpc.url", "http://localhost:8545");
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        String privateKey = config.getProperty("ethereum.private.key");
        this.credentials = Credentials.create(privateKey);

        this.processingExecutor = Executors.newFixedThreadPool(
            Integer.parseInt(config.getProperty("max.concurrent.tasks", "3")));
        this.activeTasks = new ConcurrentHashMap<>();

        // Ensure models directory exists
        try {
            Files.createDirectories(Paths.get(modelsDirectory));
        } catch (IOException e) {
            logger.error("Failed to create models directory", e);
        }

        logger.info("Local GGUF Runner initialized for node: {}", nodeId);
    }

    /**
     * Processing task representing a blockchain-triggered AI request
     */
    public static class ProcessingTask {
        public String taskId;
        public String ipfsHash;
        public String userAddress;
        public String apiKey;
        public String prompt;
        public long timestamp;
        public TaskStatus status;
        public String result;
        public long processingTimeMs;

        public ProcessingTask(String taskId, String ipfsHash, String userAddress,
                            String apiKey, String prompt) {
            this.taskId = taskId;
            this.ipfsHash = ipfsHash;
            this.userAddress = userAddress;
            this.apiKey = apiKey;
            this.prompt = prompt;
            this.timestamp = System.currentTimeMillis();
            this.status = TaskStatus.PENDING;
        }
    }

    public enum TaskStatus {
        PENDING,
        DOWNLOADING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    /**
     * Upload GGUF model to Filebase/IPFS
     * @param modelPath Local path to GGUF model
     * @return IPFS hash
     */
    public String uploadModelToFilebase(Path modelPath) throws Exception {
        logger.info("Uploading model to Filebase: {}", modelPath);

        if (!Files.exists(modelPath)) {
            throw new FileNotFoundException("Model file not found: " + modelPath);
        }

        // Filebase upload via API
        String uploadUrl = "https://api.filebase.io/v1/ipfs/upload";

        URL url = new URL(uploadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // Set authentication
        String auth = filebaseApiKey + ":" + filebaseApiSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("X-Name", modelPath.getFileName().toString());

        // Upload file
        try (FileInputStream fis = new FileInputStream(modelPath.toFile());
             OutputStream os = connection.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Filebase upload failed with code: " + responseCode);
        }

        // Parse response
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> jsonResponse = mapper.readValue(response.toString(),
                mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));

            String ipfsHash = (String) jsonResponse.get("cid");
            logger.info("Model uploaded successfully. IPFS Hash: {}", ipfsHash);
            return ipfsHash;
        }
    }

    /**
     * Download GGUF model from Filebase/IPFS
     * @param ipfsHash IPFS hash of the model
     * @return Local path to downloaded model
     */
    public Path downloadModelFromFilebase(String ipfsHash) throws Exception {
        logger.info("Downloading model from Filebase: {}", ipfsHash);

        Path localPath = Paths.get(modelsDirectory, ipfsHash + ".gguf");

        // Check if already downloaded
        if (Files.exists(localPath)) {
            logger.info("Model already exists locally: {}", localPath);
            return localPath;
        }

        String downloadUrl = filebaseGateway + ipfsHash;

        URL url = new URL(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        long contentLength = connection.getContentLengthLong();
        logger.info("Downloading {} bytes...", contentLength);

        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(localPath))) {

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            int bytesRead;
            long lastLog = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloaded += bytesRead;

                // Log progress every 5 seconds
                if (System.currentTimeMillis() - lastLog > 5000) {
                    double progress = (double) downloaded / contentLength * 100;
                    logger.info("Download progress: {:.2f}% ({} / {} bytes)",
                        progress, downloaded, contentLength);
                    lastLog = System.currentTimeMillis();
                }
            }
        }

        logger.info("Model downloaded successfully: {}", localPath);
        return localPath;
    }

    /**
     * Register model with smart contract
     * @param modelPath Local model path
     * @return Transaction hash
     */
    public String registerModelOnChain(Path modelPath) throws Exception {
        logger.info("Registering model on blockchain: {}", modelPath.getFileName());

        // Upload to Filebase first
        String ipfsHash = uploadModelToFilebase(modelPath);

        // Get model metadata
        GGUFModelMetadata metadata = analyzeGGUFMetadata(modelPath, ipfsHash);

        // Call smart contract to register model
        // This would interact with your QuantizedModelRegistry contract
        String txHash = registerModelInContract(metadata);

        logger.info("Model registered on blockchain. TX: {}", txHash);
        return txHash;
    }

    /**
     * Analyze GGUF model metadata
     */
    private GGUFModelMetadata analyzeGGUFMetadata(Path modelPath, String ipfsHash) {
        GGUFModelMetadata metadata = new GGUFModelMetadata();
        metadata.ipfsHash = ipfsHash;
        metadata.fileSize = modelPath.toFile().length();

        // Extract model info (simplified - in production, parse GGUF headers)
        String fileName = modelPath.getFileName().toString();
        if (fileName.contains("llama")) {
            metadata.modelType = "llama";
            metadata.baseModel = "llama2";
        } else if (fileName.contains("mistral")) {
            metadata.modelType = "mistral";
            metadata.baseModel = "mistral";
        }

        // Extract quantization type
        if (fileName.contains("q4_0")) {
            metadata.quantizationType = "q4_0";
        } else if (fileName.contains("q8_0")) {
            metadata.quantizationType = "q8_0";
        }

        return metadata;
    }

    /**
     * Register model in smart contract
     */
    private String registerModelInContract(GGUFModelMetadata metadata) {
        // This would be the actual smart contract interaction
        // For now, return a mock transaction hash
        return "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 64);
    }

    /**
     * Start listening for blockchain events
     */
    public void startEventListener() {
        logger.info("Starting blockchain event listener...");

        // Listen for AI processing requests from smart contract
        // This would use Web3j event filtering
        Executors.newSingleThreadExecutor().submit(this::listenForProcessingRequests);
    }

    /**
     * Listen for processing requests from smart contract
     */
    private void listenForProcessingRequests() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Poll for new processing requests
                // In production, this would use Web3j event filters
                List<ProcessingRequest> requests = pollForProcessingRequests();

                for (ProcessingRequest request : requests) {
                    if (!activeTasks.containsKey(request.taskId)) {
                        ProcessingTask task = new ProcessingTask(
                            request.taskId,
                            request.ipfsHash,
                            request.userAddress,
                            request.apiKey,
                            request.prompt
                        );

                        activeTasks.put(request.taskId, task);
                        processingExecutor.submit(() -> processTask(task));
                    }
                }

                Thread.sleep(5000); // Poll every 5 seconds

            } catch (Exception e) {
                logger.error("Error in event listener", e);
                try {
                    Thread.sleep(10000); // Wait longer on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Poll for processing requests (mock implementation)
     */
    private List<ProcessingRequest> pollForProcessingRequests() {
        // In production, this would query the smart contract for pending requests
        // For demo purposes, return empty list
        return new ArrayList<>();
    }

    /**
     * Process a blockchain-triggered task
     */
    private void processTask(ProcessingTask task) {
        logger.info("Processing task: {}", task.taskId);

        try {
            task.status = TaskStatus.DOWNLOADING;

            // Download model from Filebase
            Path modelPath = downloadModelFromFilebase(task.ipfsHash);

            task.status = TaskStatus.PROCESSING;

            // Process with local GGUF model
            long startTime = System.currentTimeMillis();
            String result = processWithGGUF(modelPath, task.prompt);
            long endTime = System.currentTimeMillis();

            task.result = result;
            task.processingTimeMs = endTime - startTime;
            task.status = TaskStatus.COMPLETED;

            // Store result back to blockchain
            storeResultOnChain(task);

            // Send result back to user via API
            sendResultToUser(task);

            logger.info("Task completed: {} in {}ms", task.taskId, task.processingTimeMs);

        } catch (Exception e) {
            logger.error("Task failed: {}", task.taskId, e);
            task.status = TaskStatus.FAILED;
        } finally {
            activeTasks.remove(task.taskId);
        }
    }

    /**
     * Process prompt with local GGUF model
     */
    private String processWithGGUF(Path modelPath, String prompt) throws Exception {
        // This would integrate with your C++ llama.cpp implementation
        // For now, return a mock response
        logger.info("Processing prompt with GGUF model: {}", prompt);

        // Simulate processing time
        Thread.sleep(2000);

        return "AI Response to: " + prompt + " (processed by local GGUF model at " + new Date() + ")";
    }

    /**
     * Store result back to blockchain
     */
    private void storeResultOnChain(ProcessingTask task) throws Exception {
        logger.info("Storing result on blockchain for task: {}", task.taskId);

        // This would call the smart contract to store the result
        // The result would be associated with the task ID and user
    }

    /**
     * Send result back to user via API
     */
    private void sendResultToUser(ProcessingTask task) throws Exception {
        logger.info("Sending result to user: {}", task.userAddress);

        // This could send the result via:
        // 1. Webhook to user's API endpoint
        // 2. Push notification
        // 3. Store in user's blockchain account

        // For demo, just log the result
        logger.info("Result for {}: {}", task.apiKey, task.result);
    }

    /**
     * Get active tasks status
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("nodeId", nodeId);
        status.put("activeTasks", activeTasks.size());
        status.put("modelsDirectory", modelsDirectory);
        status.put("contractAddress", contractAddress);

        List<Map<String, Object>> taskList = new ArrayList<>();
        for (ProcessingTask task : activeTasks.values()) {
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("taskId", task.taskId);
            taskInfo.put("status", task.status.toString());
            taskInfo.put("ipfsHash", task.ipfsHash);
            taskInfo.put("timestamp", task.timestamp);
            taskList.add(taskInfo);
        }

        status.put("tasks", taskList);
        return status;
    }

    /**
     * Shutdown the runner
     */
    public void shutdown() {
        logger.info("Shutting down Local GGUF Runner...");

        processingExecutor.shutdown();
        try {
            if (!processingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processingExecutor.shutdownNow();
        }

        web3j.shutdown();
        logger.info("Local GGUF Runner shutdown complete");
    }

    // Helper classes
    public static class GGUFModelMetadata {
        public String ipfsHash;
        public String modelType;
        public String baseModel;
        public String quantizationType;
        public long fileSize;
        public long parameterCount;
        public int contextSize;
    }

    public static class ProcessingRequest {
        public String taskId;
        public String ipfsHash;
        public String userAddress;
        public String apiKey;
        public String prompt;
    }
}
