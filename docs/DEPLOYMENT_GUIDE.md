# AI ON BLOCKCHAIN - Deployment Guide 🚀

**Author:** Sir Charles Spikes  
**Email:** SirCharlesspikes5@gmail.com  
**Telegram:** [@SirGODSATANAGI](https://t.me/SirGODSATANAGI)

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development](#local-development)
3. [Testnet Deployment](#testnet-deployment)
4. [Mainnet Deployment](#mainnet-deployment)
5. [Post-Deployment](#post-deployment)
6. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Software
- Node.js v14.0.0 or higher
- npm v6.0.0 or higher
- Git
- Docker Desktop (optional, for AI Model Runner)

### Required Accounts
- MetaMask wallet with ETH/MATIC/BNB for gas
- Infura/Alchemy account for RPC endpoints
- Etherscan/Polygonscan API keys for verification

### Environment Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/sircharlesspikes/ai-on-blockchain.git
   cd ai-on-blockchain
   ```

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Configure environment:**
   ```bash
   cp .env.example .env
   ```

   Edit `.env` and add your configuration:
   ```env
   PRIVATE_KEY=your_private_key_here
   MAINNET_RPC_URL=https://eth-mainnet.g.alchemy.com/v2/your-key
   SEPOLIA_RPC_URL=https://eth-sepolia.g.alchemy.com/v2/your-key
   ETHERSCAN_API_KEY=your_etherscan_api_key
   ```

## Local Development

### 1. Start Local Blockchain

```bash
# Terminal 1: Start Hardhat node
npm run node
```

This will:
- Start a local blockchain on `http://127.0.0.1:8545`
- Create 20 test accounts with 10,000 ETH each
- Display private keys for testing

### 2. Deploy Contracts Locally

```bash
# Terminal 2: Deploy contracts
npm run deploy:localhost
```

Expected output:
```
===============================================
AI ON BLOCKCHAIN - CONTRACT DEPLOYMENT
By Sir Charles Spikes
Cincinnati, Ohio
===============================================

Deploying contracts with account: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
Account balance: 10000000000000000000000

1. Deploying AI Governance Token...
✅ AI Governance Token deployed to: 0x5FbDB2315678afecb367f032d93F642f64180aa3

2. Deploying AI Model NFT...
✅ AI Model NFT deployed to: 0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512

3. Deploying AI Service Marketplace...
✅ AI Service Marketplace deployed to: 0x9fE46736679d2D9a65F0992F2272dE9f3c7fa6e0
```

### 3. Interact with Contracts

```bash
# Run interaction script
npm run interact
```

## Testnet Deployment

### Sepolia (Ethereum Testnet)

1. **Get Sepolia ETH:**
   - Visit [Sepolia Faucet](https://sepoliafaucet.com/)
   - Request test ETH

2. **Deploy:**
   ```bash
   npm run deploy:sepolia
   ```

3. **Verify contracts:**
   ```bash
   npx hardhat verify --network sepolia DEPLOYED_CONTRACT_ADDRESS
   ```

### Mumbai (Polygon Testnet)

1. **Get Mumbai MATIC:**
   - Visit [Polygon Faucet](https://faucet.polygon.technology/)
   - Request test MATIC

2. **Deploy:**
   ```bash
   npm run deploy:mumbai
   ```

### BSC Testnet

1. **Get test BNB:**
   - Visit [BSC Testnet Faucet](https://testnet.binance.org/faucet-smart)

2. **Deploy:**
   ```bash
   npm run deploy:bscTestnet
   ```

## Mainnet Deployment

⚠️ **WARNING:** Mainnet deployment uses real funds. Double-check everything!

### Pre-deployment Checklist

- [ ] All contracts tested thoroughly
- [ ] Security audit completed
- [ ] Gas prices checked
- [ ] Sufficient funds in deployer wallet
- [ ] Backup of private keys
- [ ] Team review completed

### Ethereum Mainnet

1. **Check gas prices:**
   ```bash
   # Visit https://etherscan.io/gastracker
   ```

2. **Estimate deployment cost:**
   ```bash
   npx hardhat run scripts/estimate-gas.js --network mainnet
   ```

3. **Deploy:**
   ```bash
   npm run deploy:mainnet
   ```

### Polygon Mainnet

1. **Deploy:**
   ```bash
   npm run deploy:polygon
   ```

2. **Lower gas costs compared to Ethereum**

### Binance Smart Chain

1. **Deploy:**
   ```bash
   npm run deploy:bsc
   ```

## Post-Deployment

### 1. Verify Contracts

**Automated verification:**
```bash
npx hardhat verify --network mainnet GOVERNANCE_TOKEN_ADDRESS
npx hardhat verify --network mainnet MODEL_NFT_ADDRESS
npx hardhat verify --network mainnet MARKETPLACE_ADDRESS "GOVERNANCE_TOKEN_ADDRESS"
```

### 2. Initialize Platform

```javascript
// scripts/initialize.js
const { ethers } = require("hardhat");

async function initialize() {
    const [owner] = await ethers.getSigners();
    
    // Load deployed contracts
    const governance = await ethers.getContractAt("AIGovernanceToken", GOVERNANCE_ADDRESS);
    const marketplace = await ethers.getContractAt("AIServiceMarketplace", MARKETPLACE_ADDRESS);
    
    // Set initial parameters
    await marketplace.updatePlatformFee(250); // 2.5%
    
    // Mint initial tokens for liquidity
    await governance.mint(LIQUIDITY_POOL_ADDRESS, ethers.utils.parseEther("10000000"));
    
    console.log("Platform initialized!");
}
```

### 3. Security Setup

1. **Transfer ownership to multisig:**
   ```javascript
   await governance.transferOwnership(MULTISIG_ADDRESS);
   await marketplace.transferOwnership(MULTISIG_ADDRESS);
   ```

2. **Set up monitoring:**
   - Configure alerts for large transactions
   - Monitor contract events
   - Set up security scanning

### 4. Documentation

Update deployment information:
```json
// deployments/mainnet_deployment.json
{
    "network": "mainnet",
    "deployer": "0x...",
    "timestamp": "2025-01-09T12:00:00Z",
    "contracts": {
        "AIGovernanceToken": {
            "address": "0x...",
            "blockNumber": 18950000
        }
    }
}
```

## Troubleshooting

### Common Issues

**1. Insufficient funds:**
```
Error: insufficient funds for gas * price + value
```
Solution: Add more ETH/MATIC/BNB to your wallet

**2. Gas price too low:**
```
Error: transaction underpriced
```
Solution: Increase gas price in hardhat.config.js

**3. Nonce issues:**
```
Error: nonce too low
```
Solution: Reset MetaMask account or manually set nonce

**4. Contract size too large:**
```
Error: Contract code size exceeds 24576 bytes
```
Solution: Enable optimizer in hardhat.config.js

### Debug Commands

```bash
# Check network connection
npx hardhat console --network mainnet

# Test deployment without executing
npx hardhat run scripts/deploy.js --network mainnet --dry-run

# Get gas estimates
npx hardhat run scripts/gas-estimate.js --network mainnet
```

## Best Practices

1. **Always test on testnet first**
2. **Use hardware wallet for mainnet**
3. **Deploy during low gas periods**
4. **Keep deployment logs**
5. **Verify contracts immediately**
6. **Set up monitoring before going live**

---

**Need Help?**

Contact Sir Charles Spikes:
- Email: SirCharlesspikes5@gmail.com
- Telegram: @SirGODSATANAGI

*Building the future of AI on Blockchain* 🚀
