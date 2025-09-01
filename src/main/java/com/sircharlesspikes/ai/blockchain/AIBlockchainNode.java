/**
 * AI Blockchain Node - Main Application
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.sircharlesspikes.ai.blockchain.proto.*;
import com.sircharlesspikes.ai.blockchain.service.AIServiceHandler;
import com.sircharlesspikes.ai.blockchain.service.NodeCommunicationService;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.grpc.GrpcRouting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AIBlockchainNode {
    private static final Logger logger = LoggerFactory.getLogger(AIBlockchainNode.class);
    
    private final String nodeId;
    private final Web3j web3j;
    private final Credentials credentials;
    private final AIServiceHandler serviceHandler;
    private final NodeCommunicationService nodeComm;
    private final ScheduledExecutorService scheduler;
    private WebServer webServer;
    
    public AIBlockchainNode(Properties config) throws IOException {
        logger.info("=================================================");
        logger.info("AI ON BLOCKCHAIN NODE - POWERED BY PBJ");
        logger.info("Author: Sir Charles Spikes");
        logger.info("Cincinnati, Ohio");
        logger.info("Contact: SirCharlesspikes5@gmail.com");
        logger.info("Telegram: @SirGODSATANAGI");
        logger.info("=================================================\n");
        
        // Initialize node ID
        this.nodeId = config.getProperty("node.id", generateNodeId());
        logger.info("Node ID: {}", nodeId);
        
        // Initialize Web3
        String rpcUrl = config.getProperty("ethereum.rpc.url", "http://localhost:8545");
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        
        // Load credentials
        String privateKey = config.getProperty("ethereum.private.key");
        if (privateKey == null || privateKey.isEmpty()) {
            throw new IllegalArgumentException("Private key not configured");
        }
        this.credentials = Credentials.create(privateKey);
        logger.info("Node address: {}", credentials.getAddress());
        
        // Initialize services
        this.serviceHandler = new AIServiceHandler(nodeId, credentials);
        this.nodeComm = new NodeCommunicationService(nodeId);
        this.scheduler = Executors.newScheduledThreadPool(4);
        
        // Load available AI models
        loadAvailableModels(config);
    }
    
    public void start() throws Exception {
        logger.info("Starting AI Blockchain Node...");
        
        // Build GRPC service
        GrpcRouting grpcRouting = GrpcRouting.builder()
            .service(new AIBlockchainServiceImpl())
            .build();
        
        // Create web server
        int port = Integer.parseInt(System.getProperty("server.port", "8811"));
        webServer = WebServer.builder()
            .port(port)
            .addRouting(grpcRouting)
            .build();
        
        // Start server
        webServer.start()
            .thenAccept(ws -> {
                logger.info("AI Blockchain Node started on port: {}", ws.port());
                logger.info("GRPC endpoint: localhost:{}", ws.port());
            })
            .exceptionally(t -> {
                logger.error("Failed to start server", t);
                return null;
            });
        
        // Start heartbeat
        startHeartbeat();
        
        // Announce service availability
        announceServices();
    }
    
    public void stop() {
        logger.info("Stopping AI Blockchain Node...");
        
        if (webServer != null) {
            webServer.shutdown()
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS)
                .join();
        }
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        logger.info("AI Blockchain Node stopped");
    }
    
    private void loadAvailableModels(Properties config) {
        logger.info("Loading available AI models...");
        
        // Example model registration
        AIModelMetadata gptModel = AIModelMetadata.newBuilder()
            .modelId("gpt-4-turbo")
            .modelName("GPT-4 Turbo")
            .modelType("LLM")
            .version("1.0")
            .ipfsHash("QmExampleGPT4TurboHash")
            .sizeBytes(10_000_000_000L)
            .creatorAddress(credentials.getAddress())
            .putParameters("max_tokens", "128000")
            .putParameters("temperature", "0.7")
            .addSupportedTasks("text-generation")
            .addSupportedTasks("chat")
            .addSupportedTasks("code-generation")
            .build();
        
        serviceHandler.registerModel(gptModel);
        
        AIModelMetadata visionModel = AIModelMetadata.newBuilder()
            .modelId("vision-transformer-v2")
            .modelName("Vision Transformer V2")
            .modelType("CNN")
            .version("2.0")
            .ipfsHash("QmExampleVisionHash")
            .sizeBytes(5_000_000_000L)
            .creatorAddress(credentials.getAddress())
            .putParameters("input_size", "1024x1024")
            .putParameters("output_classes", "1000")
            .addSupportedTasks("image-classification")
            .addSupportedTasks("object-detection")
            .build();
        
        serviceHandler.registerModel(visionModel);
        
        logger.info("Loaded {} AI models", serviceHandler.getModelCount());
    }
    
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                HeartBeat heartbeat = HeartBeat.newBuilder()
                    .putStatus("state", "active")
                    .putStatus("load", String.valueOf(serviceHandler.getCurrentLoad()))
                    .uptimeSeconds(getUptimeSeconds())
                    .addAllActiveServices(serviceHandler.getActiveServices())
                    .build();
                
                NodeMessage message = NodeMessage.newBuilder()
                    .nodeId(nodeId)
                    .heartbeat(heartbeat)
                    .signature(generateSignature(heartbeat))
                    .build();
                
                nodeComm.broadcast(message);
            } catch (Exception e) {
                logger.error("Failed to send heartbeat", e);
            }
        }, 0, 30, TimeUnit.SECONDS);
    }
    
    private void announceServices() {
        ServiceAnnouncement announcement = ServiceAnnouncement.newBuilder()
            .addAllAvailableModels(serviceHandler.getAvailableModels())
            .putNodeCapabilities("compute", "GPU")
            .putNodeCapabilities("memory", "64GB")
            .putNodeCapabilities("storage", "10TB")
            .endpointUrl("grpc://localhost:" + webServer.port())
            .build();
        
        NodeMessage message = NodeMessage.newBuilder()
            .nodeId(nodeId)
            .serviceAnnouncement(announcement)
            .signature(generateSignature(announcement))
            .build();
        
        nodeComm.broadcast(message);
        logger.info("Announced {} services to network", serviceHandler.getModelCount());
    }
    
    private String generateNodeId() {
        return "node-" + System.currentTimeMillis();
    }
    
    private long getUptimeSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
    }
    
    private Bytes generateSignature(Object data) {
        // TODO: Implement proper signature generation
        return Bytes.wrap(new byte[64]);
    }
    
    // GRPC Service Implementation
    private class AIBlockchainServiceImpl implements AIBlockchainService {
        
        @Override
        public AIServiceResponse submitRequest(AIServiceRequest request) {
            logger.info("Received AI service request: {}", request.requestId());
            
            try {
                // Process the request
                AIServiceResponse response = serviceHandler.processRequest(request);
                
                // Record transaction on blockchain
                recordTransaction(request, response);
                
                return response;
            } catch (Exception e) {
                logger.error("Failed to process request", e);
                return AIServiceResponse.newBuilder()
                    .requestId(request.requestId())
                    .serviceId(request.serviceId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
            }
        }
        
        @Override
        public void streamRequests(
                io.helidon.common.reactive.Multi<AIServiceRequest> requests,
                io.helidon.common.reactive.StreamEmitter<AIServiceResponse> responses) {
            requests.forEach(request -> {
                AIServiceResponse response = submitRequest(request);
                responses.emit(response);
            });
        }
        
        @Override
        public AIModelMetadata getModelMetadata(ModelQuery query) {
            return serviceHandler.getModel(query.modelId())
                .orElseThrow(() -> new RuntimeException("Model not found: " + query.modelId()));
        }
        
        @Override
        public ListModelsResponse listModels(ListModelsRequest request) {
            return ListModelsResponse.newBuilder()
                .addAllModels(serviceHandler.listModels(
                    request.modelType(),
                    request.pageSize(),
                    request.pageToken()))
                .build();
        }
        
        @Override
        public NodeMessageResponse sendNodeMessage(NodeMessage message) {
            try {
                nodeComm.handleMessage(message);
                return NodeMessageResponse.newBuilder()
                    .success(true)
                    .message("Message processed")
                    .build();
            } catch (Exception e) {
                return NodeMessageResponse.newBuilder()
                    .success(false)
                    .message(e.getMessage())
                    .build();
            }
        }
        
        @Override
        public void subscribeToNodes(
                SubscribeRequest request,
                io.helidon.common.reactive.StreamEmitter<NodeMessage> emitter) {
            nodeComm.subscribe(request, emitter);
        }
        
        private void recordTransaction(AIServiceRequest request, AIServiceResponse response) {
            // TODO: Implement blockchain transaction recording
            logger.debug("Recording transaction for request: {}", request.requestId());
        }
    }
    
    public static void main(String[] args) throws Exception {
        // Load configuration
        Properties config = new Properties();
        Path configPath = Path.of("config/node.properties");
        if (Files.exists(configPath)) {
            config.load(Files.newBufferedReader(configPath));
        }
        
        // Override with environment variables
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("AI_NODE_")) {
                String propKey = key.substring(8).toLowerCase().replace('_', '.');
                config.setProperty(propKey, value);
            }
        });
        
        // Create and start node
        AIBlockchainNode node = new AIBlockchainNode(config);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(node::stop));
        
        // Start the node
        node.start();
        
        // Keep main thread alive
        Thread.currentThread().join();
    }
}
