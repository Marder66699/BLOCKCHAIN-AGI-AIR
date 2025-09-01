/**
 * Local GGUF Processor - Main application for local AI processing triggered by blockchain
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sircharlesspikes.ai.blockchain.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class LocalGGUFProcessor {
    private static final Logger logger = LoggerFactory.getLogger(LocalGGUFProcessor.class);

    private final Properties config;
    private final LocalGGUFRunner ggufRunner;
    private final BlockchainEventListener eventListener;
    private final ScheduledExecutorService statusReporter;
    private volatile boolean running = false;

    public LocalGGUFProcessor(Properties config) {
        this.config = config;

        // Initialize services
        this.ggufRunner = new LocalGGUFRunner(config);
        this.eventListener = new BlockchainEventListener(config, ggufRunner);

        this.statusReporter = Executors.newScheduledThreadPool(1);

        logger.info("===============================================");
        logger.info("LOCAL GGUF PROCESSOR");
        logger.info("By Sir Charles Spikes");
        logger.info("Cincinnati, Ohio");
        logger.info("Contact: SirCharlesspikes5@gmail.com");
        logger.info("Telegram: @SirGODSATANAGI");
        logger.info("===============================================\n");
    }

    /**
     * Start the local GGUF processor
     */
    public void start() throws Exception {
        if (running) {
            logger.warn("Processor is already running");
            return;
        }

        running = true;
        logger.info("Starting Local GGUF Processor...");

        // Register node with blockchain
        registerNodeWithBlockchain();

        // Start event listener
        eventListener.startListening();

        // Start status reporting
        startStatusReporting();

        // Setup signal handlers
        setupSignalHandlers();

        logger.info("Local GGUF Processor started successfully");
        logger.info("Node is listening for blockchain events...");
        logger.info("Supported operations:");
        logger.info("  • Model registration with Filebase/IPFS");
        logger.info("  • Local GGUF model processing");
        logger.info("  • Blockchain-triggered AI requests");
        logger.info("  • Result delivery to users");
        logger.info("");
    }

    /**
     * Register this node with the blockchain
     */
    private void registerNodeWithBlockchain() throws Exception {
        logger.info("Registering node with blockchain...");

        String nodeId = config.getProperty("node.id", "local-node-" + System.currentTimeMillis());
        List<String> supportedModels = Arrays.asList(
            "llama2-7b-q4_0",
            "llama2-13b-q4_0",
            "mistral-7b-q4_0",
            "codellama-7b-q4_0"
        );

        String txHash = eventListener.registerNode(nodeId, supportedModels).get(30, TimeUnit.SECONDS);
        logger.info("Node registered successfully: {}", txHash);
    }

    /**
     * Upload a GGUF model to Filebase and register with blockchain
     */
    public String uploadAndRegisterModel(Path modelPath, String modelType, String baseModel,
                                       String quantizationType) throws Exception {
        logger.info("Uploading and registering model: {}", modelPath.getFileName());

        // Upload to Filebase
        String ipfsHash = ggufRunner.uploadModelToFilebase(modelPath);
        logger.info("Model uploaded to Filebase. IPFS Hash: {}", ipfsHash);

        // Register with smart contract
        String txHash = ggufRunner.registerModelOnChain(modelPath);
        logger.info("Model registered on blockchain. TX: {}", txHash);

        logger.info("Model registration complete!");
        logger.info("IPFS Hash: {}", ipfsHash);
        logger.info("Blockchain TX: {}", txHash);

        return ipfsHash;
    }

    /**
     * Submit a processing request (for testing)
     */
    public String submitProcessingRequest(String ipfsHash, String prompt, String apiKey) throws Exception {
        logger.info("Submitting processing request...");

        // This would call the smart contract
        // For demo, simulate the request
        String requestId = UUID.randomUUID().toString();

        logger.info("Processing request submitted:");
        logger.info("  Request ID: {}", requestId);
        logger.info("  IPFS Hash: {}", ipfsHash);
        logger.info("  API Key: {}", apiKey);

        return requestId;
    }

    /**
     * Start status reporting
     */
    private void startStatusReporting() {
        statusReporter.scheduleAtFixedRate(() -> {
            try {
                reportStatus();
            } catch (Exception e) {
                logger.error("Error reporting status", e);
            }
        }, 30, 60, TimeUnit.SECONDS); // Report every minute
    }

    /**
     * Report current status
     */
    private void reportStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("timestamp", System.currentTimeMillis());
        status.put("ggufRunner", ggufRunner.getStatus());
        status.put("eventListener", eventListener.getStatus());

        logger.info("Status Report: {}", new ObjectMapper().writeValueAsString(status));
    }

    /**
     * Setup signal handlers for graceful shutdown
     */
    private void setupSignalHandlers() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received...");
            stop();
        }));
    }

    /**
     * Stop the processor
     */
    public void stop() {
        logger.info("Stopping Local GGUF Processor...");

        running = false;

        if (eventListener != null) {
            eventListener.stopListening();
        }

        if (ggufRunner != null) {
            ggufRunner.shutdown();
        }

        statusReporter.shutdown();

        try {
            if (!statusReporter.awaitTermination(10, TimeUnit.SECONDS)) {
                statusReporter.shutdownNow();
            }
        } catch (InterruptedException e) {
            statusReporter.shutdownNow();
        }

        logger.info("Local GGUF Processor stopped");
    }

    /**
     * Get current status
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", running);
        status.put("config", config);
        status.put("ggufRunner", ggufRunner.getStatus());
        status.put("eventListener", eventListener.getStatus());

        return status;
    }

    /**
     * Command-line interface
     */
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

        LocalGGUFProcessor processor = new LocalGGUFProcessor(config);

        // Parse command line arguments
        if (args.length > 0) {
            switch (args[0]) {
                case "upload":
                    if (args.length < 2) {
                        System.err.println("Usage: upload <model-path> [model-type] [base-model] [quantization]");
                        System.exit(1);
                    }

                    Path modelPath = Paths.get(args[1]);
                    String modelType = args.length > 2 ? args[2] : "llama";
                    String baseModel = args.length > 3 ? args[3] : "llama2";
                    String quantization = args.length > 4 ? args[4] : "q4_0";

                    String ipfsHash = processor.uploadAndRegisterModel(modelPath, modelType, baseModel, quantization);
                    System.out.println("Model uploaded and registered!");
                    System.out.println("IPFS Hash: " + ipfsHash);
                    break;

                case "submit":
                    if (args.length < 4) {
                        System.err.println("Usage: submit <ipfs-hash> <prompt> <api-key>");
                        System.exit(1);
                    }

                    String requestId = processor.submitProcessingRequest(args[1], args[2], args[3]);
                    System.out.println("Processing request submitted!");
                    System.out.println("Request ID: " + requestId);
                    break;

                case "status":
                    Map<String, Object> status = processor.getStatus();
                    System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(status));
                    break;

                default:
                    System.out.println("Usage:");
                    System.out.println("  upload <model-path> [options]  - Upload and register model");
                    System.out.println("  submit <ipfs-hash> <prompt> <api-key> - Submit processing request");
                    System.out.println("  status                         - Show current status");
                    System.out.println("  (no args)                      - Start processor");
                    break;
            }

            // Exit for command mode
            if (args.length > 0) {
                return;
            }
        }

        // Start processor
        processor.start();

        // Keep main thread alive
        Thread.currentThread().join();
    }
}
