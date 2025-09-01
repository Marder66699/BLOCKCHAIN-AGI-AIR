/**
 * Quantized Model Manager - Handles GGUF models with various quantization methods
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

public class QuantizedModelManager {
    private static final Logger logger = LoggerFactory.getLogger(QuantizedModelManager.class);
    
    // Quantization types
    public enum QuantizationType {
        // Legacy quantization methods
        Q4_0("q4_0", 4.0f, "Legacy 4-bit, fastest but lower quality"),
        Q4_1("q4_1", 4.0625f, "Legacy 4-bit with extra constant"),
        Q5_0("q5_0", 5.0f, "Legacy 5-bit"),
        Q5_1("q5_1", 5.0625f, "Legacy 5-bit with extra constant"),
        Q8_0("q8_0", 8.0f, "Legacy 8-bit, near-lossless"),
        
        // K-quantization methods (recommended for most hardware)
        Q2_K("q2_k", 2.5625f, "2-bit K-quant, extreme compression"),
        Q3_K_S("q3_k_s", 2.9375f, "3-bit K-quant small"),
        Q3_K_M("q3_k_m", 3.4375f, "3-bit K-quant medium"),
        Q3_K_L("q3_k_l", 3.5625f, "3-bit K-quant large"),
        Q4_K_S("q4_k_s", 4.25f, "4-bit K-quant small"),
        Q4_K_M("q4_k_m", 4.85f, "4-bit K-quant medium, balanced"),
        Q5_K_S("q5_k_s", 5.21f, "5-bit K-quant small"),
        Q5_K_M("q5_k_m", 5.43f, "5-bit K-quant medium, recommended"),
        Q6_K("q6_k", 6.5625f, "6-bit K-quant, high quality"),
        
        // I-quantization methods (SOTA but may be slower on some hardware)
        IQ1_S("iq1_s", 1.6f, "1-bit I-quant, experimental"),
        IQ2_XXS("iq2_xxs", 2.0625f, "2-bit I-quant extra extra small"),
        IQ2_XS("iq2_xs", 2.3125f, "2-bit I-quant extra small"),
        IQ2_S("iq2_s", 2.5f, "2-bit I-quant small"),
        IQ3_XS("iq3_xs", 3.0625f, "3-bit I-quant extra small"),
        IQ3_S("iq3_s", 3.4375f, "3-bit I-quant small"),
        IQ3_M("iq3_m", 3.7f, "3-bit I-quant medium"),
        IQ4_XS("iq4_xs", 4.25f, "4-bit I-quant extra small"),
        
        // Full precision
        F16("f16", 16.0f, "16-bit floating point"),
        F32("f32", 32.0f, "32-bit floating point");
        
        private final String code;
        private final float bitsPerWeight;
        private final String description;
        
        QuantizationType(String code, float bitsPerWeight, String description) {
            this.code = code;
            this.bitsPerWeight = bitsPerWeight;
            this.description = description;
        }
        
        public String getCode() { return code; }
        public float getBitsPerWeight() { return bitsPerWeight; }
        public String getDescription() { return description; }
    }
    
    // Model metadata for GGUF format
    public static class GGUFModelInfo {
        public String modelName;
        public String baseModel;
        public QuantizationType quantization;
        public long contextSize;
        public long parameterCount;
        public long fileSize;
        public boolean hasImportanceMatrix;
        public int gpuLayers;
        public Map<String, String> metadata;
        
        public GGUFModelInfo() {
            this.metadata = new HashMap<>();
        }
        
        public float getCompressionRatio() {
            // Estimate based on original F16 size
            float originalBits = 16.0f;
            return originalBits / quantization.getBitsPerWeight();
        }
        
        public long getEstimatedMemoryUsage() {
            // Rough estimate of memory usage in bytes
            return (long) (parameterCount * quantization.getBitsPerWeight() / 8.0f);
        }
    }
    
    private final String llamaCppPath;
    private final String modelsDirectory;
    private final ExecutorService executor;
    private final Map<String, GGUFModelInfo> modelRegistry;
    
    public QuantizedModelManager(String llamaCppPath, String modelsDirectory) {
        this.llamaCppPath = llamaCppPath;
        this.modelsDirectory = modelsDirectory;
        this.executor = Executors.newCachedThreadPool();
        this.modelRegistry = new ConcurrentHashMap<>();
        
        // Ensure directories exist
        try {
            Files.createDirectories(Paths.get(modelsDirectory));
        } catch (IOException e) {
            logger.error("Failed to create models directory", e);
        }
    }
    
    /**
     * Quantize a model using llama.cpp quantization
     * @param inputModel Path to input model (GGUF F16/F32)
     * @param outputName Output model name
     * @param quantType Quantization type
     * @param useImportanceMatrix Whether to use importance matrix
     * @return Path to quantized model
     */
    public CompletableFuture<Path> quantizeModel(
            Path inputModel,
            String outputName,
            QuantizationType quantType,
            boolean useImportanceMatrix) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path outputPath = Paths.get(modelsDirectory, outputName + "-" + quantType.getCode() + ".gguf");
                
                List<String> command = new ArrayList<>();
                command.add(llamaCppPath + "/quantize");
                command.add(inputModel.toString());
                command.add(outputPath.toString());
                command.add(quantType.getCode());
                
                if (useImportanceMatrix) {
                    // Generate importance matrix if needed
                    Path imatrixPath = generateImportanceMatrix(inputModel);
                    command.add("--imatrix");
                    command.add(imatrixPath.toString());
                }
                
                logger.info("Starting quantization: {} -> {} ({})", 
                    inputModel.getFileName(), outputName, quantType.getCode());
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                // Monitor progress
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.debug("Quantize: {}", line);
                    }
                }
                
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("Quantization failed with exit code: " + exitCode);
                }
                
                // Analyze quantized model
                GGUFModelInfo info = analyzeGGUFModel(outputPath);
                info.quantization = quantType;
                info.hasImportanceMatrix = useImportanceMatrix;
                modelRegistry.put(outputName, info);
                
                logger.info("Quantization complete: {} ({:.2f}x compression)", 
                    outputPath.getFileName(), info.getCompressionRatio());
                
                return outputPath;
                
            } catch (Exception e) {
                logger.error("Quantization failed", e);
                throw new RuntimeException("Quantization failed", e);
            }
        }, executor);
    }
    
    /**
     * Generate importance matrix for better quantization
     * @param modelPath Path to model
     * @return Path to importance matrix file
     */
    private Path generateImportanceMatrix(Path modelPath) throws IOException, InterruptedException {
        Path imatrixPath = Paths.get(modelsDirectory, "imatrix.dat");
        Path calibrationData = getCalibrationDataset();
        
        ProcessBuilder pb = new ProcessBuilder(
            llamaCppPath + "/imatrix",
            "-m", modelPath.toString(),
            "-f", calibrationData.toString(),
            "-o", imatrixPath.toString(),
            "--chunks", "100"
        );
        
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            throw new RuntimeException("Importance matrix generation failed");
        }
        
        return imatrixPath;
    }
    
    /**
     * Get or create calibration dataset for importance matrix
     */
    private Path getCalibrationDataset() throws IOException {
        Path calibrationPath = Paths.get(modelsDirectory, "calibration.txt");
        
        if (!Files.exists(calibrationPath)) {
            // Create a default calibration dataset
            String calibrationText = """
                The following is a diverse set of texts for model calibration:
                
                1. Technical documentation about artificial intelligence and machine learning.
                2. Natural conversation between humans discussing various topics.
                3. Code examples in multiple programming languages.
                4. Scientific papers and research abstracts.
                5. Creative writing including stories and poems.
                6. News articles covering current events.
                7. Educational content for different subjects.
                8. Business and financial reports.
                """;
            
            Files.writeString(calibrationPath, calibrationText);
        }
        
        return calibrationPath;
    }
    
    /**
     * Analyze a GGUF model file to extract metadata
     * @param modelPath Path to GGUF model
     * @return Model information
     */
    public GGUFModelInfo analyzeGGUFModel(Path modelPath) throws IOException {
        GGUFModelInfo info = new GGUFModelInfo();
        
        // Use llama.cpp to get model info
        try {
            ProcessBuilder pb = new ProcessBuilder(
                llamaCppPath + "/main",
                "-m", modelPath.toString(),
                "--print-model-size"
            );
            
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Parse model information from output
                    if (line.contains("model size")) {
                        info.parameterCount = parseParameterCount(line);
                    } else if (line.contains("context size")) {
                        info.contextSize = parseContextSize(line);
                    }
                }
            }
            
            process.destroyForcibly();
            
        } catch (Exception e) {
            logger.warn("Failed to analyze model with llama.cpp", e);
        }
        
        // Get file size
        info.fileSize = Files.size(modelPath);
        info.modelName = modelPath.getFileName().toString();
        
        return info;
    }
    
    /**
     * Get optimal quantization based on hardware capabilities
     * @param modelSize Model size in parameters
     * @param availableVRAM Available VRAM in bytes
     * @param preferQuality Whether to prefer quality over speed
     * @return Recommended quantization type
     */
    public QuantizationType recommendQuantization(
            long modelSize, 
            long availableVRAM, 
            boolean preferQuality) {
        
        // Calculate approximate memory needed for different quants
        long memoryNeeded4bit = (modelSize * 4) / 8;
        long memoryNeeded5bit = (modelSize * 5) / 8;
        long memoryNeeded6bit = (modelSize * 6) / 8;
        long memoryNeeded8bit = (modelSize * 8) / 8;
        
        // Check hardware capabilities
        boolean hasModernGPU = checkModernGPU();
        boolean hasHighCPU = checkHighCPU();
        boolean isMemoryConstrained = availableVRAM < (8L * 1024 * 1024 * 1024); // Less than 8GB
        
        // For highest accuracy needs with sufficient memory
        if (preferQuality && availableVRAM > memoryNeeded8bit * 1.2) {
            return QuantizationType.Q8_0; // Best quality, ~290MB for small models
        }
        
        // For lightweight or mobile use
        if (isMemoryConstrained || !hasModernGPU) {
            return QuantizationType.Q4_0; // ~240MB, fastest inference
        }
        
        // Balanced recommendations
        if (availableVRAM > memoryNeeded6bit && preferQuality) {
            return QuantizationType.Q6_K;
        } else if (availableVRAM > memoryNeeded5bit) {
            return preferQuality ? QuantizationType.Q5_K_M : QuantizationType.Q5_K_S;
        } else if (availableVRAM > memoryNeeded4bit) {
            if (hasModernGPU && hasHighCPU) {
                // Modern hardware can handle I-quants efficiently
                return QuantizationType.IQ4_XS;
            } else {
                // K-quants are safer for older hardware
                return QuantizationType.Q4_K_M;
            }
        } else {
            // Need extreme compression
            if (hasModernGPU && hasHighCPU) {
                return QuantizationType.IQ3_S;
            } else {
                return QuantizationType.Q3_K_M;
            }
        }
    }
    
    /**
     * Compare quantization methods for a specific use case
     * @param modelSize Model size in parameters
     * @param useCase Use case type (mobile, edge, cloud, research)
     * @return Comparison of suitable quantization methods
     */
    public Map<QuantizationType, QuantizationComparison> compareQuantizations(
            long modelSize, String useCase) {
        
        Map<QuantizationType, QuantizationComparison> comparisons = new HashMap<>();
        
        // Q4_0 - Best for lightweight/mobile use
        QuantizationComparison q4_0 = new QuantizationComparison();
        q4_0.modelSize = (modelSize * 4) / 8 / 1024 / 1024; // MB
        q4_0.memoryUsage = "Lower, suited for limited RAM";
        q4_0.inferenceSpeed = "Faster, lighter on CPU";
        q4_0.outputQuality = "High (QAT helps)";
        q4_0.bestFor = "Lightweight or mobile use";
        comparisons.put(QuantizationType.Q4_0, q4_0);
        
        // Q8_0 - Best for highest accuracy needs
        QuantizationComparison q8_0 = new QuantizationComparison();
        q8_0.modelSize = (modelSize * 8) / 8 / 1024 / 1024; // MB
        q8_0.memoryUsage = "Higher, needs more memory";
        q8_0.inferenceSpeed = "Slightly slower, heavier";
        q8_0.outputQuality = "Slightly better detail";
        q8_0.bestFor = "Highest accuracy needs";
        comparisons.put(QuantizationType.Q8_0, q8_0);
        
        // Q4_K_M - Balanced choice
        QuantizationComparison q4_k_m = new QuantizationComparison();
        q4_k_m.modelSize = (long)(modelSize * 4.85) / 8 / 1024 / 1024; // MB
        q4_k_m.memoryUsage = "Moderate, good balance";
        q4_k_m.inferenceSpeed = "Good on modern hardware";
        q4_k_m.outputQuality = "Better than Q4_0";
        q4_k_m.bestFor = "General purpose, balanced";
        comparisons.put(QuantizationType.Q4_K_M, q4_k_m);
        
        return comparisons;
    }
    
    /**
     * Quantization comparison data structure
     */
    public static class QuantizationComparison {
        public long modelSize; // in MB
        public String memoryUsage;
        public String inferenceSpeed;
        public String outputQuality;
        public String bestFor;
    }
    
    /**
     * Calculate optimal GPU layer offloading
     * @param modelInfo Model information
     * @param availableVRAM Available VRAM in bytes
     * @return Number of layers to offload to GPU
     */
    public int calculateOptimalGPULayers(GGUFModelInfo modelInfo, long availableVRAM) {
        // Reserve some VRAM for context and operations
        long usableVRAM = (long) (availableVRAM * 0.9);
        
        // Estimate memory per layer
        long totalModelMemory = modelInfo.getEstimatedMemoryUsage();
        int estimatedLayers = 32; // Default assumption, varies by model
        long memoryPerLayer = totalModelMemory / estimatedLayers;
        
        // Calculate how many layers fit in VRAM
        int gpuLayers = (int) (usableVRAM / memoryPerLayer);
        
        // Cap at total layers
        return Math.min(gpuLayers, estimatedLayers);
    }
    
    private boolean checkModernGPU() {
        // Check for modern GPU capabilities
        // This is simplified - in production, query actual GPU
        return true;
    }
    
    private boolean checkHighCPU() {
        // Check CPU capabilities
        int cores = Runtime.getRuntime().availableProcessors();
        return cores >= 8;
    }
    
    private long parseParameterCount(String line) {
        // Parse parameter count from llama.cpp output
        // Example: "model size: 7B parameters"
        try {
            String[] parts = line.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i + 1].startsWith("B") || parts[i + 1].startsWith("M")) {
                    String num = parts[i].replaceAll("[^0-9.]", "");
                    float value = Float.parseFloat(num);
                    if (parts[i + 1].startsWith("B")) {
                        return (long) (value * 1_000_000_000);
                    } else if (parts[i + 1].startsWith("M")) {
                        return (long) (value * 1_000_000);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse parameter count", e);
        }
        return 0;
    }
    
    private long parseContextSize(String line) {
        // Parse context size from output
        try {
            String[] parts = line.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d+")) {
                    return Long.parseLong(part);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse context size", e);
        }
        return 4096; // Default
    }
    
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
