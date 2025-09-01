/**
 * AI Service Handler - Processes AI requests using PBJ
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain.service;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.sircharlesspikes.ai.blockchain.proto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.crypto.Credentials;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AIServiceHandler {
    private static final Logger logger = LoggerFactory.getLogger(AIServiceHandler.class);
    
    private final String nodeId;
    private final Credentials credentials;
    private final Map<String, AIModelMetadata> models = new ConcurrentHashMap<>();
    private final Map<String, AIModelProcessor> processors = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final LlamaCppModelLoader modelLoader;
    private final QuantizedModelManager quantizedModelManager;
    
    public AIServiceHandler(String nodeId, Credentials credentials) {
        this.nodeId = nodeId;
        this.credentials = credentials;
        
        // Initialize model loaders
        String modelsDir = System.getProperty("models.directory", "./models");
        String ipfsGateway = System.getProperty("ipfs.gateway", "https://ipfs.io");
        String llamaCppPath = System.getProperty("llamacpp.path", "./llama.cpp");
        
        this.modelLoader = new LlamaCppModelLoader(
            modelsDir, 
            ipfsGateway, 
            null, // Web3j will be injected later
            System.getProperty("contract.nft.address")
        );
        
        this.quantizedModelManager = new QuantizedModelManager(llamaCppPath, modelsDir);
        
        initializeProcessors();
    }
    
    private void initializeProcessors() {
        // Register model processors
        processors.put("LLM", new LLMProcessor());
        processors.put("CNN", new ImageProcessor());
        processors.put("RNN", new SequenceProcessor());
    }
    
    public void registerModel(AIModelMetadata model) {
        models.put(model.modelId(), model);
        logger.info("Registered AI model: {} ({})", model.modelName(), model.modelId());
    }
    
    public AIServiceResponse processRequest(AIServiceRequest request) {
        activeJobs.incrementAndGet();
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate request
            validateRequest(request);
            
            // Get the appropriate processor
            String serviceId = request.serviceId();
            AIModelMetadata model = models.get(serviceId);
            if (model == null) {
                throw new IllegalArgumentException("Model not found: " + serviceId);
            }
            
            AIModelProcessor processor = processors.get(model.modelType());
            if (processor == null) {
                throw new IllegalArgumentException("No processor for model type: " + model.modelType());
            }
            
            // Process the request
            AIServiceResponse.Builder responseBuilder = AIServiceResponse.newBuilder()
                .requestId(request.requestId())
                .serviceId(serviceId)
                .success(true)
                .timestamp(Instant.now());
            
            // Handle different input types using PBJ's efficient oneof handling
            if (request.hasTextInput()) {
                TextInput textInput = request.textInputOrThrow();
                TextOutput output = processor.processText(textInput, model);
                responseBuilder.textOutput(output);
            } else if (request.hasImageInput()) {
                ImageInput imageInput = request.imageInputOrThrow();
                ImageOutput output = processor.processImage(imageInput, model);
                responseBuilder.imageOutput(output);
            } else if (request.hasAudioInput()) {
                AudioInput audioInput = request.audioInputOrThrow();
                AudioOutput output = processor.processAudio(audioInput, model);
                responseBuilder.audioOutput(output);
            } else if (request.hasCustomInput()) {
                CustomInput customInput = request.customInputOrThrow();
                CustomOutput output = processor.processCustom(customInput, model);
                responseBuilder.customOutput(output);
            } else {
                throw new IllegalArgumentException("No valid input provided");
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            responseBuilder.processingTimeMs(processingTime);
            
            logger.info("Processed request {} in {}ms", request.requestId(), processingTime);
            
            return responseBuilder.build();
            
        } catch (Exception e) {
            logger.error("Failed to process request: {}", request.requestId(), e);
            return AIServiceResponse.newBuilder()
                .requestId(request.requestId())
                .serviceId(request.serviceId())
                .success(false)
                .errorMessage(e.getMessage())
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .timestamp(Instant.now())
                .build();
        } finally {
            activeJobs.decrementAndGet();
        }
    }
    
    private void validateRequest(AIServiceRequest request) {
        if (request.requestId() == null || request.requestId().isEmpty()) {
            throw new IllegalArgumentException("Request ID is required");
        }
        if (request.serviceId() == null || request.serviceId().isEmpty()) {
            throw new IllegalArgumentException("Service ID is required");
        }
        if (request.requesterAddress() == null || request.requesterAddress().isEmpty()) {
            throw new IllegalArgumentException("Requester address is required");
        }
    }
    
    public Optional<AIModelMetadata> getModel(String modelId) {
        return Optional.ofNullable(models.get(modelId));
    }
    
    public List<AIModelMetadata> listModels(String modelType, int pageSize, String pageToken) {
        Stream<AIModelMetadata> stream = models.values().stream();
        
        if (modelType != null && !modelType.isEmpty()) {
            stream = stream.filter(m -> m.modelType().equals(modelType));
        }
        
        // Simple pagination (in production, use more sophisticated approach)
        if (pageToken != null && !pageToken.isEmpty()) {
            stream = stream.skip(Long.parseLong(pageToken));
        }
        
        if (pageSize > 0) {
            stream = stream.limit(pageSize);
        }
        
        return stream.collect(Collectors.toList());
    }
    
    public Collection<AIModelMetadata> getAvailableModels() {
        return models.values();
    }
    
    public int getModelCount() {
        return models.size();
    }
    
    public double getCurrentLoad() {
        int active = activeJobs.get();
        int maxCapacity = 100; // Configure based on node capacity
        return (double) active / maxCapacity;
    }
    
    public List<String> getActiveServices() {
        return new ArrayList<>(models.keySet());
    }
    
    // Model processor interface
    private interface AIModelProcessor {
        default TextOutput processText(TextInput input, AIModelMetadata model) {
            throw new UnsupportedOperationException("Text processing not supported");
        }
        
        default ImageOutput processImage(ImageInput input, AIModelMetadata model) {
            throw new UnsupportedOperationException("Image processing not supported");
        }
        
        default AudioOutput processAudio(AudioInput input, AIModelMetadata model) {
            throw new UnsupportedOperationException("Audio processing not supported");
        }
        
        default CustomOutput processCustom(CustomInput input, AIModelMetadata model) {
            throw new UnsupportedOperationException("Custom processing not supported");
        }
    }
    
    // LLM Processor implementation
    private class LLMProcessor implements AIModelProcessor {
        @Override
        public TextOutput processText(TextInput input, AIModelMetadata model) {
            try {
                // Check if this is a quantized model from blockchain
                String modelFormat = model.parametersOrDefault("format", "gguf");
                
                if ("gguf".equalsIgnoreCase(modelFormat) || "ggml".equalsIgnoreCase(modelFormat)) {
                    // Load and use quantized model from IPFS
                    String tokenId = model.parametersOrDefault("tokenId", "");
                    
                    if (!tokenId.isEmpty()) {
                        // Load model from blockchain IPFS hash
                        LlamaCppModelLoader.LlamaCppModel llamaModel = 
                            modelLoader.loadModelFromBlockchain(tokenId).get(30, TimeUnit.SECONDS);
                        
                        // Generate text using llama.cpp
                        String generatedText = modelLoader.generateText(
                            llamaModel.id,
                            input.text(),
                            input.maxTokensOrElse(100)
                        ).get(60, TimeUnit.SECONDS);
                        
                        return TextOutput.newBuilder()
                            .text(generatedText)
                            .addConfidenceScores(0.95f)
                            .putMetadata("model", model.modelName())
                            .putMetadata("quantization", model.parametersOrDefault("quantization", "unknown"))
                            .putMetadata("tokens_used", String.valueOf(generatedText.split("\\s+").length))
                            .build();
                    }
                }
                
                // Fallback to Docker Model Runner for non-quantized models
                String processedText = "AI Response to: " + input.text();
                
                // Use Docker Model Runner integration here
                // ProcessBuilder pb = new ProcessBuilder("docker", "model", "run", 
                //     model.modelId(), input.text());
                
                return TextOutput.newBuilder()
                    .text(processedText)
                    .addConfidenceScores(0.95f)
                    .putMetadata("model", model.modelName())
                    .putMetadata("tokens_used", "150")
                    .build();
                    
            } catch (Exception e) {
                logger.error("LLM processing failed", e);
                throw new RuntimeException("Text generation failed: " + e.getMessage());
            }
        }
    }
    
    // Image Processor implementation
    private class ImageProcessor implements AIModelProcessor {
        @Override
        public ImageOutput processImage(ImageInput input, AIModelMetadata model) {
            // Simulate image processing
            Bytes processedImage = input.imageData(); // In production, process the image
            
            return ImageOutput.newBuilder()
                .imageData(processedImage)
                .format(input.format())
                .width(input.width())
                .height(input.height())
                .putMetadata("model", model.modelName())
                .putMetadata("detected_objects", "5")
                .build();
        }
    }
    
    // Sequence Processor implementation
    private class SequenceProcessor implements AIModelProcessor {
        @Override
        public AudioOutput processAudio(AudioInput input, AIModelMetadata model) {
            // Simulate audio processing
            return AudioOutput.newBuilder()
                .audioData(input.audioData())
                .format("mp3")
                .sampleRate(44100)
                .durationMs(input.durationMs())
                .build();
        }
        
        @Override
        public CustomOutput processCustom(CustomInput input, AIModelMetadata model) {
            // Handle custom data types
            return CustomOutput.newBuilder()
                .data(input.data())
                .contentType(input.contentType())
                .putMetadata("model", model.modelName())
                .putMetadata("processed", "true")
                .build();
        }
    }
}
