// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title Quantized Model Registry
 * @author Sir Charles Spikes
 * @dev Smart contract for managing quantized AI models with GGUF format support
 * @notice Cincinnati, Ohio | SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";

contract QuantizedModelRegistry is Ownable, ReentrancyGuard {
    
    // Quantization types
    enum QuantizationType {
        Q4_0,    // Legacy 4-bit
        Q4_1,    // Legacy 4-bit with extra constant
        Q5_0,    // Legacy 5-bit
        Q5_1,    // Legacy 5-bit with extra constant
        Q8_0,    // Legacy 8-bit
        Q2_K,    // 2-bit K-quant
        Q3_K_S,  // 3-bit K-quant small
        Q3_K_M,  // 3-bit K-quant medium
        Q3_K_L,  // 3-bit K-quant large
        Q4_K_S,  // 4-bit K-quant small
        Q4_K_M,  // 4-bit K-quant medium
        Q5_K_S,  // 5-bit K-quant small
        Q5_K_M,  // 5-bit K-quant medium
        Q6_K,    // 6-bit K-quant
        IQ2_XXS, // 2-bit I-quant extra extra small
        IQ2_XS,  // 2-bit I-quant extra small
        IQ2_S,   // 2-bit I-quant small
        IQ3_XS,  // 3-bit I-quant extra small
        IQ3_S,   // 3-bit I-quant small
        IQ3_M,   // 3-bit I-quant medium
        IQ4_XS,  // 4-bit I-quant extra small
        F16,     // 16-bit floating point
        F32      // 32-bit floating point
    }
    
    // Quantized model structure
    struct QuantizedModel {
        uint256 originalTokenId;      // Reference to original AI Model NFT
        string ipfsHash;              // IPFS hash of quantized model
        QuantizationType quantType;   // Quantization method used
        uint256 fileSize;             // File size in bytes
        uint256 contextSize;          // Maximum context size
        uint256 parameterCount;       // Number of parameters
        bool hasImportanceMatrix;     // Whether importance matrix was used
        uint256 gpuLayersRecommended; // Recommended GPU layers
        address quantizer;            // Who performed the quantization
        uint256 timestamp;            // When quantized
        uint256 downloads;            // Download counter
        mapping(address => bool) hasAccess; // Access control
    }
    
    // Model performance metrics
    struct PerformanceMetrics {
        uint256 inferenceSpeed;       // Tokens per second
        uint256 perplexity;          // Model quality metric
        uint256 memoryUsage;         // Memory usage in MB
        uint256 benchmarkScore;      // Overall benchmark score
        string hardwareProfile;      // Hardware used for testing
    }
    
    // Mappings
    mapping(uint256 => QuantizedModel) public quantizedModels;
    mapping(uint256 => PerformanceMetrics) public modelMetrics;
    mapping(address => uint256[]) public userQuantizedModels;
    mapping(string => uint256) public ipfsHashToModelId;
    
    // Counters and fees
    uint256 private modelIdCounter;
    uint256 public quantizationFee = 0.001 ether;
    uint256 public downloadFee = 0.0001 ether;
    
    // Events
    event ModelQuantized(
        uint256 indexed modelId,
        uint256 indexed originalTokenId,
        address indexed quantizer,
        QuantizationType quantType
    );
    event MetricsUpdated(uint256 indexed modelId, uint256 benchmarkScore);
    event ModelDownloaded(uint256 indexed modelId, address indexed downloader);
    event AccessGranted(uint256 indexed modelId, address indexed user);
    
    /**
     * @dev Register a quantized model
     * @param _originalTokenId Original AI Model NFT token ID
     * @param _ipfsHash IPFS hash of quantized model
     * @param _quantType Quantization type used
     * @param _fileSize File size in bytes
     * @param _contextSize Maximum context size
     * @param _parameterCount Number of parameters
     * @param _hasImportanceMatrix Whether importance matrix was used
     * @param _gpuLayers Recommended GPU layers for optimal performance
     */
    function registerQuantizedModel(
        uint256 _originalTokenId,
        string memory _ipfsHash,
        QuantizationType _quantType,
        uint256 _fileSize,
        uint256 _contextSize,
        uint256 _parameterCount,
        bool _hasImportanceMatrix,
        uint256 _gpuLayers
    ) external payable nonReentrant returns (uint256) {
        require(msg.value >= quantizationFee, "Insufficient fee");
        require(bytes(_ipfsHash).length > 0, "IPFS hash required");
        require(_fileSize > 0, "Invalid file size");
        require(ipfsHashToModelId[_ipfsHash] == 0, "Model already registered");
        
        modelIdCounter++;
        uint256 newModelId = modelIdCounter;
        
        QuantizedModel storage model = quantizedModels[newModelId];
        model.originalTokenId = _originalTokenId;
        model.ipfsHash = _ipfsHash;
        model.quantType = _quantType;
        model.fileSize = _fileSize;
        model.contextSize = _contextSize;
        model.parameterCount = _parameterCount;
        model.hasImportanceMatrix = _hasImportanceMatrix;
        model.gpuLayersRecommended = _gpuLayers;
        model.quantizer = msg.sender;
        model.timestamp = block.timestamp;
        model.hasAccess[msg.sender] = true;
        
        userQuantizedModels[msg.sender].push(newModelId);
        ipfsHashToModelId[_ipfsHash] = newModelId;
        
        emit ModelQuantized(newModelId, _originalTokenId, msg.sender, _quantType);
        
        return newModelId;
    }
    
    /**
     * @dev Update performance metrics for a model
     * @param _modelId Model ID
     * @param _inferenceSpeed Tokens per second
     * @param _perplexity Model quality metric
     * @param _memoryUsage Memory usage in MB
     * @param _hardwareProfile Hardware configuration used
     */
    function updateMetrics(
        uint256 _modelId,
        uint256 _inferenceSpeed,
        uint256 _perplexity,
        uint256 _memoryUsage,
        string memory _hardwareProfile
    ) external {
        require(quantizedModels[_modelId].quantizer == msg.sender, "Not model owner");
        
        PerformanceMetrics storage metrics = modelMetrics[_modelId];
        metrics.inferenceSpeed = _inferenceSpeed;
        metrics.perplexity = _perplexity;
        metrics.memoryUsage = _memoryUsage;
        metrics.hardwareProfile = _hardwareProfile;
        
        // Calculate benchmark score (higher is better)
        // Score = (speed * 100) / (perplexity * memoryUsage / 1000)
        uint256 score = (_inferenceSpeed * 100 * 1000) / (_perplexity * _memoryUsage);
        metrics.benchmarkScore = score;
        
        emit MetricsUpdated(_modelId, score);
    }
    
    /**
     * @dev Download a quantized model
     * @param _modelId Model ID to download
     */
    function downloadModel(uint256 _modelId) external payable nonReentrant {
        require(quantizedModels[_modelId].timestamp > 0, "Model not found");
        require(msg.value >= downloadFee, "Insufficient download fee");
        
        QuantizedModel storage model = quantizedModels[_modelId];
        require(model.hasAccess[msg.sender], "No access to model");
        
        model.downloads++;
        
        // Pay quantizer a portion of download fee
        uint256 quantizerShare = (msg.value * 70) / 100;
        payable(model.quantizer).transfer(quantizerShare);
        
        emit ModelDownloaded(_modelId, msg.sender);
    }
    
    /**
     * @dev Grant access to a model
     * @param _modelId Model ID
     * @param _user User to grant access to
     */
    function grantAccess(uint256 _modelId, address _user) external {
        require(quantizedModels[_modelId].quantizer == msg.sender, "Not model owner");
        quantizedModels[_modelId].hasAccess[_user] = true;
        emit AccessGranted(_modelId, _user);
    }
    
    /**
     * @dev Get recommended quantization for given constraints
     * @param _parameterCount Model parameter count
     * @param _maxMemoryMB Maximum memory in MB
     * @param _preferQuality Whether to prefer quality over speed
     * @return Recommended quantization type
     */
    function recommendQuantization(
        uint256 _parameterCount,
        uint256 _maxMemoryMB,
        bool _preferQuality
    ) external pure returns (QuantizationType) {
        // Calculate approximate memory usage for different quants
        uint256 mem4bit = (_parameterCount * 4) / 8 / 1024 / 1024;
        uint256 mem5bit = (_parameterCount * 5) / 8 / 1024 / 1024;
        uint256 mem6bit = (_parameterCount * 6) / 8 / 1024 / 1024;
        
        if (_maxMemoryMB > mem6bit && _preferQuality) {
            return QuantizationType.Q6_K;
        } else if (_maxMemoryMB > mem5bit) {
            return _preferQuality ? QuantizationType.Q5_K_M : QuantizationType.Q5_K_S;
        } else if (_maxMemoryMB > mem4bit) {
            return QuantizationType.Q4_K_M;
        } else {
            return QuantizationType.Q3_K_M;
        }
    }
    
    /**
     * @dev Calculate compression ratio
     * @param _quantType Quantization type
     * @return Compression ratio (x times smaller than F16)
     */
    function getCompressionRatio(QuantizationType _quantType) 
        external 
        pure 
        returns (uint256) 
    {
        // Approximate bits per weight for each quant type
        if (_quantType == QuantizationType.Q2_K) return 6; // 16/2.56 ≈ 6.25
        if (_quantType == QuantizationType.Q3_K_M) return 4; // 16/3.44 ≈ 4.65
        if (_quantType == QuantizationType.Q4_K_M) return 3; // 16/4.85 ≈ 3.30
        if (_quantType == QuantizationType.Q5_K_M) return 2; // 16/5.43 ≈ 2.95
        if (_quantType == QuantizationType.Q6_K) return 2; // 16/6.56 ≈ 2.44
        if (_quantType == QuantizationType.Q8_0) return 2; // 16/8 = 2
        return 1; // Default
    }
    
    /**
     * @dev Get user's quantized models
     * @param _user User address
     * @return Array of model IDs
     */
    function getUserModels(address _user) external view returns (uint256[] memory) {
        return userQuantizedModels[_user];
    }
    
    /**
     * @dev Update fees (owner only)
     * @param _quantizationFee New quantization fee
     * @param _downloadFee New download fee
     */
    function updateFees(uint256 _quantizationFee, uint256 _downloadFee) 
        external 
        onlyOwner 
    {
        quantizationFee = _quantizationFee;
        downloadFee = _downloadFee;
    }
    
    /**
     * @dev Withdraw accumulated fees (owner only)
     */
    function withdrawFees() external onlyOwner {
        uint256 balance = address(this).balance;
        require(balance > 0, "No fees to withdraw");
        payable(owner()).transfer(balance);
    }
}
