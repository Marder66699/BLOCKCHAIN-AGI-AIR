// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title AI Governance Token
 * @author Sir Charles Spikes
 * @dev ERC20 governance token for AI blockchain ecosystem
 * @notice Cincinnati, Ohio | SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Snapshot.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/security/Pausable.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Votes.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Permit.sol";

contract AIGovernanceToken is ERC20, ERC20Burnable, ERC20Snapshot, Ownable, Pausable, ERC20Votes, ERC20Permit {
    // Token details
    uint256 public constant MAX_SUPPLY = 1_000_000_000 * 10**18; // 1 billion tokens
    uint256 public constant INITIAL_SUPPLY = 100_000_000 * 10**18; // 100 million initial
    
    // Staking variables
    mapping(address => uint256) public stakedBalance;
    mapping(address => uint256) public stakingTimestamp;
    mapping(address => uint256) public rewardsClaimed;
    
    uint256 public totalStaked;
    uint256 public rewardRate = 100; // 1% per 30 days
    uint256 public constant STAKING_PERIOD = 30 days;
    
    // Events
    event TokensStaked(address indexed user, uint256 amount);
    event TokensUnstaked(address indexed user, uint256 amount);
    event RewardsClaimed(address indexed user, uint256 amount);
    event RewardRateUpdated(uint256 newRate);
    
    constructor() 
        ERC20("AI Governance Token", "AIGOV") 
        ERC20Permit("AI Governance Token") 
    {
        _mint(msg.sender, INITIAL_SUPPLY);
    }
    
    /**
     * @dev Mint new tokens (owner only, up to max supply)
     * @param to Address to mint tokens to
     * @param amount Amount of tokens to mint
     */
    function mint(address to, uint256 amount) external onlyOwner {
        require(totalSupply() + amount <= MAX_SUPPLY, "Exceeds max supply");
        _mint(to, amount);
    }
    
    /**
     * @dev Stake tokens for rewards
     * @param amount Amount of tokens to stake
     */
    function stake(uint256 amount) external whenNotPaused {
        require(amount > 0, "Cannot stake 0 tokens");
        require(balanceOf(msg.sender) >= amount, "Insufficient balance");
        
        // Claim pending rewards before staking
        if (stakedBalance[msg.sender] > 0) {
            _claimRewards(msg.sender);
        }
        
        _transfer(msg.sender, address(this), amount);
        stakedBalance[msg.sender] += amount;
        stakingTimestamp[msg.sender] = block.timestamp;
        totalStaked += amount;
        
        emit TokensStaked(msg.sender, amount);
    }
    
    /**
     * @dev Unstake tokens
     * @param amount Amount of tokens to unstake
     */
    function unstake(uint256 amount) external {
        require(amount > 0, "Cannot unstake 0 tokens");
        require(stakedBalance[msg.sender] >= amount, "Insufficient staked balance");
        
        // Claim pending rewards
        _claimRewards(msg.sender);
        
        stakedBalance[msg.sender] -= amount;
        totalStaked -= amount;
        
        _transfer(address(this), msg.sender, amount);
        
        emit TokensUnstaked(msg.sender, amount);
    }
    
    /**
     * @dev Claim staking rewards
     */
    function claimRewards() external {
        require(stakedBalance[msg.sender] > 0, "No staked tokens");
        _claimRewards(msg.sender);
    }
    
    /**
     * @dev Internal function to claim rewards
     * @param user Address of the user
     */
    function _claimRewards(address user) internal {
        uint256 rewards = calculateRewards(user);
        if (rewards > 0) {
            // Mint rewards (within max supply limit)
            uint256 mintable = rewards;
            if (totalSupply() + rewards > MAX_SUPPLY) {
                mintable = MAX_SUPPLY - totalSupply();
            }
            
            if (mintable > 0) {
                _mint(user, mintable);
                rewardsClaimed[user] += mintable;
                emit RewardsClaimed(user, mintable);
            }
        }
        stakingTimestamp[user] = block.timestamp;
    }
    
    /**
     * @dev Calculate pending rewards for a user
     * @param user Address of the user
     */
    function calculateRewards(address user) public view returns (uint256) {
        if (stakedBalance[user] == 0) {
            return 0;
        }
        
        uint256 stakingDuration = block.timestamp - stakingTimestamp[user];
        uint256 periods = stakingDuration / STAKING_PERIOD;
        
        if (periods == 0) {
            return 0;
        }
        
        uint256 rewards = (stakedBalance[user] * rewardRate * periods) / 10000;
        return rewards;
    }
    
    /**
     * @dev Update reward rate (owner only)
     * @param newRate New reward rate in basis points
     */
    function updateRewardRate(uint256 newRate) external onlyOwner {
        require(newRate <= 500, "Rate too high"); // Max 5%
        rewardRate = newRate;
        emit RewardRateUpdated(newRate);
    }
    
    /**
     * @dev Create a snapshot for governance
     */
    function snapshot() external onlyOwner returns (uint256) {
        return _snapshot();
    }
    
    /**
     * @dev Pause token transfers
     */
    function pause() external onlyOwner {
        _pause();
    }
    
    /**
     * @dev Unpause token transfers
     */
    function unpause() external onlyOwner {
        _unpause();
    }
    
    /**
     * @dev Get user staking info
     * @param user Address of the user
     */
    function getUserStakingInfo(address user) external view returns (
        uint256 staked,
        uint256 timestamp,
        uint256 pendingRewards,
        uint256 claimed
    ) {
        return (
            stakedBalance[user],
            stakingTimestamp[user],
            calculateRewards(user),
            rewardsClaimed[user]
        );
    }
    
    // Override required functions
    function _beforeTokenTransfer(
        address from,
        address to,
        uint256 amount
    ) internal override(ERC20, ERC20Snapshot) whenNotPaused {
        super._beforeTokenTransfer(from, to, amount);
    }
    
    function _afterTokenTransfer(
        address from,
        address to,
        uint256 amount
    ) internal override(ERC20, ERC20Votes) {
        super._afterTokenTransfer(from, to, amount);
    }
    
    function _mint(address to, uint256 amount) internal override(ERC20, ERC20Votes) {
        super._mint(to, amount);
    }
    
    function _burn(address account, uint256 amount) internal override(ERC20, ERC20Votes) {
        super._burn(account, amount);
    }
}
