/**
 * Llama.cpp Model Loader - Loads and executes transformer models from IPFS
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain.service;

import com.sircharlesspikes.ai.blockchain.proto.AIModelMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.Map;

public class LlamaCppModelLoader {
    private static final Logger logger = LoggerFactory.getLogger(LlamaCppModelLoader.class);
    
    private final String modelsDirectory;
    private final String ipfsGateway;
    private final Web3j web3j;
    private final String modelNFTAddress;
    private final ExecutorService downloadExecutor;
    private final Map<String, LlamaCppModel> loadedModels = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Path>> downloadingModels = new ConcurrentHashMap<>();
    
    // Llama.cpp process management
    private final Map<String, Process> llamaProcesses = new ConcurrentHashMap<>();
    
    public LlamaCppModelLoader(String modelsDirectory, String ipfsGateway, Web3j web3j, String modelNFTAddress) {
        this.modelsDirectory = modelsDirectory;
        this.ipfsGateway = ipfsGateway;
        this.web3j = web3j;
        this.modelNFTAddress = modelNFTAddress;
        this.downloadExecutor = Executors.newFixedThreadPool(4);
        
        // Create models directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(modelsDirectory));
        } catch (IOException e) {
            logger.error("Failed to create models directory", e);
        }
        
        logger.info("LlamaCpp Model Loader initialized");
        logger.info("Models directory: {}", modelsDirectory);
        logger.info("IPFS Gateway: {}", ipfsGateway);
    }
    
    /**
     * Load a model from blockchain-stored IPFS hash
     * @param tokenId The NFT token ID containing the model metadata
     * @return Loaded model instance
     */
    public CompletableFuture<LlamaCppModel> loadModelFromBlockchain(String tokenId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get model metadata from smart contract
                logger.info("Fetching model metadata for token ID: {}", tokenId);
                String ipfsHash = getModelIpfsHashFromContract(tokenId);
                
                if (ipfsHash == null || ipfsHash.isEmpty()) {
                    throw new RuntimeException("No IPFS hash found for token ID: " + tokenId);
                }
                
                // Check if model is already loaded
                LlamaCppModel existingModel = loadedModels.get(ipfsHash);
                if (existingModel != null && existingModel.isLoaded()) {
                    logger.info("Model already loaded: {}", ipfsHash);
                    return existingModel;
                }
                
                // Download model from IPFS if needed
                Path modelPath = downloadModelFromIPFS(ipfsHash).get();
                
                // Load model with llama.cpp
                LlamaCppModel model = loadLlamaCppModel(ipfsHash, modelPath);
                loadedModels.put(ipfsHash, model);
                
                return model;
                
            } catch (Exception e) {
                logger.error("Failed to load model from blockchain", e);
                throw new RuntimeException("Model loading failed", e);
            }
        }, downloadExecutor);
    }
    
    /**
     * Download model from IPFS
     * @param ipfsHash IPFS hash of the model
     * @return Path to downloaded model file
     */
    private CompletableFuture<Path> downloadModelFromIPFS(String ipfsHash) {
        // Check if already downloading
        CompletableFuture<Path> existingDownload = downloadingModels.get(ipfsHash);
        if (existingDownload != null) {
            return existingDownload;
        }
        
        CompletableFuture<Path> downloadFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Path modelPath = Paths.get(modelsDirectory, ipfsHash + ".gguf");
                
                // Check if already downloaded
                if (Files.exists(modelPath)) {
                    logger.info("Model already downloaded: {}", modelPath);
                    return modelPath;
                }
                
                // Download from IPFS
                logger.info("Downloading model from IPFS: {}", ipfsHash);
                String ipfsUrl = ipfsGateway + "/ipfs/" + ipfsHash;
                
                URL url = new URL(ipfsUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(300000); // 5 minutes for large models
                
                long contentLength = connection.getContentLengthLong();
                logger.info("Model size: {} bytes", contentLength);
                
                // Download with progress tracking
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     OutputStream out = new BufferedOutputStream(Files.newOutputStream(modelPath))) {
                    
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
                
                logger.info("Model downloaded successfully: {}", modelPath);
                return modelPath;
                
            } catch (Exception e) {
                logger.error("Failed to download model from IPFS", e);
                throw new RuntimeException("IPFS download failed", e);
            }
        }, downloadExecutor);
        
        downloadingModels.put(ipfsHash, downloadFuture);
        
        // Remove from downloading map when complete
        downloadFuture.whenComplete((path, error) -> {
            downloadingModels.remove(ipfsHash);
        });
        
        return downloadFuture;
    }
    
    /**
     * Load model using llama.cpp
     * @param ipfsHash Model identifier
     * @param modelPath Path to model file
     * @return Loaded model instance
     */
    private LlamaCppModel loadLlamaCppModel(String ipfsHash, Path modelPath) {
        try {
            logger.info("Loading model with llama.cpp: {}", modelPath);
            
            // Prepare llama.cpp command
            ProcessBuilder pb = new ProcessBuilder(
                "llama-cli",  // or path to llama.cpp executable
                "--model", modelPath.toString(),
                "--interactive",
                "--ctx-size", "4096",
                "--n-gpu-layers", "35",  // Offload layers to GPU
                "--threads", "8",
                "--mlock",  // Lock model in memory
                "--no-mmap"  // Disable memory mapping for better control
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            llamaProcesses.put(ipfsHash, process);
            
            // Create model wrapper
            LlamaCppModel model = new LlamaCppModel(
                ipfsHash,
                modelPath,
                process,
                new BufferedWriter(new OutputStreamWriter(process.getOutputStream())),
                new BufferedReader(new InputStreamReader(process.getInputStream()))
            );
            
            // Wait for model to initialize
            model.waitForReady();
            
            logger.info("Llama.cpp model loaded successfully: {}", ipfsHash);
            return model;
            
        } catch (Exception e) {
            logger.error("Failed to load llama.cpp model", e);
            throw new RuntimeException("Llama.cpp loading failed", e);
        }
    }
    
    /**
     * Get IPFS hash from smart contract
     * @param tokenId NFT token ID
     * @return IPFS hash
     */
    private String getModelIpfsHashFromContract(String tokenId) {
        try {
            // Prepare function call to get model details
            String functionCall = "0x" + 
                "abcdef12" + // getModelDetails function selector (replace with actual)
                String.format("%064x", Long.parseLong(tokenId)); // token ID parameter
            
            EthCall ethCall = web3j.ethCall(
                org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                    null,
                    modelNFTAddress,
                    functionCall
                ),
                DefaultBlockParameterName.LATEST
            ).send();
            
            String result = ethCall.getValue();
            
            // Parse IPFS hash from result (this is simplified, actual parsing depends on contract)
            // Assuming IPFS hash is at a specific position in the return data
            if (result != null && result.length() > 130) {
                // Extract IPFS hash (assuming it's a string in the struct)
                String ipfsHashHex = result.substring(130, 194); // Adjust based on actual contract
                return hexToString(ipfsHashHex);
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Failed to get IPFS hash from contract", e);
            return null;
        }
    }
    
    /**
     * Execute inference with a loaded model
     * @param modelId Model identifier (IPFS hash)
     * @param prompt Input prompt
     * @param maxTokens Maximum tokens to generate
     * @return Generated text
     */
    public CompletableFuture<String> generateText(String modelId, String prompt, int maxTokens) {
        return CompletableFuture.supplyAsync(() -> {
            LlamaCppModel model = loadedModels.get(modelId);
            if (model == null || !model.isLoaded()) {
                throw new RuntimeException("Model not loaded: " + modelId);
            }
            
            try {
                return model.generate(prompt, maxTokens);
            } catch (Exception e) {
                logger.error("Generation failed", e);
                throw new RuntimeException("Text generation failed", e);
            }
        });
    }
    
    /**
     * Unload a model from memory
     * @param modelId Model identifier
     */
    public void unloadModel(String modelId) {
        LlamaCppModel model = loadedModels.remove(modelId);
        if (model != null) {
            model.close();
        }
        
        Process process = llamaProcesses.remove(modelId);
        if (process != null) {
            process.destroyForcibly();
        }
    }
    
    /**
     * Shutdown the model loader
     */
    public void shutdown() {
        logger.info("Shutting down LlamaCpp Model Loader");
        
        // Unload all models
        loadedModels.keySet().forEach(this::unloadModel);
        
        // Shutdown executor
        downloadExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
        }
    }
    
    private String hexToString(String hex) {
        if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return new String(bytes).trim();
    }
    
    /**
     * Llama.cpp model wrapper
     */
    public static class LlamaCppModel {
        private final String id;
        private final Path modelPath;
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private volatile boolean loaded = false;
        
        public LlamaCppModel(String id, Path modelPath, Process process, 
                           BufferedWriter writer, BufferedReader reader) {
            this.id = id;
            this.modelPath = modelPath;
            this.process = process;
            this.writer = writer;
            this.reader = reader;
        }
        
        public void waitForReady() throws IOException {
            // Wait for llama.cpp to be ready
            String line;
            while ((line = reader.readLine()) != null) {
                logger.debug("Llama.cpp: {}", line);
                if (line.contains("loaded successfully") || line.contains("ready")) {
                    loaded = true;
                    break;
                }
            }
        }
        
        public String generate(String prompt, int maxTokens) throws IOException {
            if (!loaded) {
                throw new IllegalStateException("Model not loaded");
            }
            
            // Send prompt to llama.cpp
            writer.write(prompt);
            writer.write("\n");
            writer.flush();
            
            // Read response
            StringBuilder response = new StringBuilder();
            String line;
            int tokenCount = 0;
            
            while ((line = reader.readLine()) != null && tokenCount < maxTokens) {
                response.append(line).append("\n");
                tokenCount += line.split("\\s+").length; // Simple token counting
                
                // Check for end of generation markers
                if (line.contains("</s>") || line.isEmpty()) {
                    break;
                }
            }
            
            return response.toString();
        }
        
        public boolean isLoaded() {
            return loaded && process.isAlive();
        }
        
        public void close() {
            try {
                writer.close();
                reader.close();
                process.destroyForcibly();
            } catch (Exception e) {
                logger.error("Error closing model", e);
            }
        }
    }
}
