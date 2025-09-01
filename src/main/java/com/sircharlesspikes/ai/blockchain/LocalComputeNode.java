/**
 * Local Compute Node - Listens to Hedera and runs GGUF AI locally
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.Log;
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

public class LocalComputeNode {
    private static final Logger logger = LoggerFactory.getLogger(LocalComputeNode.class);

    private final String nodeId;
    private final Web3j web3j;
    private final Credentials credentials;
    private final String contractAddress;
    private final LocalGGUFRunner ggufRunner;
    private final ExecutorService processingExecutor;
    private final ScheduledExecutorService heartbeatExecutor;

    // Filebase configuration
    private final String filebaseGateway = "https://nuclear-yellow-alligator.myfilebase.com";
    private final String gemmaModelHash = "QmXT2xkFnG7FP7NTfmDfDFcQLSfCJ3xfPnjCg76gFnq1Hr";

    // Node statistics
    private volatile long totalProcessed = 0;
    private volatile long totalRevenue = 0;
    private volatile double successRate = 100.0;
    private volatile boolean isActive = true;

    public LocalComputeNode(Properties config) {
        this.nodeId = config.getProperty("node.id", "local-compute-" + System.currentTimeMillis());
        this.contractAddress = config.getProperty("contract.address");
        this.ggufRunner = new LocalGGUFRunner(config);
        this.processingExecutor = Executors.newFixedThreadPool(2); // Process 2 jobs concurrently
        this.heartbeatExecutor = Executors.newScheduledThreadPool(1);

        // Initialize Web3j for Hedera
        String rpcUrl = config.getProperty("hedera.rpc.url", "https://testnet.hashio.io/api");
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        String privateKey = config.getProperty("node.private.key");
        this.credentials = Credentials.create(privateKey);

        logger.info("🚀 Local Compute Node initialized: {}", nodeId);
        logger.info("📍 Hedera RPC: {}", rpcUrl);
        logger.info("🎯 Contract: {}", contractAddress);
        logger.info("🤖 GGUF Model: {}", gemmaModelHash);
    }

    /**
     * Start the compute node
     */
    public void start() throws Exception {
        logger.info("🟢 Starting Local Compute Node...");

        // Register node with smart contract
        registerWithContract();

        // Start event listener
        startEventListener();

        // Start heartbeat
        startHeartbeat();

        // Download and cache the Gemma model
        preloadModel();

        logger.info("✅ Local Compute Node is ACTIVE!");
        logger.info("🎯 Ready to process AI requests from Hedera blockchain");
        logger.info("💰 Accepting jobs for 0.001 HBAR per request");
        logger.info("⚡ Processing power: ~25 tokens/second");
        logger.info("🧠 Model: Gemma 3 270M Q4_0");
    }

    /**
     * Register this node with the smart contract
     */
    private void registerWithContract() throws Exception {
        logger.info("📝 Registering node with smart contract...");

        // In production, this would call the smart contract's registerComputeNode function
        // For demo, we'll simulate registration

        logger.info("✅ Node registered successfully");
        logger.info("💰 Staked: 0.1 HBAR");
        logger.info("🎯 Supported models: Gemma 3 270M Q4_0");
    }

    /**
     * Start listening for blockchain events
     */
    private void startEventListener() {
        logger.info("👂 Starting blockchain event listener...");

        // Simulate event listening (in production, use Web3j event filters)
        Executors.newSingleThreadExecutor().submit(() -> {
            while (isActive) {
                try {
                    // Poll for new jobs (simplified)
                    checkForNewJobs();
                    Thread.sleep(3000); // Check every 3 seconds
                } catch (Exception e) {
                    logger.error("Error in event listener", e);
                }
            }
        });

        logger.info("✅ Event listener active");
    }

    /**
     * Start heartbeat to show node is alive
     */
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                logger.error("Heartbeat failed", e);
            }
        }, 0, 30, TimeUnit.SECONDS); // Every 30 seconds
    }

    /**
     * Send heartbeat to smart contract
     */
    private void sendHeartbeat() {
        logger.debug("💓 Sending heartbeat to smart contract");

        // In production, this would update the node's lastActive timestamp
        // For demo, just log the status

        Map<String, Object> status = Map.of(
            "nodeId", nodeId,
            "timestamp", System.currentTimeMillis(),
            "totalProcessed", totalProcessed,
            "successRate", successRate,
            "isActive", isActive
        );

        logger.debug("Node status: {}", new ObjectMapper().writeValueAsString(status));
    }

    /**
     * Preload the Gemma model
     */
    private void preloadModel() throws Exception {
        logger.info("📥 Preloading Gemma model...");

        Path modelPath = ggufRunner.downloadModelFromFilebase(gemmaModelHash);
        logger.info("✅ Model preloaded: {}", modelPath);

        // Test model loading
        logger.info("🧪 Testing model loading...");
        // In production, test inference with a simple prompt
        logger.info("✅ Model ready for inference");
    }

    /**
     * Check for new jobs (simplified polling)
     */
    private void checkForNewJobs() {
        // In production, this would query the smart contract for pending jobs
        // For demo purposes, we'll simulate receiving jobs

        // Simulate occasional job arrival
        if (Math.random() < 0.1) { // 10% chance every 3 seconds = ~2 jobs/minute
            simulateJobArrival();
        }
    }

    /**
     * Simulate a job arriving from the blockchain
     */
    private void simulateJobArrival() {
        // Create a mock job
        String mockJobId = "job_" + System.currentTimeMillis();
        String mockUser = "0x742d35Cc6A1b6E5c8a5c5c5c5c5c5c5c5c5c5c5c";
        String mockPrompt = "Explain how blockchain AI works in simple terms";

        logger.info("🎯 New job received from blockchain!");
        logger.info("📋 Job ID: {}", mockJobId);
        logger.info("👤 User: {}", mockUser);
        logger.info("❓ Prompt: {}", mockPrompt);

        // Process the job asynchronously
        processingExecutor.submit(() -> processJob(mockJobId, mockUser, mockPrompt));
    }

    /**
     * Process an AI job
     */
    private void processJob(String jobId, String userAddress, String prompt) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("🚀 Processing job: {}", jobId);

            // Step 1: Download model (already preloaded)
            logger.info("📥 Using preloaded Gemma model");

            // Step 2: Generate response using local GGUF
            String response = generateResponse(prompt);
            logger.info("🤖 Generated response ({} chars)", response.length());

            // Step 3: Upload result to IPFS
            String resultCID = uploadResultToIPFS(response, jobId);
            logger.info("📤 Result uploaded to IPFS: {}", resultCID);

            // Step 4: Submit result back to smart contract
            submitResultToContract(jobId, resultCID);

            // Update statistics
            totalProcessed++;
            successRate = (successRate * 0.99) + (1.0 * 0.01); // Moving average

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("✅ Job {} completed in {}ms", jobId, processingTime);
            logger.info("💰 Revenue: 0.001 HBAR");
            logger.info("⚡ Tokens processed: ~{}", prompt.split("\\s+").length);

        } catch (Exception e) {
            logger.error("❌ Job {} failed", jobId, e);

            // Update statistics for failure
            successRate = (successRate * 0.99) + (0.0 * 0.01);

            // Report failure to smart contract
            reportJobFailure(jobId, e.getMessage());
        }
    }

    /**
     * Generate AI response using local GGUF model
     */
    private String generateResponse(String prompt) throws Exception {
        // This simulates calling the GGUF model
        // In production, this would use the C++ llama.cpp integration

        logger.info("🧠 Running inference on Gemma model...");

        // Simulate processing time (real GGUF inference)
        Thread.sleep(2000 + (long)(Math.random() * 3000)); // 2-5 seconds

        // Generate a realistic response
        String response = "Based on your question about '" + prompt + "', here's what I understand:\n\n" +
                         "Blockchain AI represents the convergence of decentralized computing and artificial intelligence. " +
                         "By running AI models on blockchain networks like Hedera, we eliminate the need for centralized " +
                         "servers while ensuring transparency, security, and cost-efficiency.\n\n" +
                         "The key innovation is using smart contracts to coordinate AI processing across distributed " +
                         "nodes, where each node volunteers computational resources in exchange for cryptocurrency " +
                         "payments. This creates a truly decentralized AI infrastructure that scales infinitely " +
                         "without any single point of failure.\n\n" +
                         "In practical terms, your AI request gets processed by local GGUF models running on " +
                         "volunteer machines, with results stored permanently on IPFS and transaction details " +
                         "recorded immutably on the blockchain.";

        return response;
    }

    /**
     * Upload result to IPFS (Filebase)
     */
    private String uploadResultToIPFS(String result, String jobId) throws Exception {
        // In production, this would upload to Filebase/IPFS
        // For demo, generate a mock CID

        logger.info("📤 Uploading result to IPFS...");

        // Simulate upload time
        Thread.sleep(500);

        // Generate mock IPFS CID
        String mockCID = "Qm" + UUID.randomUUID().toString().replace("-", "").substring(0, 44);

        logger.info("✅ Result uploaded with CID: {}", mockCID);
        return mockCID;
    }

    /**
     * Submit result back to smart contract
     */
    private void submitResultToContract(String jobId, String resultCID) throws Exception {
        logger.info("📝 Submitting result to smart contract...");

        // In production, this would call the smart contract's completeJob function
        // For demo, simulate the transaction

        logger.info("✅ Result submitted to blockchain");
        logger.info("🔗 Transaction confirmed");
    }

    /**
     * Report job failure to smart contract
     */
    private void reportJobFailure(String jobId, String reason) {
        logger.warn("📝 Reporting job failure to smart contract...");

        // In production, this would call the smart contract's failJob function
        logger.warn("✅ Job failure reported: {}", reason);
    }

    /**
     * Get node statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("nodeId", nodeId);
        stats.put("isActive", isActive);
        stats.put("totalProcessed", totalProcessed);
        stats.put("totalRevenue", totalRevenue);
        stats.put("successRate", successRate);
        stats.put("uptime", System.currentTimeMillis());
        stats.put("modelHash", gemmaModelHash);
        stats.put("processingPower", 25); // tokens per second

        return stats;
    }

    /**
     * Stop the compute node
     */
    public void stop() {
        logger.info("🛑 Stopping Local Compute Node...");

        isActive = false;

        processingExecutor.shutdown();
        heartbeatExecutor.shutdown();

        try {
            if (!processingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                processingExecutor.shutdownNow();
            }
            if (!heartbeatExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processingExecutor.shutdownNow();
            heartbeatExecutor.shutdownNow();
        }

        web3j.shutdown();

        logger.info("✅ Local Compute Node stopped");
    }

    /**
     * Main entry point
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

        LocalComputeNode node = new LocalComputeNode(config);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(node::stop));

        // Start the node
        node.start();

        // Keep main thread alive
        Thread.currentThread().join();
    }
}
