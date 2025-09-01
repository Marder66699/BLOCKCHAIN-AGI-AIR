// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title AI Service Marketplace
 * @author Sir Charles Spikes
 * @dev Smart contract for decentralized AI services marketplace
 * @notice Cincinnati, Ohio | SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/Counters.sol";

contract AIServiceMarketplace is ReentrancyGuard, Ownable {
    using Counters for Counters.Counter;
    
    // State variables
    Counters.Counter private _serviceIdCounter;
    Counters.Counter private _jobIdCounter;
    
    IERC20 public paymentToken;
    uint256 public platformFeePercentage = 250; // 2.5%
    
    // Service structure
    struct AIService {
        uint256 id;
        address provider;
        string name;
        string description;
        string modelType;
        uint256 pricePerCall;
        bool isActive;
        uint256 totalCalls;
        uint256 rating;
        uint256 ratingCount;
    }
    
    // Job structure
    struct Job {
        uint256 id;
        uint256 serviceId;
        address requester;
        string inputData;
        string outputData;
        uint256 payment;
        JobStatus status;
        uint256 timestamp;
    }
    
    enum JobStatus {
        Pending,
        Processing,
        Completed,
        Cancelled,
        Disputed
    }
    
    // Mappings
    mapping(uint256 => AIService) public services;
    mapping(uint256 => Job) public jobs;
    mapping(address => uint256[]) public providerServices;
    mapping(address => uint256[]) public requesterJobs;
    mapping(address => uint256) public providerRatings;
    mapping(address => uint256) public providerBalance;
    
    // Events
    event ServiceRegistered(uint256 indexed serviceId, address indexed provider, string name);
    event ServiceUpdated(uint256 indexed serviceId, address indexed provider);
    event JobCreated(uint256 indexed jobId, uint256 indexed serviceId, address indexed requester);
    event JobCompleted(uint256 indexed jobId, uint256 indexed serviceId);
    event PaymentProcessed(uint256 indexed jobId, address provider, uint256 amount);
    event RatingSubmitted(uint256 indexed serviceId, address indexed rater, uint256 rating);
    
    /**
     * @dev Constructor
     * @param _paymentToken Address of the ERC20 token used for payments
     */
    constructor(address _paymentToken) {
        require(_paymentToken != address(0), "Invalid payment token");
        paymentToken = IERC20(_paymentToken);
    }
    
    /**
     * @dev Register a new AI service
     * @param _name Service name
     * @param _description Service description
     * @param _modelType Type of AI model (GPT, DALL-E, Custom, etc.)
     * @param _pricePerCall Price per API call in payment tokens
     */
    function registerService(
        string memory _name,
        string memory _description,
        string memory _modelType,
        uint256 _pricePerCall
    ) external {
        require(bytes(_name).length > 0, "Name cannot be empty");
        require(_pricePerCall > 0, "Price must be greater than 0");
        
        _serviceIdCounter.increment();
        uint256 newServiceId = _serviceIdCounter.current();
        
        services[newServiceId] = AIService({
            id: newServiceId,
            provider: msg.sender,
            name: _name,
            description: _description,
            modelType: _modelType,
            pricePerCall: _pricePerCall,
            isActive: true,
            totalCalls: 0,
            rating: 0,
            ratingCount: 0
        });
        
        providerServices[msg.sender].push(newServiceId);
        
        emit ServiceRegistered(newServiceId, msg.sender, _name);
    }
    
    /**
     * @dev Update service details
     * @param _serviceId Service ID to update
     * @param _pricePerCall New price per call
     * @param _isActive Service active status
     */
    function updateService(
        uint256 _serviceId,
        uint256 _pricePerCall,
        bool _isActive
    ) external {
        require(services[_serviceId].provider == msg.sender, "Not service owner");
        require(_pricePerCall > 0, "Price must be greater than 0");
        
        services[_serviceId].pricePerCall = _pricePerCall;
        services[_serviceId].isActive = _isActive;
        
        emit ServiceUpdated(_serviceId, msg.sender);
    }
    
    /**
     * @dev Create a new job request
     * @param _serviceId ID of the AI service to use
     * @param _inputData Input data for the AI service
     */
    function createJob(uint256 _serviceId, string memory _inputData) external nonReentrant {
        AIService memory service = services[_serviceId];
        require(service.isActive, "Service not active");
        require(bytes(_inputData).length > 0, "Input data cannot be empty");
        
        uint256 totalPayment = service.pricePerCall;
        require(paymentToken.transferFrom(msg.sender, address(this), totalPayment), "Payment failed");
        
        _jobIdCounter.increment();
        uint256 newJobId = _jobIdCounter.current();
        
        jobs[newJobId] = Job({
            id: newJobId,
            serviceId: _serviceId,
            requester: msg.sender,
            inputData: _inputData,
            outputData: "",
            payment: totalPayment,
            status: JobStatus.Pending,
            timestamp: block.timestamp
        });
        
        requesterJobs[msg.sender].push(newJobId);
        
        emit JobCreated(newJobId, _serviceId, msg.sender);
    }
    
    /**
     * @dev Complete a job and submit results
     * @param _jobId Job ID to complete
     * @param _outputData Results from the AI service
     */
    function completeJob(uint256 _jobId, string memory _outputData) external nonReentrant {
        Job storage job = jobs[_jobId];
        AIService storage service = services[job.serviceId];
        
        require(service.provider == msg.sender, "Not service provider");
        require(job.status == JobStatus.Pending, "Job not pending");
        require(bytes(_outputData).length > 0, "Output data cannot be empty");
        
        job.outputData = _outputData;
        job.status = JobStatus.Completed;
        
        // Calculate fees
        uint256 platformFee = (job.payment * platformFeePercentage) / 10000;
        uint256 providerPayment = job.payment - platformFee;
        
        // Update provider balance
        providerBalance[msg.sender] += providerPayment;
        providerBalance[owner()] += platformFee;
        
        // Update service stats
        service.totalCalls++;
        
        emit JobCompleted(_jobId, job.serviceId);
        emit PaymentProcessed(_jobId, msg.sender, providerPayment);
    }
    
    /**
     * @dev Submit rating for a completed job
     * @param _jobId Job ID to rate
     * @param _rating Rating from 1 to 5
     */
    function submitRating(uint256 _jobId, uint256 _rating) external {
        require(_rating >= 1 && _rating <= 5, "Rating must be between 1 and 5");
        
        Job memory job = jobs[_jobId];
        require(job.requester == msg.sender, "Not job requester");
        require(job.status == JobStatus.Completed, "Job not completed");
        
        AIService storage service = services[job.serviceId];
        
        // Update rating
        service.rating = ((service.rating * service.ratingCount) + _rating) / (service.ratingCount + 1);
        service.ratingCount++;
        
        emit RatingSubmitted(job.serviceId, msg.sender, _rating);
    }
    
    /**
     * @dev Withdraw accumulated balance
     */
    function withdrawBalance() external nonReentrant {
        uint256 balance = providerBalance[msg.sender];
        require(balance > 0, "No balance to withdraw");
        
        providerBalance[msg.sender] = 0;
        require(paymentToken.transfer(msg.sender, balance), "Withdrawal failed");
    }
    
    /**
     * @dev Update platform fee (owner only)
     * @param _newFeePercentage New fee percentage (basis points)
     */
    function updatePlatformFee(uint256 _newFeePercentage) external onlyOwner {
        require(_newFeePercentage <= 1000, "Fee too high"); // Max 10%
        platformFeePercentage = _newFeePercentage;
    }
    
    /**
     * @dev Get service details
     * @param _serviceId Service ID
     */
    function getService(uint256 _serviceId) external view returns (AIService memory) {
        return services[_serviceId];
    }
    
    /**
     * @dev Get job details
     * @param _jobId Job ID
     */
    function getJob(uint256 _jobId) external view returns (Job memory) {
        return jobs[_jobId];
    }
    
    /**
     * @dev Get provider's services
     * @param _provider Provider address
     */
    function getProviderServices(address _provider) external view returns (uint256[] memory) {
        return providerServices[_provider];
    }
    
    /**
     * @dev Get requester's jobs
     * @param _requester Requester address
     */
    function getRequesterJobs(address _requester) external view returns (uint256[] memory) {
        return requesterJobs[_requester];
    }
}
