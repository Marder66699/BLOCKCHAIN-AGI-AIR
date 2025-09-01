// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title AI Model NFT
 * @author Sir Charles Spikes
 * @dev NFT contract for tokenizing AI models and their ownership
 * @notice Cincinnati, Ohio | SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

import "@openzeppelin/contracts/token/ERC721/ERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721URIStorage.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/ERC721Burnable.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/Counters.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";

contract AIModelNFT is ERC721, ERC721URIStorage, ERC721Burnable, Ownable, ReentrancyGuard {
    using Counters for Counters.Counter;
    
    Counters.Counter private _tokenIdCounter;
    
    // Model metadata structure
    struct AIModel {
        string modelName;
        string modelType;
        string version;
        string ipfsHash;
        uint256 size;
        address creator;
        uint256 creationTime;
        uint256 price;
        bool isForSale;
        uint256 royaltyPercentage;
        string modelFormat; // GGUF, GGML, etc.
        uint256 contextSize; // Max context size for transformer models
        string baseModel; // Base model (llama2, mistral, etc.)
        bool isVerified; // Model verification status
    }
    
    // Mapping from token ID to model data
    mapping(uint256 => AIModel) public aiModels;
    
    // Mapping from creator to their model tokens
    mapping(address => uint256[]) public creatorModels;
    
    // Mapping for model sales
    mapping(uint256 => address) public modelBuyers;
    
    // Events
    event ModelMinted(uint256 indexed tokenId, address indexed creator, string modelName);
    event ModelListed(uint256 indexed tokenId, uint256 price);
    event ModelSold(uint256 indexed tokenId, address indexed buyer, uint256 price);
    event ModelDelisted(uint256 indexed tokenId);
    event RoyaltyPaid(uint256 indexed tokenId, address indexed creator, uint256 amount);
    
    constructor() ERC721("AI Model NFT", "AIMNFT") {}
    
    /**
     * @dev Mint a new AI model NFT
     * @param to Address to mint the NFT to
     * @param uri IPFS URI containing model metadata
     * @param _modelName Name of the AI model
     * @param _modelType Type of model (Transformer, CNN, RNN, etc.)
     * @param _version Model version
     * @param _ipfsHash IPFS hash of the model weights
     * @param _size Model size in bytes
     * @param _royaltyPercentage Royalty percentage for secondary sales (basis points)
     * @param _modelFormat Model format (GGUF, GGML, etc.)
     * @param _contextSize Max context size for transformer models
     * @param _baseModel Base model name (llama2, mistral, etc.)
     */
    function mintAIModel(
        address to,
        string memory uri,
        string memory _modelName,
        string memory _modelType,
        string memory _version,
        string memory _ipfsHash,
        uint256 _size,
        uint256 _royaltyPercentage,
        string memory _modelFormat,
        uint256 _contextSize,
        string memory _baseModel
    ) public returns (uint256) {
        require(to != address(0), "Cannot mint to zero address");
        require(bytes(_modelName).length > 0, "Model name required");
        require(bytes(_ipfsHash).length > 0, "IPFS hash required");
        require(_royaltyPercentage <= 1000, "Royalty too high"); // Max 10%
        
        _tokenIdCounter.increment();
        uint256 tokenId = _tokenIdCounter.current();
        
        _safeMint(to, tokenId);
        _setTokenURI(tokenId, uri);
        
        aiModels[tokenId] = AIModel({
            modelName: _modelName,
            modelType: _modelType,
            version: _version,
            ipfsHash: _ipfsHash,
            size: _size,
            creator: msg.sender,
            creationTime: block.timestamp,
            price: 0,
            isForSale: false,
            royaltyPercentage: _royaltyPercentage,
            modelFormat: _modelFormat,
            contextSize: _contextSize,
            baseModel: _baseModel,
            isVerified: false
        });
        
        creatorModels[msg.sender].push(tokenId);
        
        emit ModelMinted(tokenId, msg.sender, _modelName);
        
        return tokenId;
    }
    
    /**
     * @dev List model for sale
     * @param tokenId Token ID of the model
     * @param price Sale price in wei
     */
    function listModelForSale(uint256 tokenId, uint256 price) external {
        require(ownerOf(tokenId) == msg.sender, "Not the owner");
        require(price > 0, "Price must be greater than 0");
        
        aiModels[tokenId].price = price;
        aiModels[tokenId].isForSale = true;
        
        emit ModelListed(tokenId, price);
    }
    
    /**
     * @dev Remove model from sale
     * @param tokenId Token ID of the model
     */
    function delistModel(uint256 tokenId) external {
        require(ownerOf(tokenId) == msg.sender, "Not the owner");
        
        aiModels[tokenId].isForSale = false;
        aiModels[tokenId].price = 0;
        
        emit ModelDelisted(tokenId);
    }
    
    /**
     * @dev Purchase a listed model
     * @param tokenId Token ID of the model to purchase
     */
    function purchaseModel(uint256 tokenId) external payable nonReentrant {
        AIModel memory model = aiModels[tokenId];
        require(model.isForSale, "Model not for sale");
        require(msg.value >= model.price, "Insufficient payment");
        
        address seller = ownerOf(tokenId);
        require(seller != msg.sender, "Cannot buy own model");
        
        // Calculate royalty
        uint256 royaltyAmount = 0;
        if (model.creator != seller && model.royaltyPercentage > 0) {
            royaltyAmount = (msg.value * model.royaltyPercentage) / 10000;
            payable(model.creator).transfer(royaltyAmount);
            emit RoyaltyPaid(tokenId, model.creator, royaltyAmount);
        }
        
        // Transfer remaining amount to seller
        uint256 sellerAmount = msg.value - royaltyAmount;
        payable(seller).transfer(sellerAmount);
        
        // Transfer NFT
        _transfer(seller, msg.sender, tokenId);
        
        // Update sale status
        aiModels[tokenId].isForSale = false;
        aiModels[tokenId].price = 0;
        
        emit ModelSold(tokenId, msg.sender, msg.value);
    }
    
    /**
     * @dev Get model details
     * @param tokenId Token ID of the model
     */
    function getModelDetails(uint256 tokenId) external view returns (AIModel memory) {
        require(_exists(tokenId), "Model does not exist");
        return aiModels[tokenId];
    }
    
    /**
     * @dev Get all models by creator
     * @param creator Address of the creator
     */
    function getModelsByCreator(address creator) external view returns (uint256[] memory) {
        return creatorModels[creator];
    }
    
    /**
     * @dev Update model metadata (only owner)
     * @param tokenId Token ID of the model
     * @param newUri New IPFS URI
     */
    function updateModelURI(uint256 tokenId, string memory newUri) external {
        require(ownerOf(tokenId) == msg.sender, "Not the owner");
        _setTokenURI(tokenId, newUri);
    }
    
    // Override required functions
    function _burn(uint256 tokenId) internal override(ERC721, ERC721URIStorage) {
        super._burn(tokenId);
    }
    
    function tokenURI(uint256 tokenId) public view override(ERC721, ERC721URIStorage) returns (string memory) {
        return super.tokenURI(tokenId);
    }
    
    function supportsInterface(bytes4 interfaceId) public view override(ERC721, ERC721URIStorage) returns (bool) {
        return super.supportsInterface(interfaceId);
    }
}
