/**
 * Deploy AI Brain On Chain to Hedera
 * Author: Sir Charles Spikes
 * Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI
 */

const { ethers } = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
    console.log("===============================================");
    console.log("🤖 AI BRAIN ON CHAIN - HEDERA DEPLOYMENT");
    console.log("By Sir Charles Spikes");
    console.log("Cincinnati, Ohio");
    console.log("===============================================\n");

    // Check if we're on Hedera network
    const network = await ethers.provider.getNetwork();
    console.log("🌐 Network:", network.name);
    console.log("🆔 Chain ID:", network.chainId);

    // Hedera Testnet configuration
    if (network.chainId === 296) {
        console.log("✅ Connected to Hedera Testnet");
    } else {
        console.log("⚠️  Not connected to Hedera. Please configure your network.");
        console.log("Expected Chain ID: 296 (Hedera Testnet)");
        return;
    }

    // Get deployer account
    const [deployer] = await ethers.getSigners();
    console.log("👤 Deployer:", deployer.address);

    const balance = await deployer.getBalance();
    console.log("💰 Balance:", ethers.utils.formatEther(balance), "HBAR");

    if (balance.lt(ethers.utils.parseEther("1"))) {
        console.log("❌ Insufficient balance. Need at least 1 HBAR for deployment.");
        console.log("Get free HBAR from: https://faucet.hedera.com");
        return;
    }

    // Deploy AIBrainOnChain contract
    console.log("\n🚀 Deploying AI Brain On Chain...");
    console.log("This contract transforms HBAR tokens into AI computations");
    console.log("No servers needed - pure blockchain AI!");

    const AIBrainOnChain = await ethers.getContractFactory("AIBrainOnChain");
    const aiBrain = await AIBrainOnChain.deploy();

    console.log("⏳ Waiting for deployment...");
    await aiBrain.deployed();

    console.log("✅ CONTRACT DEPLOYED SUCCESSFULLY!");
    console.log("🏠 Address:", aiBrain.address);
    console.log("📜 Transaction:", aiBrain.deployTransaction.hash);

    // Verify deployment
    const code = await ethers.provider.getCode(aiBrain.address);
    if (code !== "0x") {
        console.log("✅ Contract code verified on blockchain");
    }

    // Get contract information
    console.log("\n📊 Contract Configuration:");
    try {
        const pricePerCompute = await aiBrain.pricePerCompute();
        const platformFee = await aiBrain.platformFee();
        const minStakeAmount = await aiBrain.minStakeAmount();

        console.log("💰 Price per compute:", ethers.utils.formatEther(pricePerCompute), "HBAR");
        console.log("🏦 Platform fee:", platformFee.toString(), "basis points");
        console.log("💎 Min stake amount:", ethers.utils.formatEther(minStakeAmount), "HBAR");
    } catch (error) {
        console.log("⚠️  Could not read contract configuration");
    }

    // Save deployment information
    const deployment = {
        network: network.name,
        chainId: network.chainId,
        contractAddress: aiBrain.address,
        deployer: deployer.address,
        deploymentTx: aiBrain.deployTransaction.hash,
        blockNumber: aiBrain.deployTransaction.blockNumber,
        timestamp: new Date().toISOString(),
        configuration: {
            pricePerCompute: "0.001 HBAR",
            platformFee: "5%",
            minStakeAmount: "0.1 HBAR"
        }
    };

    // Create deployments directory
    const deploymentsDir = path.join(__dirname, "..", "deployments");
    if (!fs.existsSync(deploymentsDir)) {
        fs.mkdirSync(deploymentsDir);
    }

    // Save deployment data
    const deploymentPath = path.join(deploymentsDir, `hedera_deployment_${Date.now()}.json`);
    fs.writeFileSync(deploymentPath, JSON.stringify(deployment, null, 2));

    console.log("\n💾 Deployment saved to:", deploymentPath);

    // Test basic contract functionality
    console.log("\n🧪 Testing Contract Functionality...");

    try {
        // Get request count (should be 0)
        const requestCount = await aiBrain.getRequestCount();
        console.log("📋 Total requests:", requestCount.toString());

        // Get platform stats
        const stats = await aiBrain.getPlatformStats();
        console.log("📊 Platform stats:");
        console.log("  • Total requests:", stats[0].toString());
        console.log("  • Active nodes:", stats[1].toString());
        console.log("  • Total revenue:", ethers.utils.formatEther(stats[2]), "HBAR");

        // Test Gemma model registration
        console.log("\n🧠 Testing Gemma Model Registration...");
        const gemmaHash = "QmXT2xkFnG7FP7NTfmDfDFcQLSfCJ3xfPnjCg76gFnq1Hr";

        try {
            const modelInfo = await aiBrain.getModelInfo(gemmaHash);
            if (modelInfo[0] !== "0x0000000000000000000000000000000000000000") {
                console.log("✅ Gemma model found:");
                console.log("  • Owner:", modelInfo[0]);
                console.log("  • Name:", modelInfo[1]);
                console.log("  • Type:", modelInfo[2]);
                console.log("  • Usage:", modelInfo[6].toString());
            }
        } catch (error) {
            console.log("⚠️  Gemma model not found (expected for new deployment)");
        }

    } catch (error) {
        console.log("⚠️  Contract testing failed:", error.message);
    }

    // Generate usage instructions
    console.log("\n🎯 DEPLOYMENT COMPLETE!");
    console.log("==========================================");
    console.log("🏠 CONTRACT ADDRESS:", aiBrain.address);
    console.log("🌐 NETWORK: Hedera Testnet");
    console.log("💰 CURRENCY: HBAR (tinybars)");
    console.log("==========================================");

    console.log("\n📝 NEXT STEPS:");
    console.log("1. Copy the contract address above");
    console.log("2. Update your .env file:");
    echo "   GGUF_PROCESSOR_CONTRACT_ADDRESS=${aiBrain.address}";
    console.log("3. Start your local compute node:");
    echo "   npm run start:local-processor";
    console.log("4. Test AI requests:");
    echo "   npm run test:gemma-api";

    console.log("\n💡 USAGE EXAMPLES:");
    console.log("# Request AI processing (costs 0.001 HBAR)");
    echo "aiBrain.requestAI(modelCID, prompt)";
    console.log("# Register as compute node (stake 0.1 HBAR)");
    echo "aiBrain.registerComputeNode(nodeId, supportedModels)";
    console.log("# Check your results");
    echo "aiBrain.getResult(requestId)";

    console.log("\n🎉 READY FOR BLOCKCHAIN AI!");
    console.log("No servers. No limits. Just pure decentralized intelligence.");
    console.log("\nContact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI");
}

// Handle errors
main()
    .then(() => process.exit(0))
    .catch((error) => {
        console.error("❌ Deployment failed:", error);
        process.exit(1);
    });
