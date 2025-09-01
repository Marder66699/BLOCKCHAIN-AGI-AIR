# 🤖 AI ON BLOCKCHAIN - Decentralized AI Processing Platform

> Run AI models locally, triggered by blockchain events, with ChatGPT-compatible API

**Author:** Sir Charles Spikes  
**Location:** Cincinnati, Ohio  
**Email:** SirCharlesspikes5@gmail.com  
**Telegram:** [@SirGODSATANAGI](https://t.me/SirGODSATANAGI)  
**GitHub:** [AI_ON_BLOCKCHAIN(SIRCHARLES_SPIKES)](https://github.com/sircharlesspikes/AI_ON_BLOCKCHAIN_SIRCHARLES_SPIKES)

---

## 🌟 What is AI on Blockchain?

This platform enables **decentralized AI processing** where:

1. **AI models** are stored on IPFS/Filebase (immutable, decentralized)
2. **Smart contracts** coordinate processing requests via blockchain events
3. **Local machines** run GGUF models using llama.cpp for optimal performance
4. **Results** are delivered through a **ChatGPT-compatible API**

### Key Features

✅ **Decentralized AI** - No central servers, processing happens locally  
✅ **Blockchain Coordination** - Smart contracts trigger local processing  
✅ **IPFS Model Storage** - Immutable, permanent model storage  
✅ **GGUF Optimization** - Q4_0/Q8_0 quantization for efficiency  
✅ **OpenAI Compatibility** - Drop-in replacement for ChatGPT API  
✅ **Edge Computing** - Distributed processing across multiple devices  
✅ **Token-Based Access** - Cryptocurrency payments for API usage  
✅ **C++ Performance** - Native llama.cpp execution  

---

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   User Request   │────│ Smart Contract  │────│ Blockchain Event │
│                 │    │                 │    │                 │
│ • API Key       │    │ • Store IPFS    │    │ • Processing    │
│ • Base URL      │    │ • Trigger Local │    │ • Request       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                        │                        │
         │                        │                        │
         ▼                        ▼                        ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Filebase/IPFS  │────│ Local Processor │────│   GGUF Model    │
│                 │    │                 │    │                 │
│ • Model Storage │    │ • Event Listener│    │ • llama.cpp     │
│ • CDN Delivery  │    │ • Download Model│    │ • Inference     │
│ • Global Access │    │ • Process Prompt│    │ • Generate      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites

- **Java 17+** (for local processing)
- **Node.js 14+** (for deployment scripts)
- **Git** (for cloning repository)
- **Filebase Account** (for decentralized storage)
- **Ethereum Wallet** (for blockchain interaction)

### 1. Clone and Setup

```bash
# Clone the repository
git clone https://github.com/sircharlesspikes/AI_ON_BLOCKCHAIN_SIRCHARLES_SPIKES.git
cd AI_ON_BLOCKCHAIN_SIRCHARLES_SPIKES

# Install dependencies
npm install

# Setup local GGUF processing environment
npm run setup:local-gguf
```

### 2. Configure Environment

```bash
# Create environment configuration
cp .env.example .env

# Edit with your credentials
nano .env
```

**Required Environment Variables:**
```bash
# Filebase (for decentralized storage)
FILEBASE_API_KEY=your_filebase_key
FILEBASE_API_SECRET=your_filebase_secret

# Ethereum (for blockchain interaction)
PRIVATE_KEY=your_ethereum_private_key

# Optional: IPFS Configuration
IPFS_PROJECT_ID=your_infura_project_id
IPFS_PROJECT_SECRET=your_infura_secret
```

### 3. Deploy Smart Contracts

```bash
# Start local blockchain
npm run node

# Deploy contracts in new terminal
npm run deploy:localhost
```

### 4. Upload Your First Model

```bash
# Download a GGUF model (example)
wget https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF/resolve/main/llama-2-7b-chat.Q4_0.gguf

# Upload to Filebase and register
npm run upload:model llama-2-7b-chat.Q4_0.gguf llama llama2 Q4_0
```

### 5. Start Local Processing

```bash
# Start the local GGUF processor
npm run start:local-processor
```

### 6. Test Your AI API

```bash
# Test the ChatGPT-compatible API
npm run test:gemma-api

# Or use cURL directly
curl -X POST http://localhost:8082/v1/chat/completions \
  -H "Authorization: Bearer your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gguf-model-llama2-7b-q4_0",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'
```

---

## 📊 Performance & Specifications

### Model Support
| Model Type | Quantization | Size | Performance |
|------------|--------------|------|-------------|
| **Llama 2** | Q4_0 | ~4GB | 25-30 tokens/sec |
| **Mistral** | Q4_0 | ~4GB | 20-25 tokens/sec |
| **Gemma** | Q4_0 | ~2GB | 30-35 tokens/sec |
| **CodeLlama** | Q4_0 | ~4GB | 20-25 tokens/sec |

### System Requirements
| Component | Minimum | Recommended |
|-----------|---------|-------------|
| **RAM** | 8GB | 16GB+ |
| **CPU** | 4 cores | 8+ cores |
| **GPU** | Optional | RTX 3060+ |
| **Storage** | 10GB | 50GB+ |
| **Network** | 10 Mbps | 100+ Mbps |

### Quantization Options
- **Q4_0**: Best balance of quality and performance
- **Q8_0**: Highest quality, slower processing
- **IQ3_M**: Extreme compression, modern hardware
- **Q3_K_M**: Legacy compatible, balanced

---

## 🛠️ API Usage Examples

### JavaScript/Node.js

```javascript
import OpenAI from 'openai';

const client = new OpenAI({
  apiKey: 'your-api-key',
  baseURL: 'http://localhost:8082/v1'
});

// Chat completion
const response = await client.chat.completions.create({
  model: 'gguf-model-llama2-7b-q4_0',
  messages: [
    { role: 'system', content: 'You are a helpful assistant.' },
    { role: 'user', content: 'Explain quantum computing.' }
  ],
  max_tokens: 500,
  temperature: 0.7
});

console.log(response.choices[0].message.content);
```

### Python

```python
import openai

client = openai.OpenAI(
    api_key="your-api-key",
    base_url="http://localhost:8082/v1"
)

response = client.chat.completions.create(
    model="gguf-model-llama2-7b-q4_0",
    messages=[{"role": "user", "content": "Hello!"}],
    max_tokens=100
)

print(response.choices[0].message.content)
```

### cURL

```bash
# Chat completion
curl -X POST http://localhost:8082/v1/chat/completions \
  -H "Authorization: Bearer your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gguf-model-llama2-7b-q4_0",
    "messages": [{"role": "user", "content": "Hello!"}],
    "max_tokens": 100,
    "temperature": 0.8
  }'

# List available models
curl http://localhost:8082/v1/models

# Check health
curl http://localhost:8082/health
```

---

## 🔧 Advanced Configuration

### Filebase Setup

1. **Create Filebase Account**: https://filebase.com
2. **Create Bucket**: `llmq4` (or your preferred name)
3. **Get API Credentials**: Dashboard → Access Keys
4. **Configure Environment**:

```bash
export FILEBASE_API_KEY="your_key_here"
export FILEBASE_API_SECRET="your_secret_here"
```

### Model Management

```bash
# List available models
curl http://localhost:8082/v1/models

# Get model information
curl http://localhost:8082/v1/models/gguf-model-llama2-7b-q4_0

# Check processing status
npm run check:status
```

### Performance Tuning

```javascript
// Adjust inference parameters
const response = await client.chat.completions.create({
  model: 'gguf-model-llama2-7b-q4_0',
  messages: [{ role: 'user', content: 'Hello!' }],
  temperature: 0.1,      // Lower for more deterministic
  top_p: 0.9,           // Nucleus sampling
  max_tokens: 256,      // Response length
  presence_penalty: 0.1, // Reduce repetition
  frequency_penalty: 0.1 // Encourage diversity
});
```

---

## 🔐 Security & Privacy

### Data Protection
- **Local Processing**: AI runs on your hardware, data never leaves
- **IPFS Encryption**: Models stored with content addressing
- **API Key Authentication**: Secure access control
- **Blockchain Transparency**: All transactions verifiable

### API Key Management
```javascript
// Generate new API key
const apiKey = await generateApiKey(userAddress, 'premium');

// Validate API key
const isValid = await validateApiKey(apiKey);

// Track usage
await trackUsage(apiKey, requestCount);
```

---

## 🌐 Network Architecture

### Single Node Setup
```
Internet ──► Your Machine ──► GGUF Model ──► API Response
                    │
                    └─► Filebase/IPFS ←─► Smart Contracts
```

### Multi-Node Network
```
Internet ──► Load Balancer ──► Node 1 ──► GGUF Models
                    │                    │
                    ├─► Node 2 ──►       └─► API Response
                    │                    │
                    └─► Node N ──►       └─► Blockchain Events
```

### Decentralized Architecture
- **No Single Point of Failure**
- **Geographic Distribution**
- **Automatic Load Balancing**
- **Redundant Model Storage**

---

## 📈 Use Cases & Applications

### 1. **Decentralized Chat Applications**
- Privacy-focused chatbots
- Local AI assistants
- Custom model deployments

### 2. **Edge AI Processing**
- IoT device intelligence
- Offline AI capabilities
- Resource-constrained environments

### 3. **Research & Development**
- Model testing and evaluation
- Custom fine-tuning workflows
- Comparative analysis

### 4. **Enterprise Solutions**
- Private AI deployments
- Regulatory compliance
- Custom model integration

---

## 🔧 Development & Contribution

### Build from Source

```bash
# Clone repository
git clone https://github.com/sircharlesspikes/AI_ON_BLOCKCHAIN_SIRCHARLES_SPIKES.git
cd AI_ON_BLOCKCHAIN_SIRCHARLES_SPIKES

# Install dependencies
npm install

# Build Java components
./gradlew build

# Run tests
npm test
```

### Project Structure

```
AI_ON_BLOCKCHAIN/
├── contracts/           # Solidity smart contracts
│   ├── LocalGGUFProcessor.sol
│   ├── AIModelNFT.sol
│   └── QuantizedModelRegistry.sol
├── src/main/java/       # Java backend services
│   ├── LocalGGUFProcessor.java
│   ├── OpenAICompatibleAPIServer.java
│   └── service/
├── src/main/cpp/        # C++ llama.cpp integration
├── scripts/            # Deployment & setup scripts
├── config/             # Configuration files
└── docs/               # Documentation
```

### Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

---

## 📚 Documentation

- **[Complete Hedera AI Guide](HEDERA_AI_COMPLETE_GUIDE.md)** - Full serverless AGI system
- **[Quick Start Guide](GEMMA_FILEBASE_QUICKSTART.md)** - Get started in 5 minutes
- **[Local GGUF Guide](README_LOCAL_GGUF.md)** - Complete local processing guide
- **[Quantization Guide](docs/QUANTIZATION_GUIDE.md)** - Q4_0 vs Q8_0 comparison
- **[PBJ Integration](docs/PBJ_INTEGRATION.md)** - Protocol buffer documentation
- **[API Reference](https://platform.openai.com/docs/api-reference)** - Compatible with OpenAI API

---

## 🤝 Community & Support

### Getting Help

- **GitHub Issues**: Bug reports and feature requests
- **Discussions**: General questions and community support
- **Telegram**: [@SirGODSATANAGI](https://t.me/SirGODSATANAGI)

### Community Resources

- **Discord Server**: [Join our community](https://discord.gg/ai-blockchain)
- **Twitter**: [@AI_Blockchain](https://twitter.com/AI_Blockchain)
- **Blog**: [Technical deep-dives and tutorials](https://ai-blockchain.dev/blog)

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **ggerganov** for [llama.cpp](https://github.com/ggerganov/llama.cpp)
- **Hedera** for [PBJ](https://github.com/hashgraph/pbj)
- **Filebase** for decentralized storage
- **OpenAI** for the API specification
- **Ethereum** for the blockchain foundation

---

## 🎯 Vision & Roadmap

### Current Status ✅
- Decentralized AI model storage (IPFS/Filebase)
- Blockchain-triggered local processing
- ChatGPT-compatible API interface
- GGUF model optimization
- Multi-node architecture support

### Upcoming Features 🚧
- **Federated Learning**: Collaborative model training
- **Cross-Chain Support**: Multi-blockchain compatibility
- **Model Marketplace**: Buy/sell AI models
- **Privacy-Preserving AI**: Zero-knowledge proofs
- **Mobile SDK**: iOS/Android integration

### Long-term Vision 🎯
- **Global AI Network**: Worldwide distributed processing
- **AI Sovereignty**: User-controlled AI infrastructure
- **Interoperability**: Universal AI model format
- **Sustainability**: Energy-efficient AI processing

---

<div align="center">

**Built with ❤️ by Sir Charles Spikes**

*"Democratizing AI through blockchain technology - one local inference at a time"*

🌟 **Star this repo** if you find it useful! 🌟

[📧 Email](mailto:SirCharlesspikes5@gmail.com) • [💬 Telegram](https://t.me/SirGODSATANAGI) • [🐙 GitHub](https://github.com/sircharlesspikes)

</div>