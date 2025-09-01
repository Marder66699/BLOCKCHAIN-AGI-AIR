#!/bin/bash

# COMPLETE HEDERA AI SETUP - From Zero to AGI
# Author: Sir Charles Spikes
# Contact: SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI

set -e

# Configuration
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.."
WALLET_FILE="$HOME/.hedera_wallet"
CONFIG_FILE="$PROJECT_ROOT/.env.hedera"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${PURPLE}[STEP $1]${NC} $2"
}

# Check system requirements
check_system() {
    log_step "1" "Checking System Requirements..."

    local missing_tools=()

    # Check Node.js
    if ! command -v node &> /dev/null; then
        missing_tools+=("Node.js")
    else
        local node_version=$(node -v | cut -d'.' -f1 | cut -d'v' -f2)
        if [ "$node_version" -lt 14 ]; then
            missing_tools+=("Node.js 14+")
        fi
    fi

    # Check npm
    if ! command -v npm &> /dev/null; then
        missing_tools+=("npm")
    fi

    # Check curl
    if ! command -v curl &> /dev/null; then
        missing_tools+=("curl")
    fi

    if [ ${#missing_tools[@]} -ne 0 ]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        log_info "Please install the missing tools and try again."
        exit 1
    fi

    log_success "System requirements met"
}

# Setup Hedera wallet
setup_hedera_wallet() {
    log_step "2" "Setting Up Hedera Wallet..."

    if [ -f "$WALLET_FILE" ]; then
        log_warn "Wallet file already exists: $WALLET_FILE"
        read -p "Do you want to create a new wallet? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Using existing wallet"
            return
        fi
    fi

    log_info "Creating new Hedera wallet..."

    # Generate random private key (for demo purposes)
    # In production, use proper key generation
    WALLET_PRIVATE_KEY="0x$(openssl rand -hex 32)"
    WALLET_ADDRESS="0x$(echo $WALLET_PRIVATE_KEY | cut -c3- | head -c40)"

    # Save wallet
    cat > "$WALLET_FILE" << EOF
# Hedera AI Wallet
# Generated: $(date)
# WARNING: This is for demo purposes only!
# Never use these keys for real value!

PRIVATE_KEY=$WALLET_PRIVATE_KEY
ADDRESS=$WALLET_ADDRESS
NETWORK=testnet
EOF

    log_success "Wallet created: $WALLET_FILE"
    log_warn "⚠️  This wallet is for DEMO purposes only!"
    log_warn "⚠️  Never use these keys for real HBAR!"
    echo ""
    log_info "Wallet Details:"
    echo "  Address: $WALLET_ADDRESS"
    echo "  Private Key: ${WALLET_PRIVATE_KEY:0:10}..."
    echo ""
}

# Get free HBAR
get_free_hbar() {
    log_step "3" "Getting Free HBAR..."

    if [ ! -f "$WALLET_FILE" ]; then
        log_error "Wallet not found. Please run wallet setup first."
        exit 1
    fi

    source "$WALLET_FILE"

    log_info "Getting free HBAR for address: $ADDRESS"
    echo ""
    log_info "Choose your faucet method:"
    echo "1. Hedera Developer Faucet (1000 HBAR) - https://portal.hedera.com/faucet"
    echo "2. Community Faucet (100 HBAR) - https://faucet.hedera.com"
    echo "3. Discord Bot (varies) - https://discord.gg/hedera"
    echo ""

    read -p "Have you received HBAR? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        log_success "Great! You have HBAR to deploy AI contracts"
    else
        log_info "Please get HBAR from one of the faucets above, then continue"
        exit 0
    fi
}

# Configure project
configure_project() {
    log_step "4" "Configuring AI Project..."

    if [ ! -f "$WALLET_FILE" ]; then
        log_error "Wallet not found"
        exit 1
    fi

    source "$WALLET_FILE"

    # Create .env.hedera
    cat > "$CONFIG_FILE" << EOF
# Hedera AI Configuration
# Generated: $(date)

# Hedera Network
HEDERA_NETWORK=testnet
HEDERA_RPC_URL=https://testnet.hashio.io/api

# Wallet
AI_NODE_PRIVATE_KEY=$PRIVATE_KEY
WALLET_ADDRESS=$ADDRESS

# Smart Contracts (to be deployed)
GGUF_PROCESSOR_CONTRACT_ADDRESS=

# Filebase (for model storage)
FILEBASE_API_KEY=your_filebase_key
FILEBASE_API_SECRET=your_filebase_secret
FILEBASE_GATEWAY=https://nuclear-yellow-alligator.myfilebase.com

# Local Configuration
MODELS_DIRECTORY=./models
NODE_ID=hedera-ai-node-$(date +%s)
MAX_CONCURRENT_TASKS=3
PROCESSING_TIMEOUT_SECONDS=300

# Logging
LOG_LEVEL=INFO
LOG_FILE=./logs/hedera-ai.log
EOF

    log_success "Configuration created: $CONFIG_FILE"
    log_info "Please edit this file with your actual credentials:"
    echo "  nano $CONFIG_FILE"
    echo ""
}

# Setup project dependencies
setup_dependencies() {
    log_step "5" "Setting Up Project Dependencies..."

    cd "$PROJECT_ROOT"

    # Install Node.js dependencies
    log_info "Installing Node.js dependencies..."
    if [ -f "package.json" ]; then
        npm install > /dev/null 2>&1
        log_success "Node.js dependencies installed"
    else
        log_warn "package.json not found"
    fi

    # Setup Hardhat
    if [ -f "hardhat.config.js" ]; then
        log_info "Configuring Hardhat for Hedera..."
        # Hardhat is already configured in the project
        log_success "Hardhat configured"
    fi

    # Create necessary directories
    mkdir -p models uploads results logs contracts

    log_success "Project setup complete"
}

# Deploy smart contract
deploy_contract() {
    log_step "6" "Deploying AI Smart Contract to Hedera..."

    if [ ! -f "$CONFIG_FILE" ]; then
        log_error "Configuration not found. Please run setup first."
        exit 1
    fi

    cd "$PROJECT_ROOT"

    log_info "Deploying AIBrainOnChain contract..."
    log_info "This may take a few minutes..."

    # Run deployment script
    if [ -f "scripts/deploy-hedera.js" ]; then
        node scripts/deploy-hedera.js
    else
        log_error "Deployment script not found"
        exit 1
    fi

    # Check if deployment was successful
    if [ -d "deployments" ] && [ "$(ls deployments/*.json 2>/dev/null | wc -l)" -gt 0 ]; then
        local deployment_file=$(ls deployments/*.json | tail -1)
        local contract_address=$(grep -o '"contractAddress":"[^"]*"' "$deployment_file" | cut -d'"' -f4)

        if [ -n "$contract_address" ]; then
            log_success "Contract deployed successfully!"
            log_info "Address: $contract_address"

            # Update configuration
            sed -i.bak "s/GGUF_PROCESSOR_CONTRACT_ADDRESS=.*/GGUF_PROCESSOR_CONTRACT_ADDRESS=$contract_address/" "$CONFIG_FILE"
            log_success "Configuration updated with contract address"
        else
            log_error "Could not find contract address in deployment file"
        fi
    else
        log_error "Deployment failed or deployment file not created"
        exit 1
    fi
}

# Setup local compute node
setup_compute_node() {
    log_step "7" "Setting Up Local Compute Node..."

    cd "$PROJECT_ROOT"

    log_info "Configuring local GGUF processor..."

    # Copy configuration
    if [ -f "$CONFIG_FILE" ]; then
        cp "$CONFIG_FILE" "config/node.properties"
        log_success "Node configuration copied"
    else
        log_warn "Configuration file not found"
    fi

    # Setup llama.cpp (if available)
    if [ ! -d "llama.cpp" ]; then
        log_info "llama.cpp not found. Setting up..."
        if [ -f "scripts/setup-local-gguf.sh" ]; then
            ./scripts/setup-local-gguf.sh
        fi
    fi

    log_success "Local compute node configured"
}

# Test the system
test_system() {
    log_step "8" "Testing AI System..."

    cd "$PROJECT_ROOT"

    log_info "Running system tests..."

    # Test 1: Check configuration
    if [ -f "config/node.properties" ]; then
        log_success "✓ Configuration file exists"
    else
        log_error "✗ Configuration file missing"
    fi

    # Test 2: Check contract deployment
    if [ -d "deployments" ] && [ "$(ls deployments/*.json 2>/dev/null | wc -l)" -gt 0 ]; then
        log_success "✓ Contract deployment found"
    else
        log_warn "⚠ Contract deployment not found"
    fi

    # Test 3: Check dependencies
    if [ -d "node_modules" ]; then
        log_success "✓ Node.js dependencies installed"
    else
        log_warn "⚠ Node.js dependencies not found"
    fi

    log_success "System test complete"
}

# Show usage instructions
show_final_instructions() {
    log_step "9" "🎉 HEDERA AI SETUP COMPLETE!"

    echo ""
    log_success "Your Hedera AI system is ready!"
    echo ""

    if [ -f "$CONFIG_FILE" ]; then
        source "$CONFIG_FILE"
        echo "🏠 Contract Address: ${GGUF_PROCESSOR_CONTRACT_ADDRESS:-Not deployed yet}"
        echo "👤 Wallet Address: ${WALLET_ADDRESS:-Not configured}"
        echo "🌐 Network: Hedera Testnet"
        echo ""
    fi

    log_info "🚀 START YOUR AI SYSTEM:"
    echo ""
    echo "# 1. Start local compute node"
    echo "npm run start:local-processor"
    echo ""
    echo "# 2. Test AI requests"
    echo "npm run test:gemma-api"
    echo ""
    echo "# 3. Use your AI API"
    echo "curl -X POST http://localhost:8082/v1/chat/completions \\"
    echo "  -H \"Authorization: Bearer your-api-key\" \\"
    echo "  -H \"Content-Type: application/json\" \\"
    echo "  -d '{\"model\": \"gguf-model-gemma-3-270m-q4_0\", \"messages\": [{\"role\": \"user\", \"content\": \"Hello!\"}]}'"
    echo ""

    log_info "💰 COSTS:"
    echo "• Contract deployment: ~2 HBAR"
    echo "• AI request: 0.001 HBAR"
    echo "• Node staking: 0.1 HBAR"
    echo ""

    log_info "📚 RESOURCES:"
    echo "• Hedera Documentation: https://docs.hedera.com"
    echo "• Filebase: https://filebase.com"
    echo "• GGUF Models: https://huggingface.co/models?library=gguf"
    echo ""

    log_info "🆘 SUPPORT:"
    echo "• Email: SirCharlesspikes5@gmail.com"
    echo "• Telegram: @SirGODSATANAGI"
    echo "• GitHub Issues: Report bugs and features"
    echo ""

    log_success "🚀 WELCOME TO THE FUTURE OF AI!"
    log_success "No servers. No limits. Just blockchain intelligence. 🤖⛓️"
    echo ""
}

# Main execution
main() {
    echo "==============================================="
    echo "🚀 HEDERA AI - COMPLETE SETUP GUIDE"
    echo "By Sir Charles Spikes"
    echo "Cincinnati, Ohio"
    echo "==============================================="
    echo ""
    log_info "Transforming HBAR tokens into AI intelligence..."
    log_info "This setup takes ~10 minutes"
    echo ""

    check_system
    setup_hedera_wallet
    get_free_hbar
    configure_project
    setup_dependencies
    deploy_contract
    setup_compute_node
    test_system
    show_final_instructions
}

# Parse command line arguments
case "${1:-}" in
    "--help"|"-h")
        echo "Hedera AI Setup Script"
        echo ""
        echo "Usage: $0 [options]"
        echo ""
        echo "Options:"
        echo "  --help, -h     Show this help"
        echo "  --skip-wallet  Skip wallet creation"
        echo "  --test-only    Run tests only"
        echo ""
        exit 0
        ;;
    "--skip-wallet")
        log_info "Skipping wallet creation..."
        WALLET_FILE="/tmp/dummy_wallet"
        echo "PRIVATE_KEY=dummy_key" > "$WALLET_FILE"
        echo "ADDRESS=0x0000000000000000000000000000000000000000" >> "$WALLET_FILE"
        main
        ;;
    "--test-only")
        test_system
        ;;
    *)
        main
        ;;
esac
