# Quantization Guide for AI Models

**Author:** Sir Charles Spikes  
**Contact:** SirCharlesspikes5@gmail.com | Telegram: @SirGODSATANAGI

## Overview

This guide explains the quantization methods available in our AI on Blockchain platform, helping you choose the optimal quantization for your use case.

## Quick Comparison: Q4_0 vs Q8_0

| Criterion | Q4_0 (4-bit) | Q8_0 (8-bit) |
|-----------|--------------|--------------|
| **Model Size** | ~240 MB | ~290 MB |
| **Memory Usage** | Lower, suited for limited RAM | Higher, needs more memory |
| **Inference Speed** | Faster, lighter on CPU | Slightly slower, heavier |
| **Output Quality** | High (QAT helps) | Slightly better detail |
| **Best For** | Lightweight or mobile use | Highest accuracy needs |

## Detailed Quantization Methods

### Legacy Quantization (Q4_0, Q4_1, Q5_0, Q5_1, Q8_0)

**Q4_0 - 4-bit Quantization**
- **Size Reduction:** ~4x smaller than F16
- **Use Cases:** 
  - Mobile devices
  - Edge computing
  - Consumer GPUs with limited VRAM
  - CPU-only inference
- **Performance:** Fastest inference, minimal memory footprint
- **Quality:** Good for most applications, especially with QAT (Quantization-Aware Training)

**Q8_0 - 8-bit Quantization**
- **Size Reduction:** ~2x smaller than F16
- **Use Cases:**
  - Professional applications requiring high accuracy
  - Research and development
  - Systems with ample memory
  - When output quality is paramount
- **Performance:** Slower than Q4_0 but still efficient
- **Quality:** Near-lossless compared to F16

### K-Quantization (Modern Approach)

K-quants allocate bits more intelligently across layers:

**Q4_K_M - 4-bit K-Quantization Medium**
- **Size:** ~4.85 bits per weight
- **Memory:** ~30% larger than Q4_0
- **Quality:** Better than Q4_0 with minimal size increase
- **Speed:** Similar to Q4_0 on modern hardware
- **Recommendation:** Best balanced choice for most users

**Q5_K_M - 5-bit K-Quantization Medium**
- **Size:** ~5.43 bits per weight
- **Memory:** Between Q4 and Q8
- **Quality:** Excellent, approaches Q8_0
- **Speed:** Good on modern CPUs/GPUs
- **Recommendation:** When you have some headroom

### I-Quantization (State-of-the-Art)

I-quants use advanced techniques with lookup tables:

**IQ3_M - 3-bit I-Quantization Medium**
- **Size:** ~3.7 bits per weight
- **Memory:** Very efficient
- **Quality:** Surprisingly good for size
- **Speed:** Slower on older hardware
- **Recommendation:** For modern hardware with compute headroom

## Hardware Recommendations

### For Different Model Sizes

**7B Parameter Models (e.g., Llama 2 7B)**
| Hardware | Recommended Quant | Reasoning |
|----------|-------------------|-----------|
| 8GB VRAM | Q4_K_M | Fits comfortably with context |
| 16GB VRAM | Q5_K_M or Q6_K | Better quality, still fits |
| CPU only | Q4_0 | Fastest CPU inference |

**70B Parameter Models (e.g., Llama 2 70B)**
| Hardware | Recommended Quant | Reasoning |
|----------|-------------------|-----------|
| 24GB VRAM | IQ3_M with offloading | Maximum compression |
| 48GB VRAM | Q4_K_M | Good balance |
| Multi-GPU | Q5_K_M | Quality with distribution |

### CPU vs GPU Considerations

**CPU Inference:**
- Prefer legacy quants (Q4_0) or K-quants
- Avoid I-quants on older CPUs
- Memory bandwidth is often the bottleneck

**GPU Inference:**
- Modern GPUs handle all quant types well
- I-quants shine with high compute capability
- Offload as many layers as VRAM allows

## Quantization Decision Tree

```
Start
├─ Limited Memory (<8GB)?
│  ├─ Yes → Q4_0 or Q3_K_M
│  └─ No → Continue
├─ Need Best Quality?
│  ├─ Yes → Q8_0 or Q6_K
│  └─ No → Continue
├─ Modern Hardware?
│  ├─ Yes → Q4_K_M or IQ4_XS
│  └─ No → Q4_0 or Q4_K_S
└─ Balanced Choice → Q4_K_M
```

## Importance Matrix

Always use importance matrix (imatrix) when available:
- Improves quality for free
- No performance penalty
- Especially important for <4-bit quants

## Practical Examples

### Example 1: Laptop with 8GB RAM, No GPU
```bash
# Best choice: Q4_0 with importance matrix
./quantize model.gguf model-q4_0.gguf q4_0 --imatrix imatrix.dat
```

### Example 2: Gaming PC with RTX 3080 (10GB VRAM)
```bash
# Best choice: Q4_K_M for 7B models
./quantize model.gguf model-q4_k_m.gguf q4_k_m --imatrix imatrix.dat

# For 70B models: IQ3_M with partial offloading
./quantize model.gguf model-iq3_m.gguf iq3_m --imatrix imatrix.dat
```

### Example 3: Server with Dual A100 (80GB VRAM each)
```bash
# Best choice: Q6_K or even Q8_0 for maximum quality
./quantize model.gguf model-q6_k.gguf q6_k --imatrix imatrix.dat
```

## Performance Benchmarks

### Speed Comparison (tokens/second)
| Model Size | Q4_0 | Q4_K_M | Q8_0 | Hardware |
|------------|------|--------|------|----------|
| 7B | 45 | 42 | 35 | RTX 3070 |
| 7B | 12 | 11 | 8 | CPU (i7-10700K) |
| 13B | 28 | 26 | 20 | RTX 3080 |
| 70B | 8 | 7 | 5 | RTX 4090 |

### Quality Comparison (Perplexity - Lower is Better)
| Model | F16 | Q8_0 | Q5_K_M | Q4_K_M | Q4_0 |
|-------|-----|------|--------|--------|------|
| Llama 2 7B | 5.80 | 5.82 | 5.85 | 5.89 | 5.95 |
| Difference | - | +0.3% | +0.9% | +1.6% | +2.6% |

## Integration with AI Blockchain

Our platform automatically selects optimal quantization:

```java
// In QuantizedModelManager.java
QuantizationType recommended = quantizedModelManager.recommendQuantization(
    modelSize,           // Parameter count
    availableVRAM,       // Available memory
    preferQuality        // Quality vs speed preference
);
```

Smart contract tracks quantization metrics:

```solidity
// In QuantizedModelRegistry.sol
function getCompressionRatio(QuantizationType _quantType) 
    returns (uint256 ratio);
```

## Best Practices

1. **Always benchmark** on your target hardware
2. **Start with K-quants** (Q4_K_M) as baseline
3. **Use importance matrix** for all quantizations
4. **Monitor perplexity** to ensure quality
5. **Consider use case** - chat vs code generation
6. **Test edge cases** in your domain

## Troubleshooting

### Model runs slowly
- Check if using I-quants on old hardware
- Try legacy quants (Q4_0)
- Reduce context size
- Enable GPU offloading

### Out of memory
- Use lower bit quantization
- Enable mmap (memory mapping)
- Reduce batch size
- Use partial offloading

### Poor quality output
- Try higher bit quantization
- Ensure importance matrix was used
- Check if model was properly converted
- Verify quantization settings

---

For more details on implementation, see the source code in `QuantizedModelManager.java` and `QuantizedModelRegistry.sol`.
