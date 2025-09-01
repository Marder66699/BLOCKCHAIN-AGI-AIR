// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * Local GGUF Processor - Smart contract for triggering local GGUF model processing
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/Counters.sol";

contract LocalGGUFProcessor is Ownable, ReentrancyGuard {
    using Counters for Counters.Counter;

    Counters.Counter private _requestIdCounter;

    // Processing request structure
    struct ProcessingRequest {
        uint256 id;
        address requester;
        string ipfsHash;
        string prompt;
        string apiKey;
        uint256 fee;
        RequestStatus status;
        uint256 createdAt;
        uint256 processedAt;
        string result;
        address processor; // Node that processed the request
    }

    // GGUF model registration
    struct GGUFModel {
        string ipfsHash;
        address owner;
        string modelType;
        string baseModel;
        string quantizationType;
        uint256 fileSize;
        uint256 parameterCount;
        uint256 contextSize;
        bool isActive;
        uint256 registrationFee;
        uint256 processingFee;
    }

    // Node registration (local machines running GGUF)
    struct ProcessingNode {
        address nodeAddress;
        string nodeId;
        uint256 stakeAmount;
        uint256 reputationScore;
        uint256 totalProcessed;
        uint256 successRate;
        bool isActive;
        uint256 lastActive;
        string[] supportedModels;
    }

    enum RequestStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        EXPIRED
    }

    // Mappings
    mapping(uint256 => ProcessingRequest) public processingRequests;
    mapping(string => GGUFModel) public ggufModels;
    mapping(address => ProcessingNode) public processingNodes;
    mapping(address => uint256[]) public userRequests;
    mapping(string => uint256) public ipfsToModelId;

    // Events
    event ModelRegistered(
        string indexed ipfsHash,
        address indexed owner,
        string modelType,
        uint256 registrationFee
    );

    event ProcessingRequested(
        uint256 indexed requestId,
        address indexed requester,
        string indexed ipfsHash,
        uint256 fee
    );

    event ProcessingStarted(
        uint256 indexed requestId,
        address indexed processor
    );

    event ProcessingCompleted(
        uint256 indexed requestId,
        address indexed processor,
        string result
    );

    event NodeRegistered(
        address indexed nodeAddress,
        string nodeId,
        uint256 stakeAmount
    );

    event NodeActivated(
        address indexed nodeAddress,
        bool isActive
    );

    // Configuration
    uint256 public minStakeAmount = 0.1 ether;
    uint256 public platformFee = 500; // 5%
    uint256 public requestTimeout = 5 minutes;
    uint256 public maxConcurrentRequests = 10;

    // Node management
    address[] public activeNodes;
    mapping(address => uint256) public activeRequestsPerNode;

    /**
     * Register a GGUF model with its IPFS hash
     * @param _ipfsHash IPFS hash of the GGUF model
     * @param _modelType Type of model (llama, mistral, etc.)
     * @param _baseModel Base model name
     * @param _quantizationType Quantization type (q4_0, q8_0, etc.)
     * @param _fileSize File size in bytes
     * @param _parameterCount Number of parameters
     * @param _contextSize Maximum context size
     * @param _processingFee Fee per processing request
     */
    function registerModel(
        string memory _ipfsHash,
        string memory _modelType,
        string memory _baseModel,
        string memory _quantizationType,
        uint256 _fileSize,
        uint256 _parameterCount,
        uint256 _contextSize,
        uint256 _processingFee
    ) external payable nonReentrant {
        require(bytes(_ipfsHash).length > 0, "IPFS hash required");
        require(ggufModels[_ipfsHash].owner == address(0), "Model already registered");

        uint256 registrationFee = 0.01 ether;
        require(msg.value >= registrationFee, "Insufficient registration fee");

        GGUFModel memory newModel = GGUFModel({
            ipfsHash: _ipfsHash,
            owner: msg.sender,
            modelType: _modelType,
            baseModel: _baseModel,
            quantizationType: _quantizationType,
            fileSize: _fileSize,
            parameterCount: _parameterCount,
            contextSize: _contextSize,
            isActive: true,
            registrationFee: registrationFee,
            processingFee: _processingFee
        });

        ggufModels[_ipfsHash] = newModel;
        ipfsToModelId[_ipfsHash] = block.timestamp; // Use timestamp as simple ID

        // Refund excess fee
        if (msg.value > registrationFee) {
            payable(msg.sender).transfer(msg.value - registrationFee);
        }

        emit ModelRegistered(_ipfsHash, msg.sender, _modelType, registrationFee);
    }

    /**
     * Register as a processing node
     * @param _nodeId Unique node identifier
     * @param _supportedModels Array of supported model IPFS hashes
     */
    function registerNode(
        string memory _nodeId,
        string[] memory _supportedModels
    ) external payable nonReentrant {
        require(msg.value >= minStakeAmount, "Insufficient stake amount");
        require(bytes(_nodeId).length > 0, "Node ID required");
        require(processingNodes[msg.sender].nodeAddress == address(0), "Node already registered");

        ProcessingNode memory newNode = ProcessingNode({
            nodeAddress: msg.sender,
            nodeId: _nodeId,
            stakeAmount: msg.value,
            reputationScore: 100, // Start with 100 reputation
            totalProcessed: 0,
            successRate: 100,
            isActive: true,
            lastActive: block.timestamp,
            supportedModels: _supportedModels
        });

        processingNodes[msg.sender] = newNode;
        activeNodes.push(msg.sender);

        emit NodeRegistered(msg.sender, _nodeId, msg.value);
    }

    /**
     * Submit a processing request
     * @param _ipfsHash IPFS hash of the model to use
     * @param _prompt Input prompt for processing
     * @param _apiKey User's API key for result delivery
     */
    function submitProcessingRequest(
        string memory _ipfsHash,
        string memory _prompt,
        string memory _apiKey
    ) external payable nonReentrant returns (uint256) {
        GGUFModel memory model = ggufModels[_ipfsHash];
        require(model.isActive, "Model not available");
        require(bytes(_prompt).length > 0, "Prompt required");
        require(bytes(_apiKey).length > 0, "API key required");

        uint256 totalFee = model.processingFee;
        require(msg.value >= totalFee, "Insufficient processing fee");

        _requestIdCounter.increment();
        uint256 requestId = _requestIdCounter.current();

        ProcessingRequest memory newRequest = ProcessingRequest({
            id: requestId,
            requester: msg.sender,
            ipfsHash: _ipfsHash,
            prompt: _prompt,
            apiKey: _apiKey,
            fee: totalFee,
            status: RequestStatus.PENDING,
            createdAt: block.timestamp,
            processedAt: 0,
            result: "",
            processor: address(0)
        });

        processingRequests[requestId] = newRequest;
        userRequests[msg.sender].push(requestId);

        // Find available node for processing
        address selectedNode = findAvailableNode(_ipfsHash);
        if (selectedNode != address(0)) {
            startProcessing(requestId, selectedNode);
        }

        emit ProcessingRequested(requestId, msg.sender, _ipfsHash, totalFee);

        return requestId;
    }

    /**
     * Find an available node that supports the requested model
     * @param _ipfsHash Model IPFS hash
     * @return Address of available node
     */
    function findAvailableNode(string memory _ipfsHash) internal view returns (address) {
        for (uint256 i = 0; i < activeNodes.length; i++) {
            address nodeAddr = activeNodes[i];
            ProcessingNode memory node = processingNodes[nodeAddr];

            if (!node.isActive) continue;
            if (activeRequestsPerNode[nodeAddr] >= maxConcurrentRequests) continue;

            // Check if node supports the model
            for (uint256 j = 0; j < node.supportedModels.length; j++) {
                if (keccak256(abi.encodePacked(node.supportedModels[j])) ==
                    keccak256(abi.encodePacked(_ipfsHash))) {
                    return nodeAddr;
                }
            }
        }

        return address(0);
    }

    /**
     * Start processing a request
     * @param _requestId Request ID
     * @param _processor Node address
     */
    function startProcessing(uint256 _requestId, address _processor) internal {
        require(processingRequests[_requestId].status == RequestStatus.PENDING, "Invalid request status");

        processingRequests[_requestId].status = RequestStatus.PROCESSING;
        processingRequests[_requestId].processor = _processor;
        activeRequestsPerNode[_processor]++;

        // Update node last active time
        processingNodes[_processor].lastActive = block.timestamp;

        emit ProcessingStarted(_requestId, _processor);
    }

    /**
     * Complete processing and store result
     * @param _requestId Request ID
     * @param _result Processing result
     */
    function completeProcessing(uint256 _requestId, string memory _result) external nonReentrant {
        ProcessingRequest storage request = processingRequests[_requestId];
        require(request.processor == msg.sender, "Not the assigned processor");
        require(request.status == RequestStatus.PROCESSING, "Request not in processing state");
        require(bytes(_result).length > 0, "Result required");

        request.status = RequestStatus.COMPLETED;
        request.result = _result;
        request.processedAt = block.timestamp;

        // Update processor statistics
        ProcessingNode storage node = processingNodes[msg.sender];
        node.totalProcessed++;
        node.reputationScore = (node.reputationScore * 99 + 100) / 100; // Moving average

        // Calculate and distribute fees
        uint256 platformFeeAmount = (request.fee * platformFee) / 10000;
        uint256 processorFee = request.fee - platformFeeAmount;

        // Pay processor
        payable(msg.sender).transfer(processorFee);

        // Platform fee stays in contract
        // Model owner could also get a share here

        // Decrease active requests counter
        activeRequestsPerNode[msg.sender]--;

        emit ProcessingCompleted(_requestId, msg.sender, _result);
    }

    /**
     * Fail a processing request
     * @param _requestId Request ID
     */
    function failProcessing(uint256 _requestId) external {
        ProcessingRequest storage request = processingRequests[_requestId];
        require(request.processor == msg.sender, "Not the assigned processor");
        require(request.status == RequestStatus.PROCESSING, "Request not in processing state");

        request.status = RequestStatus.FAILED;

        // Update processor statistics (lower reputation)
        ProcessingNode storage node = processingNodes[msg.sender];
        node.reputationScore = (node.reputationScore * 99 + 50) / 100; // Penalty for failure

        // Decrease active requests counter
        activeRequestsPerNode[msg.sender]--;

        // Refund requester (partial refund)
        uint256 refundAmount = request.fee / 2;
        payable(request.requester).transfer(refundAmount);
    }

    /**
     * Check for expired requests and reassign them
     */
    function checkExpiredRequests() external {
        uint256 currentTime = block.timestamp;

        for (uint256 i = 1; i <= _requestIdCounter.current(); i++) {
            ProcessingRequest storage request = processingRequests[i];

            if (request.status == RequestStatus.PENDING &&
                currentTime > request.createdAt + requestTimeout) {

                request.status = RequestStatus.EXPIRED;

                // Find new node for expired request
                address newNode = findAvailableNode(request.ipfsHash);
                if (newNode != address(0) && newNode != request.processor) {
                    startProcessing(i, newNode);
                } else {
                    // Refund if no node available
                    payable(request.requester).transfer(request.fee);
                }
            }
        }
    }

    /**
     * Get processing request details
     * @param _requestId Request ID
     */
    function getProcessingRequest(uint256 _requestId) external view returns (
        address requester,
        string memory ipfsHash,
        string memory prompt,
        RequestStatus status,
        uint256 createdAt,
        uint256 processedAt,
        string memory result,
        address processor
    ) {
        ProcessingRequest memory request = processingRequests[_requestId];
        return (
            request.requester,
            request.ipfsHash,
            request.prompt,
            request.status,
            request.createdAt,
            request.processedAt,
            request.result,
            request.processor
        );
    }

    /**
     * Get user's processing requests
     * @param _user User address
     */
    function getUserRequests(address _user) external view returns (uint256[] memory) {
        return userRequests[_user];
    }

    /**
     * Get node statistics
     * @param _node Node address
     */
    function getNodeStats(address _node) external view returns (
        string memory nodeId,
        uint256 stakeAmount,
        uint256 reputationScore,
        uint256 totalProcessed,
        uint256 successRate,
        bool isActive,
        uint256 activeRequests
    ) {
        ProcessingNode memory node = processingNodes[_node];
        return (
            node.nodeId,
            node.stakeAmount,
            node.reputationScore,
            node.totalProcessed,
            node.successRate,
            node.isActive,
            activeRequestsPerNode[_node]
        );
    }

    /**
     * Update configuration (owner only)
     */
    function updateConfig(
        uint256 _minStakeAmount,
        uint256 _platformFee,
        uint256 _requestTimeout,
        uint256 _maxConcurrentRequests
    ) external onlyOwner {
        minStakeAmount = _minStakeAmount;
        platformFee = _platformFee;
        requestTimeout = _requestTimeout;
        maxConcurrentRequests = _maxConcurrentRequests;
    }

    /**
     * Withdraw platform fees (owner only)
     */
    function withdrawFees() external onlyOwner {
        uint256 balance = address(this).balance;
        require(balance > 0, "No fees to withdraw");
        payable(owner()).transfer(balance);
    }

    /**
     * Emergency pause/unpause
     */
    function setNodeActive(address _node, bool _active) external {
        require(msg.sender == _node || msg.sender == owner(), "Not authorized");
        processingNodes[_node].isActive = _active;
        emit NodeActivated(_node, _active);
    }
}
