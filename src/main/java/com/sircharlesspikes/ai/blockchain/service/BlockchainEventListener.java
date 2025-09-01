/**
 * Blockchain Event Listener - Listens for smart contract events and triggers local processing
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;

public class BlockchainEventListener {
    private static final Logger logger = LoggerFactory.getLogger(BlockchainEventListener.class);

    private final Web3j web3j;
    private final Credentials credentials;
    private final String contractAddress;
    private final LocalGGUFRunner ggufRunner;
    private final ExecutorService eventExecutor;
    private final ScheduledExecutorService pollExecutor;
    private volatile boolean running = false;

    // Event definitions
    private static final Event PROCESSING_REQUESTED_EVENT = new Event(
        "ProcessingRequested",
        Arrays.asList(
            new TypeReference<Uint256>(true) {}, // requestId
            new TypeReference<Address>(true) {}, // requester
            new TypeReference<Utf8String>(true) {}, // ipfsHash
            new TypeReference<Uint256>() {} // fee
        )
    );

    private static final Event PROCESSING_COMPLETED_EVENT = new Event(
        "ProcessingCompleted",
        Arrays.asList(
            new TypeReference<Uint256>(true) {}, // requestId
            new TypeReference<Address>(true) {}, // processor
            new TypeReference<Utf8String>() {} // result
        )
    );

    public BlockchainEventListener(Properties config, LocalGGUFRunner ggufRunner) {
        this.contractAddress = config.getProperty("contract.gguf.address");
        this.ggufRunner = ggufRunner;

        // Initialize Web3
        String rpcUrl = config.getProperty("ethereum.rpc.url", "http://localhost:8545");
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        String privateKey = config.getProperty("ethereum.private.key");
        this.credentials = Credentials.create(privateKey);

        this.eventExecutor = Executors.newCachedThreadPool();
        this.pollExecutor = Executors.newScheduledThreadPool(2);

        logger.info("Blockchain Event Listener initialized for contract: {}", contractAddress);
    }

    /**
     * Start listening for blockchain events
     */
    public void startListening() {
        if (running) {
            logger.warn("Event listener is already running");
            return;
        }

        running = true;
        logger.info("Starting blockchain event listener...");

        // Start event subscription (if supported by RPC)
        try {
            startEventSubscription();
        } catch (Exception e) {
            logger.warn("Event subscription failed, falling back to polling: {}", e.getMessage());
            startPollingMode();
        }
    }

    /**
     * Start WebSocket/real-time event subscription
     */
    private void startEventSubscription() throws Exception {
        // Create filter for ProcessingRequested events
        EthFilter filter = new EthFilter(
            DefaultBlockParameterName.LATEST,
            DefaultBlockParameterName.LATEST,
            contractAddress
        );

        // Add event topics
        String processingRequestedTopic = EventEncoder.encode(PROCESSING_REQUESTED_EVENT);
        filter.addSingleTopic(processingRequestedTopic);

        // Subscribe to events
        web3j.ethLogFlowable(filter).subscribe(
            this::handleEvent,
            error -> logger.error("Event subscription error", error),
            () -> logger.info("Event subscription completed")
        );

        logger.info("Event subscription started for ProcessingRequested events");
    }

    /**
     * Fallback polling mode for RPC endpoints that don't support subscriptions
     */
    private void startPollingMode() {
        logger.info("Starting polling mode for blockchain events...");

        pollExecutor.scheduleAtFixedRate(() -> {
            try {
                pollForEvents();
            } catch (Exception e) {
                logger.error("Error polling for events", e);
            }
        }, 0, 10, TimeUnit.SECONDS); // Poll every 10 seconds
    }

    /**
     * Poll for new events
     */
    private void pollForEvents() throws Exception {
        // Get latest block
        BigInteger latestBlock = web3j.ethBlockNumber().send().getBlockNumber();

        // Create filter for recent blocks (last 10 blocks)
        BigInteger fromBlock = latestBlock.subtract(BigInteger.TEN);
        if (fromBlock.compareTo(BigInteger.ZERO) < 0) {
            fromBlock = BigInteger.ZERO;
        }

        EthFilter filter = new EthFilter(
            DefaultBlockParameterName.valueOf(fromBlock),
            DefaultBlockParameterName.valueOf(latestBlock),
            contractAddress
        );

        // Add event topics
        String processingRequestedTopic = EventEncoder.encode(PROCESSING_REQUESTED_EVENT);
        filter.addSingleTopic(processingRequestedTopic);

        // Query for events
        EthLog ethLog = web3j.ethGetLogs(filter).send();
        List<EthLog.LogResult> logs = ethLog.getLogs();

        for (EthLog.LogResult logResult : logs) {
            Log log = (Log) logResult.get();
            handleEvent(log);
        }
    }

    /**
     * Handle blockchain event
     */
    private void handleEvent(Log log) {
        eventExecutor.submit(() -> {
            try {
                processEvent(log);
            } catch (Exception e) {
                logger.error("Error processing event", e);
            }
        });
    }

    /**
     * Process blockchain event
     */
    private void processEvent(Log log) throws Exception {
        String eventSignature = log.getTopics().get(0);

        // Check which event type
        if (EventEncoder.encode(PROCESSING_REQUESTED_EVENT).equals(eventSignature)) {
            handleProcessingRequestedEvent(log);
        } else if (EventEncoder.encode(PROCESSING_COMPLETED_EVENT).equals(eventSignature)) {
            handleProcessingCompletedEvent(log);
        } else {
            logger.debug("Unknown event type: {}", eventSignature);
        }
    }

    /**
     * Handle ProcessingRequested event
     */
    private void handleProcessingRequestedEvent(Log log) throws Exception {
        logger.info("ProcessingRequested event received: {}", log.getTransactionHash());

        // Parse event data
        List<String> topics = log.getTopics();
        List<String> data = Arrays.asList(log.getData().substring(2).split("(?<=\\G.{64})"));

        if (topics.size() >= 4 && data.size() >= 4) {
            String requestId = topics.get(1).substring(2); // Remove 0x prefix
            String requester = "0x" + topics.get(2).substring(26); // Address from topic
            String ipfsHash = "0x" + topics.get(3).substring(2); // IPFS hash from topic

            BigInteger requestIdInt = new BigInteger(requestId, 16);

            logger.info("New processing request: ID={}, Requester={}, IPFS={}",
                requestIdInt, requester, ipfsHash);

            // Check if we can process this request
            if (canProcessRequest(ipfsHash)) {
                // Accept and process the request
                processBlockchainRequest(requestIdInt, ipfsHash, requester);
            } else {
                logger.info("Cannot process request - model not supported or node busy");
            }
        }
    }

    /**
     * Handle ProcessingCompleted event
     */
    private void handleProcessingCompletedEvent(Log log) {
        logger.info("ProcessingCompleted event received: {}", log.getTransactionHash());
        // Handle completion event if needed
    }

    /**
     * Check if we can process a request
     */
    private boolean canProcessRequest(String ipfsHash) {
        // Check if we support the model and have capacity
        // This would integrate with your LocalGGUFRunner
        return ggufRunner != null && ggufRunner.canProcessModel(ipfsHash);
    }

    /**
     * Process a blockchain request
     */
    private void processBlockchainRequest(BigInteger requestId, String ipfsHash, String requester) {
        try {
            logger.info("Processing blockchain request: {}", requestId);

            // Create processing task
            LocalGGUFRunner.ProcessingTask task = new LocalGGUFRunner.ProcessingTask(
                requestId.toString(),
                ipfsHash,
                requester,
                "blockchain-api-" + requester,
                getRequestPrompt(requestId) // This would need to be fetched from contract
            );

            // Start processing
            ggufRunner.processTask(task);

        } catch (Exception e) {
            logger.error("Failed to process blockchain request: {}", requestId, e);
        }
    }

    /**
     * Get request prompt from smart contract (simplified)
     */
    private String getRequestPrompt(BigInteger requestId) {
        // This would call the smart contract to get the prompt
        // For demo purposes, return a generic prompt
        return "Process this AI request from blockchain ID: " + requestId;
    }

    /**
     * Register this node with the smart contract
     */
    public CompletableFuture<String> registerNode(String nodeId, List<String> supportedModels) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Registering node with smart contract...");

                // This would call the smart contract registerNode function
                // For now, return a mock transaction hash
                String txHash = "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 64);

                logger.info("Node registered successfully. TX: {}", txHash);
                return txHash;

            } catch (Exception e) {
                logger.error("Failed to register node", e);
                throw new RuntimeException("Node registration failed", e);
            }
        });
    }

    /**
     * Submit processing result back to blockchain
     */
    public CompletableFuture<String> submitResult(String requestId, String result) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Submitting result to blockchain for request: {}", requestId);

                // This would call the smart contract completeProcessing function
                // For now, return a mock transaction hash
                String txHash = "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 64);

                logger.info("Result submitted successfully. TX: {}", txHash);
                return txHash;

            } catch (Exception e) {
                logger.error("Failed to submit result", e);
                throw new RuntimeException("Result submission failed", e);
            }
        });
    }

    /**
     * Get current status
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", running);
        status.put("contractAddress", contractAddress);
        status.put("nodeAddress", credentials.getAddress());

        return status;
    }

    /**
     * Stop listening for events
     */
    public void stopListening() {
        logger.info("Stopping blockchain event listener...");
        running = false;

        eventExecutor.shutdown();
        pollExecutor.shutdown();

        try {
            if (!eventExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                eventExecutor.shutdownNow();
            }
            if (!pollExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                pollExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            eventExecutor.shutdownNow();
            pollExecutor.shutdownNow();
        }

        web3j.shutdown();
        logger.info("Blockchain event listener stopped");
    }

    // Helper method to add to LocalGGUFRunner
    private boolean canProcessModel(String ipfsHash) {
        // This would check if the model is supported by the local runner
        return true; // Simplified for demo
    }
}
