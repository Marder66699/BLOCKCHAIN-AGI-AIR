/**
 * Edge Coordinator - Manages distributed inference across edge devices
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

package com.sircharlesspikes.ai.blockchain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.io.*;

public class EdgeCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(EdgeCoordinator.class);

    // Device management
    private final Map<String, EdgeDevice> devices = new ConcurrentHashMap<>();
    private final ScheduledExecutorService healthCheckExecutor = Executors.newScheduledThreadPool(2);
    private final ExecutorService inferenceExecutor = Executors.newCachedThreadPool();

    // Model distribution
    private final Map<String, Set<String>> modelToDevices = new ConcurrentHashMap<>();
    private final Map<String, Queue<InferenceTask>> taskQueues = new ConcurrentHashMap<>();

    public EdgeCoordinator() {
        startHealthChecks();
    }

    /**
     * Edge device representation
     */
    public static class EdgeDevice {
        public String deviceId;
        public String ipAddress;
        public int port;
        public DeviceCapabilities capabilities;
        public DeviceStatus status;
        public long lastHeartbeat;
        public double loadFactor;

        public EdgeDevice(String deviceId, String ipAddress, int port) {
            this.deviceId = deviceId;
            this.ipAddress = ipAddress;
            this.port = port;
            this.capabilities = new DeviceCapabilities();
            this.status = DeviceStatus.OFFLINE;
            this.lastHeartbeat = 0;
            this.loadFactor = 0.0;
        }

        public boolean isHealthy() {
            return status == DeviceStatus.ONLINE &&
                   System.currentTimeMillis() - lastHeartbeat < 30000; // 30 seconds
        }

        public double calculateScore(InferenceConfig config) {
            double score = 1.0;

            // Memory score
            long requiredMemory = estimateMemoryUsage(config);
            double memoryScore = Math.min(1.0, (double)capabilities.memoryMB / requiredMemory);
            score *= memoryScore;

            // GPU score
            if (config.nGpuLayers > 0) {
                double gpuScore = Math.min(1.0, (double)capabilities.vramMB /
                    (config.nGpuLayers * 100)); // Rough estimate
                score *= gpuScore;
            }

            // CPU score
            double cpuScore = Math.min(1.0, (double)capabilities.cpuCores / config.nThreads);
            score *= cpuScore;

            // Load factor penalty
            score *= (1.0 - loadFactor);

            return score;
        }

        private long estimateMemoryUsage(InferenceConfig config) {
            // Rough estimation based on context size and model parameters
            return config.nCtx * 2; // 2 bytes per token estimate
        }
    }

    /**
     * Device capabilities
     */
    public static class DeviceCapabilities {
        public int cpuCores;
        public int gpuCores;
        public long memoryMB;
        public long vramMB;
        public String gpuModel;
        public List<String> supportedModels;
        public double performanceScore;

        public DeviceCapabilities() {
            this.supportedModels = new ArrayList<>();
        }
    }

    /**
     * Device status
     */
    public enum DeviceStatus {
        ONLINE,
        OFFLINE,
        MAINTENANCE,
        OVERLOADED
    }

    /**
     * Inference configuration
     */
    public static class InferenceConfig {
        public String modelId;
        public int nThreads = 4;
        public int nCtx = 4096;
        public int nGpuLayers = 35;
        public int nPredict = 256;
        public float temperature = 0.8f;
        public String ipfsHash;
    }

    /**
     * Inference task
     */
    public static class InferenceTask {
        public String taskId;
        public String modelId;
        public String prompt;
        public InferenceConfig config;
        public CompletableFuture<String> result;
        public long submittedAt;
        public String assignedDevice;

        public InferenceTask(String taskId, String modelId, String prompt, InferenceConfig config) {
            this.taskId = taskId;
            this.modelId = modelId;
            this.prompt = prompt;
            this.config = config;
            this.result = new CompletableFuture<>();
            this.submittedAt = System.currentTimeMillis();
        }
    }

    /**
     * Register edge device
     */
    public void registerDevice(String deviceId, String ipAddress, int port, DeviceCapabilities capabilities) {
        EdgeDevice device = new EdgeDevice(deviceId, ipAddress, port);
        device.capabilities = capabilities;
        device.status = DeviceStatus.ONLINE;
        device.lastHeartbeat = System.currentTimeMillis();

        devices.put(deviceId, device);

        // Add device to model support lists
        for (String model : capabilities.supportedModels) {
            modelToDevices.computeIfAbsent(model, k -> new HashSet<>()).add(deviceId);
        }

        logger.info("Registered edge device: {} at {}:{}", deviceId, ipAddress, port);
    }

    /**
     * Unregister edge device
     */
    public void unregisterDevice(String deviceId) {
        EdgeDevice device = devices.remove(deviceId);
        if (device != null) {
            // Remove from model support lists
            for (String model : device.capabilities.supportedModels) {
                Set<String> deviceSet = modelToDevices.get(model);
                if (deviceSet != null) {
                    deviceSet.remove(deviceId);
                }
            }

            // Cancel pending tasks
            Queue<InferenceTask> queue = taskQueues.get(deviceId);
            if (queue != null) {
                for (InferenceTask task : queue) {
                    task.result.completeExceptionally(new RuntimeException("Device offline"));
                }
                queue.clear();
            }
        }

        logger.info("Unregistered edge device: {}", deviceId);
    }

    /**
     * Distribute inference task to optimal edge device
     */
    public CompletableFuture<String> distributeInference(String modelId, String prompt, InferenceConfig config) {
        InferenceTask task = new InferenceTask(
            UUID.randomUUID().toString(),
            modelId,
            prompt,
            config
        );

        // Find optimal device
        String optimalDevice = findOptimalDevice(modelId, config);
        if (optimalDevice == null) {
            task.result.completeExceptionally(new RuntimeException("No suitable device available"));
            return task.result;
        }

        task.assignedDevice = optimalDevice;

        // Queue task for device
        taskQueues.computeIfAbsent(optimalDevice, k -> new LinkedList<>()).add(task);

        // Process task asynchronously
        inferenceExecutor.submit(() -> processTask(task));

        logger.info("Distributed inference task {} to device {}", task.taskId, optimalDevice);

        return task.result;
    }

    /**
     * Find optimal device for model and configuration
     */
    private String findOptimalDevice(String modelId, InferenceConfig config) {
        Set<String> availableDevices = modelToDevices.get(modelId);
        if (availableDevices == null || availableDevices.isEmpty()) {
            return null;
        }

        String bestDevice = null;
        double bestScore = -1.0;

        for (String deviceId : availableDevices) {
            EdgeDevice device = devices.get(deviceId);
            if (device == null || !device.isHealthy()) {
                continue;
            }

            double score = device.calculateScore(config);
            if (score > bestScore) {
                bestScore = score;
                bestDevice = deviceId;
            }
        }

        return bestDevice;
    }

    /**
     * Process inference task on assigned device
     */
    private void processTask(InferenceTask task) {
        EdgeDevice device = devices.get(task.assignedDevice);
        if (device == null || !device.isHealthy()) {
            task.result.completeExceptionally(new RuntimeException("Device not available"));
            return;
        }

        try {
            // Update device load
            device.loadFactor = Math.min(1.0, device.loadFactor + 0.1);

            // Send task to device
            String result = sendTaskToDevice(device, task);

            // Update device load
            device.loadFactor = Math.max(0.0, device.loadFactor - 0.1);

            task.result.complete(result);

            logger.info("Completed inference task {} on device {}", task.taskId, device.deviceId);

        } catch (Exception e) {
            logger.error("Failed to process task {} on device {}", task.taskId, device.deviceId, e);
            task.result.completeExceptionally(e);

            // Reduce device load on failure
            device.loadFactor = Math.max(0.0, device.loadFactor - 0.1);
        }
    }

    /**
     * Send task to edge device via HTTP
     */
    private String sendTaskToDevice(EdgeDevice device, InferenceTask task) throws Exception {
        String url = String.format("http://%s:%d/api/inference", device.ipAddress, device.port);

        // Create JSON payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskId", task.taskId);
        payload.put("modelId", task.modelId);
        payload.put("prompt", task.prompt);
        payload.put("config", Map.of(
            "nThreads", task.config.nThreads,
            "nCtx", task.config.nCtx,
            "nGpuLayers", task.config.nGpuLayers,
            "nPredict", task.config.nPredict,
            "temperature", task.config.temperature,
            "ipfsHash", task.config.ipfsHash
        ));

        // Send HTTP request
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        // Write payload
        try (OutputStream os = con.getOutputStream()) {
            byte[] input = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(payload).getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        // Read response
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return response.toString();
        }
    }

    /**
     * Start health check routine
     */
    private void startHealthChecks() {
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            try {
                performHealthChecks();
            } catch (Exception e) {
                logger.error("Health check failed", e);
            }
        }, 0, 30, TimeUnit.SECONDS); // Every 30 seconds
    }

    /**
     * Perform health checks on all devices
     */
    private void performHealthChecks() {
        for (Map.Entry<String, EdgeDevice> entry : devices.entrySet()) {
            String deviceId = entry.getKey();
            EdgeDevice device = entry.getValue();

            try {
                // Send health check ping
                String healthUrl = String.format("http://%s:%d/api/health",
                    device.ipAddress, device.port);

                URL url = new URL(healthUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(5000);
                con.setReadTimeout(5000);

                int responseCode = con.getResponseCode();
                if (responseCode == 200) {
                    device.status = DeviceStatus.ONLINE;
                    device.lastHeartbeat = System.currentTimeMillis();

                    // Update load factor from response
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream(), "utf-8"))) {
                        String response = br.readLine();
                        // Parse load factor from response
                        if (response.contains("load")) {
                            // Simple parsing - in production, use JSON parsing
                            device.loadFactor = 0.5; // Placeholder
                        }
                    }
                } else {
                    device.status = DeviceStatus.OFFLINE;
                    logger.warn("Device {} health check failed: {}", deviceId, responseCode);
                }

            } catch (Exception e) {
                device.status = DeviceStatus.OFFLINE;
                logger.warn("Device {} unreachable: {}", deviceId, e.getMessage());
            }
        }
    }

    /**
     * Get device statistics
     */
    public Map<String, Object> getDeviceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalDevices", devices.size());

        int onlineDevices = 0;
        double totalLoad = 0.0;

        for (EdgeDevice device : devices.values()) {
            if (device.isHealthy()) {
                onlineDevices++;
                totalLoad += device.loadFactor;
            }
        }

        stats.put("onlineDevices", onlineDevices);
        stats.put("averageLoad", onlineDevices > 0 ? totalLoad / onlineDevices : 0.0);

        List<Map<String, Object>> deviceList = new ArrayList<>();
        for (EdgeDevice device : devices.values()) {
            Map<String, Object> deviceStats = new HashMap<>();
            deviceStats.put("deviceId", device.deviceId);
            deviceStats.put("ipAddress", device.ipAddress);
            deviceStats.put("status", device.status.toString());
            deviceStats.put("loadFactor", device.loadFactor);
            deviceStats.put("lastHeartbeat", device.lastHeartbeat);
            deviceList.add(deviceStats);
        }

        stats.put("devices", deviceList);
        return stats;
    }

    /**
     * Shutdown coordinator
     */
    public void shutdown() {
        logger.info("Shutting down Edge Coordinator");

        healthCheckExecutor.shutdown();
        inferenceExecutor.shutdown();

        try {
            if (!healthCheckExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                healthCheckExecutor.shutdownNow();
            }
            if (!inferenceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                inferenceExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthCheckExecutor.shutdownNow();
            inferenceExecutor.shutdownNow();
        }
    }
}
