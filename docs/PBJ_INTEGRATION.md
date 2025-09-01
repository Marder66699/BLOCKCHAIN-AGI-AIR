# PBJ (Protobuf Java Library) Integration Guide

**Author:** Sir Charles Spikes  
**Contact:** SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI

## Overview

This project integrates [Hashgraph's PBJ (Protobuf Java Library)](https://github.com/hashgraph/pbj) for high-performance protocol buffer handling in our AI blockchain platform.

## Why PBJ?

PBJ provides several critical benefits for blockchain applications:

1. **Deterministic Binary Encoding** - Essential for blockchain consensus
2. **Performance Optimized** - Faster than standard protoc
3. **Minimal Garbage Generation** - Better for long-running nodes
4. **Stable hashCode() and equals()** - Required for blockchain data structures
5. **GRPC Support via Helidon** - Perfect for node-to-node communication

## Architecture

```
┌─────────────────────────────────────────────────┐
│              Smart Contracts (Solidity)          │
└─────────────────────┬───────────────────────────┘
                      │ Web3/Ethereum RPC
┌─────────────────────┴───────────────────────────┐
│           AI Blockchain Node (Java)              │
│  ┌──────────────────────────────────────────┐   │
│  │     PBJ Protocol Buffers Layer           │   │
│  │  • Deterministic serialization           │   │
│  │  • Efficient parsing                     │   │
│  │  • Type-safe message handling            │   │
│  └──────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────┐   │
│  │     GRPC Service Layer (Helidon)         │   │
│  │  • Node communication                    │   │
│  │  • AI service requests/responses         │   │
│  └──────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────┐   │
│  │     AI Model Processors                  │   │
│  │  • Docker Model Runner integration       │   │
│  │  • Model execution                       │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

## Building with PBJ

### 1. Install Prerequisites
```bash
# Java 17+
java -version

# Gradle
./gradlew --version
```

### 2. Generate Protocol Buffer Classes
```bash
# Generate PBJ classes from .proto files
./gradlew generatePbj

# Or use the alias
./gradlew generateProto
```

### 3. Build the Project
```bash
# Full build including proto generation
./gradlew build
```

## Protocol Buffer Definitions

Our protocol buffers define:

- **AIModelMetadata** - AI model information
- **AIServiceRequest/Response** - Service invocation messages
- **AITransaction** - Blockchain transaction data
- **NodeMessage** - Inter-node communication
- **GRPC Services** - RPC definitions

## Using PBJ in Code

### Creating Messages
```java
// PBJ uses builder pattern with type-safe methods
AIServiceRequest request = AIServiceRequest.newBuilder()
    .requestId(UUID.randomUUID().toString())
    .serviceId("gpt-4-turbo")
    .requesterAddress(userAddress)
    .textInput(TextInput.newBuilder()
        .text("Hello AI!")
        .language("en")
        .maxTokens(100)
        .build())
    .putParameters("temperature", "0.7")
    .timestamp(Instant.now())
    .build();
```

### Efficient Serialization
```java
// Serialize to bytes (deterministic)
Bytes serialized = request.toBytes();

// Parse from bytes
AIServiceRequest parsed = AIServiceRequest.parseFrom(serialized);
```

### Null-Safe Access
```java
// PBJ returns null for missing fields (not defaults)
if (response.hasTextOutput()) {
    TextOutput output = response.textOutputOrThrow();
    // Process output
}

// Or use default value
String error = response.errorMessageOrElse("No error");
```

## GRPC Service Implementation

```java
@Override
public AIServiceResponse submitRequest(AIServiceRequest request) {
    // PBJ messages work seamlessly with Helidon GRPC
    return serviceHandler.processRequest(request);
}
```

## Performance Benefits

PBJ provides:
- **2-3x faster parsing** than standard protoc
- **50% less garbage generation**
- **Deterministic output** for consensus
- **Zero-copy operations** where possible

## Docker Model Runner Integration

PBJ messages integrate with Docker Model Runner:

```java
// Convert PBJ message to Docker model input
String modelInput = request.textInput().text();
String result = dockerModelRunner.run("ai/smollm2", modelInput);

// Create PBJ response
TextOutput output = TextOutput.newBuilder()
    .text(result)
    .build();
```

## Best Practices

1. **Always use builders** for creating messages
2. **Check hasField()** before accessing optional fields
3. **Use orElse() methods** for defaults
4. **Leverage deterministic serialization** for hashing
5. **Keep proto definitions simple** and well-documented

## Troubleshooting

### Common Issues

1. **Proto compilation fails**
   ```bash
   ./gradlew clean generatePbj
   ```

2. **Version conflicts**
   - Ensure PBJ version matches in build.gradle
   - Check Helidon compatibility

3. **Missing generated classes**
   - Run generatePbj task
   - Check src/main/java for generated code

## Future Enhancements

- WebSocket support via PBJ streaming
- Cross-chain message standards
- Advanced compression options
- Performance monitoring integration

---

For more information about PBJ, visit: https://github.com/hashgraph/pbj
