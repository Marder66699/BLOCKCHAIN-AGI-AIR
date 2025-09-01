// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

/**
 * @title AI Brain On Chain - Serverless AGI on Hedera
 * @author Sir Charles Spikes
 * @dev Smart contract that transforms blockchain tokens into AI computations
 * @notice Cincinnati, Ohio | SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 *
 * THE VISION: AGI WITHOUT SERVERS
 * - Deploy AI models to blockchain
 * - Execute computations without ANY server
 * - Transform tokens into AI logic
 * - Scale to infinity with zero infrastructure
 * - Log everything immutably on IPFS
 *
 * THE RESULT: Serverless AGI that runs forever on the blockchain
 */

import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/Counters.sol";

contract AIBrainOnChain is Ownable, ReentrancyGuard {
    using Counters for Counters.Counter;

    Counters.Counter private _requestIdCounter;
    Counters.Counter private _conversationIdCounter;

    // THE CORE: Token becomes Intelligence
    struct AIRequest {
        uint256 id;
        address user;
        string modelCID;           // IPFS address of GGUF model (QmXT2xkFnG7FP7NTfmDfDFcQLSfCJ3xfPnjCg76gFnq1Hr)
        string prompt;             // What to ask the AI
        uint256 tokensPaid;        // Payment = Priority (in HBAR)
        string resultCID;          // Answer stored on IPFS
        uint256 createdAt;
        uint256 completedAt;
        RequestStatus status;
        address processor;         // Which node processed it
        uint256 priority;          // Based on tokens paid
    }

    // GGUF Model Registry
    struct GGUFModel {
        string ipfsHash;
        address owner;
        string modelName;
        string quantizationType;   // Q4_0, Q8_0, etc.
        uint256 parameterCount;
        uint256 contextSize;
        uint256 fileSize;
        bool isActive;
        uint256 usageCount;
        uint256 totalRevenue;
    }

    // Compute Node Registry
    struct ComputeNode {
        address nodeAddress;
        string nodeId;
        uint256 stakeAmount;
        uint256 reputationScore;
        uint256 totalProcessed;
        uint256 successRate;
        bool isActive;
        uint256 lastActive;
        string[] supportedModels;
        uint256 processingPower;   // Tokens per second
    }

    // Conversation History
    struct Conversation {
        uint256 id;
        address user;
        uint256[] requestIds;
        string conversationCID;    // IPFS hash of full conversation
        uint256 createdAt;
        uint256 lastActivity;
        uint256 totalTokensSpent;
    }

    enum RequestStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        EXPIRED,
        CANCELLED
    }

    // STATE: Everything logged forever
    mapping(uint256 => AIRequest) public requests;
    mapping(address => string) public userAPIKeys;
    mapping(address => uint256[]) public userRequests;
    mapping(address => Conversation) public userConversations;
    mapping(string => GGUFModel) public ggufModels;
    mapping(address => ComputeNode) public computeNodes;
    mapping(string => uint256) public ipfsToModelId;

    // Active nodes for load balancing
    address[] public activeNodes;

    // CONFIGURATION
    uint256 public pricePerCompute = 1000000; // 0.001 HBAR (in tinybars)
    uint256 public minStakeAmount = 100000000; // 0.1 HBAR
    uint256 public requestTimeout = 5 minutes;
    uint256 public maxConcurrentRequests = 100;
    uint256 public platformFee = 200; // 2%

    // EVENTS: Trigger off-chain AI
    event AIJobCreated(
        uint256 indexed jobId,
        address indexed user,
        string modelCID,
        string prompt,
        uint256 tokensPaid,
        uint256 priority
    );

    event AIJobCompleted(
        uint256 indexed jobId,
        address indexed processor,
        string resultCID,
        uint256 processingTime
    );

    event AIJobFailed(
        uint256 indexed jobId,
        string reason
    );

    event ModelRegistered(
        string indexed ipfsHash,
        address indexed owner,
        string modelName
    );

    event NodeRegistered(
        address indexed nodeAddress,
        string nodeId,
        uint256 stakeAmount
    );

    event ConversationLogged(
        address indexed user,
        uint256 indexed conversationId,
        string conversationCID
    );

    event RevenueDistributed(
        address indexed node,
        address indexed modelOwner,
        address indexed platform,
        uint256 nodeShare,
        uint256 ownerShare,
        uint256 platformShare
    );

    // CONSTRUCTOR
    constructor() {
        // Register the user's Gemma model automatically
        _registerModelInternal(
            "QmXT2xkFnG7FP7NTfmDfDFcQLSfCJ3xfPnjCg76gFnq1Hr",
            "Gemma 3 270M Q4_0",
            "Q4_0",
            270000000,  // 270M parameters
            4096,       // context size
            230000000   // ~230MB
        );
    }

    // THE MAGIC: Token → AI Logic
    function requestAI(string memory modelCID, string memory prompt)
        external
        payable
        nonReentrant
        returns (uint256)
    {
        require(msg.value >= pricePerCompute, "Pay up for AI! Minimum 0.001 HBAR");
        require(bytes(prompt).length > 0 && bytes(prompt).length <= 2048, "Prompt too long");
        require(bytes(modelCID).length > 0, "Model CID required");

        // Verify model exists and is active
        GGUFModel memory model = ggufModels[modelCID];
        require(model.isActive, "Model not available");

        // Create computation request
        _requestIdCounter.increment();
        uint256 jobId = _requestIdCounter.current();

        // Calculate priority based on payment (higher payment = higher priority)
        uint256 priority = msg.value / pricePerCompute;

        requests[jobId] = AIRequest({
            id: jobId,
            user: msg.sender,
            modelCID: modelCID,
            prompt: prompt,
            tokensPaid: msg.value,
            resultCID: "",
            createdAt: block.timestamp,
            completedAt: 0,
            status: RequestStatus.PENDING,
            processor: address(0),
            priority: priority
        });

        // Track user requests
        userRequests[msg.sender].push(jobId);

        // Update user conversation
        _updateUserConversation(msg.sender, jobId);

        // Find available compute node
        address selectedNode = _findOptimalNode(modelCID, priority);
        if (selectedNode != address(0)) {
            _assignJobToNode(jobId, selectedNode);
        }

        // Update model usage statistics
        ggufModels[modelCID].usageCount++;
        ggufModels[modelCID].totalRevenue += msg.value;

        // EMIT: This triggers off-chain computation!
        emit AIJobCreated(jobId, msg.sender, modelCID, prompt, msg.value, priority);

        return jobId;
    }

    // REGISTER: Your personal AI API endpoint
    function registerAPIKey(string memory apiKey) external {
        require(bytes(apiKey).length >= 32, "API key too short");
        userAPIKeys[msg.sender] = apiKey;
    }

    // ORACLE: Off-chain service writes results back
    function completeJob(uint256 jobId, string memory resultCID)
        external
        nonReentrant
    {
        AIRequest storage request = requests[jobId];
        require(request.status == RequestStatus.PROCESSING, "Job not in processing state");
        require(request.processor == msg.sender, "Not the assigned processor");

        request.status = RequestStatus.COMPLETED;
        request.resultCID = resultCID;
        request.completedAt = block.timestamp;

        // Update node statistics
        ComputeNode storage node = computeNodes[msg.sender];
        node.totalProcessed++;
        node.reputationScore = (node.reputationScore * 99 + 100) / 100; // Moving average
        node.lastActive = block.timestamp;

        // Distribute revenue
        uint256 totalRevenue = request.tokensPaid;
        uint256 platformShare = (totalRevenue * platformFee) / 10000;
        uint256 ownerShare = (totalRevenue * 3000) / 10000; // 30% to model owner
        uint256 nodeShare = totalRevenue - platformShare - ownerShare;

        // Get model owner
        GGUFModel memory model = ggufModels[request.modelCID];
        address modelOwner = model.owner;

        // Transfer payments
        payable(msg.sender).transfer(nodeShare);           // Node gets 68%
        payable(modelOwner).transfer(ownerShare);          // Owner gets 30%
        payable(owner()).transfer(platformShare);          // Platform gets 2%

        // Release node from active processing
        _releaseNodeFromJob(msg.sender);

        emit AIJobCompleted(jobId, msg.sender, resultCID, request.completedAt - request.createdAt);
        emit RevenueDistributed(msg.sender, modelOwner, owner(), nodeShare, ownerShare, platformShare);
    }

    // FAIL: Handle failed processing
    function failJob(uint256 jobId, string memory reason) external {
        AIRequest storage request = requests[jobId];
        require(request.status == RequestStatus.PROCESSING, "Job not in processing state");
        require(request.processor == msg.sender, "Not the assigned processor");

        request.status = RequestStatus.FAILED;

        // Update node reputation (penalty)
        ComputeNode storage node = computeNodes[msg.sender];
        node.reputationScore = (node.reputationScore * 99 + 20) / 100; // Penalty for failure

        // Release node and find new processor
        _releaseNodeFromJob(msg.sender);
        address newNode = _findOptimalNode(request.modelCID, request.priority);
        if (newNode != address(0)) {
            _assignJobToNode(jobId, newNode);
        }

        emit AIJobFailed(jobId, reason);
    }

    // REGISTER COMPUTE NODE: Join the AI network
    function registerComputeNode(
        string memory nodeId,
        string[] memory supportedModels
    ) external payable nonReentrant {
        require(msg.value >= minStakeAmount, "Insufficient stake amount");
        require(bytes(nodeId).length > 0, "Node ID required");
        require(supportedModels.length > 0, "Must support at least one model");

        ComputeNode memory newNode = ComputeNode({
            nodeAddress: msg.sender,
            nodeId: nodeId,
            stakeAmount: msg.value,
            reputationScore: 100, // Start with perfect reputation
            totalProcessed: 0,
            successRate: 100,
            isActive: true,
            lastActive: block.timestamp,
            supportedModels: supportedModels,
            processingPower: 10 // Default 10 tokens/second
        });

        computeNodes[msg.sender] = newNode;
        activeNodes.push(msg.sender);

        emit NodeRegistered(msg.sender, nodeId, msg.value);
    }

    // LOGGING: Permanent conversation history
    function logConversation(string memory ipfsCID) external {
        require(bytes(ipfsCID).length > 0, "IPFS CID required");

        _conversationIdCounter.increment();
        uint256 conversationId = _conversationIdCounter.current();

        Conversation storage conv = userConversations[msg.sender];
        conv.id = conversationId;
        conv.user = msg.sender;
        conv.conversationCID = ipfsCID;
        conv.lastActivity = block.timestamp;

        if (conv.createdAt == 0) {
            conv.createdAt = block.timestamp;
        }

        emit ConversationLogged(msg.sender, conversationId, ipfsCID);
    }

    // RETRIEVE: Get your AI results
    function getResult(uint256 jobId) external view returns (
        string memory resultCID,
        RequestStatus status,
        uint256 completedAt,
        address processor
    ) {
        AIRequest memory request = requests[jobId];
        require(request.user == msg.sender, "Not your request");

        return (
            request.resultCID,
            request.status,
            request.completedAt,
            request.processor
        );
    }

    // HISTORY: See all your AI interactions
    function getMyHistory() external view returns (uint256[] memory) {
        return userRequests[msg.sender];
    }

    // GET CONVERSATION
    function getConversation(address user) external view returns (
        uint256 id,
        uint256[] memory requestIds,
        string memory conversationCID,
        uint256 createdAt,
        uint256 totalTokensSpent
    ) {
        Conversation memory conv = userConversations[user];
        return (
            conv.id,
            conv.requestIds,
            conv.conversationCID,
            conv.createdAt,
            conv.totalTokensSpent
        );
    }

    // GET MODEL INFO
    function getModelInfo(string memory ipfsHash) external view returns (
        address owner,
        string memory modelName,
        string memory quantizationType,
        uint256 parameterCount,
        uint256 contextSize,
        bool isActive,
        uint256 usageCount
    ) {
        GGUFModel memory model = ggufModels[ipfsHash];
        return (
            model.owner,
            model.modelName,
            model.quantizationType,
            model.parameterCount,
            model.contextSize,
            model.isActive,
            model.usageCount
        );
    }

    // GET NODE INFO
    function getNodeInfo(address nodeAddress) external view returns (
        string memory nodeId,
        uint256 reputationScore,
        uint256 totalProcessed,
        bool isActive,
        uint256 processingPower
    ) {
        ComputeNode memory node = computeNodes[nodeAddress];
        return (
            node.nodeId,
            node.reputationScore,
            node.totalProcessed,
            node.isActive,
            node.processingPower
        );
    }

    // INTERNAL FUNCTIONS

    function _registerModelInternal(
        string memory ipfsHash,
        string memory modelName,
        string memory quantizationType,
        uint256 parameterCount,
        uint256 contextSize,
        uint256 fileSize
    ) internal {
        GGUFModel memory newModel = GGUFModel({
            ipfsHash: ipfsHash,
            owner: owner(), // Platform owns default models
            modelName: modelName,
            quantizationType: quantizationType,
            parameterCount: parameterCount,
            contextSize: contextSize,
            fileSize: fileSize,
            isActive: true,
            usageCount: 0,
            totalRevenue: 0
        });

        ggufModels[ipfsHash] = newModel;
        ipfsToModelId[ipfsHash] = block.timestamp;

        emit ModelRegistered(ipfsHash, owner(), modelName);
    }

    function _findOptimalNode(string memory modelCID, uint256 priority) internal view returns (address) {
        address bestNode = address(0);
        uint256 bestScore = 0;

        for (uint256 i = 0; i < activeNodes.length; i++) {
            address nodeAddr = activeNodes[i];
            ComputeNode memory node = computeNodes[nodeAddr];

            if (!node.isActive) continue;

            // Check if node supports the model
            bool supportsModel = false;
            for (uint256 j = 0; j < node.supportedModels.length; j++) {
                if (keccak256(abi.encodePacked(node.supportedModels[j])) ==
                    keccak256(abi.encodePacked(modelCID))) {
                    supportsModel = true;
                    break;
                }
            }

            if (!supportsModel) continue;

            // Calculate score based on reputation, load, and priority
            uint256 score = node.reputationScore * node.processingPower * (100 + priority);
            // Lower score if node has many active requests
            // (This is simplified - in production, track active requests per node)

            if (score > bestScore) {
                bestScore = score;
                bestNode = nodeAddr;
            }
        }

        return bestNode;
    }

    function _assignJobToNode(uint256 jobId, address nodeAddress) internal {
        requests[jobId].status = RequestStatus.PROCESSING;
        requests[jobId].processor = nodeAddress;

        // Update node last active time
        computeNodes[nodeAddress].lastActive = block.timestamp;
    }

    function _releaseNodeFromJob(address nodeAddress) internal {
        // In production, decrement active requests counter
        // For now, this is a placeholder
    }

    function _updateUserConversation(address user, uint256 requestId) internal {
        Conversation storage conv = userConversations[user];
        conv.requestIds.push(requestId);
        conv.lastActivity = block.timestamp;
        conv.totalTokensSpent += requests[requestId].tokensPaid;

        if (conv.createdAt == 0) {
            conv.createdAt = block.timestamp;
        }
    }

    // ADMIN FUNCTIONS

    function updatePricing(uint256 newPricePerCompute) external onlyOwner {
        pricePerCompute = newPricePerCompute;
    }

    function updatePlatformFee(uint256 newFee) external onlyOwner {
        require(newFee <= 1000, "Fee too high"); // Max 10%
        platformFee = newFee;
    }

    function setNodeActive(address nodeAddress, bool active) external onlyOwner {
        computeNodes[nodeAddress].isActive = active;
    }

    function withdrawPlatformFees() external onlyOwner {
        uint256 balance = address(this).balance;
        require(balance > 0, "No fees to withdraw");
        payable(owner()).transfer(balance);
    }

    // VIEW FUNCTIONS

    function getRequestCount() external view returns (uint256) {
        return _requestIdCounter.current();
    }

    function getActiveNodeCount() external view returns (uint256) {
        return activeNodes.length;
    }

    function getPlatformStats() external view returns (
        uint256 totalRequests,
        uint256 activeNodes,
        uint256 totalRevenue,
        uint256 averageProcessingTime
    ) {
        uint256 revenue = 0;
        uint256 completedRequests = 0;
        uint256 totalProcessingTime = 0;

        for (uint256 i = 1; i <= _requestIdCounter.current(); i++) {
            AIRequest memory request = requests[i];
            if (request.status == RequestStatus.COMPLETED) {
                revenue += request.tokensPaid;
                completedRequests++;
                if (request.completedAt > request.createdAt) {
                    totalProcessingTime += (request.completedAt - request.createdAt);
                }
            }
        }

        uint256 avgProcessingTime = completedRequests > 0 ?
            totalProcessingTime / completedRequests : 0;

        return (
            _requestIdCounter.current(),
            activeNodes.length,
            revenue,
            avgProcessingTime
        );
    }

    // EMERGENCY FUNCTIONS

    function pauseAllOperations() external onlyOwner {
        // Implementation for emergency pause
    }

    function resumeAllOperations() external onlyOwner {
        // Implementation for emergency resume
    }
}
