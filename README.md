# KisaraColorizer

> **Ultra-Fast, On-Device Neural Manga Colorization Sandbox with Real-Time Hardware Monitoring, Forensic Color Correction & Jetpack Compose UI**

**Package**: `com.raen.kisaracolorizer.debug`  
**Architecture**: Jetpack Compose + Material3, ONNX Runtime Mobile (FP16/FP32), NNAPI Hardware Acceleration (NPU/GPU), Injekt DI, Kotlin Coroutines & StateFlow.  
**Companion App**: Catmikku / Kisara (`eu.kanade.tachiyomi`).

---

## Features & Highlights

1. **On-Device Neural Inference**:
   - Runs `manga_colorization_v2_int8.onnx` (61.6MB end-to-end anime color generator) and `deoldify_artistic_quant.onnx`.
   - Supports both **ARM CPU (Multi-core NEON)** and **NNAPI Hardware Acceleration** (NPU/GPU driver routing).
   - Sanity tensor probe verification (`576x576`) before activating hardware acceleration.

2. **Forensic Color Correction & Dynamic Range Compensation**:
   - **Auto Paper Whitening**: Uses smooth Hermite interpolation (`smoothstep`) for pixels with luminance $\ge 0.88$, ensuring speech bubbles, panel borders, and white backgrounds are pure clean white (`RGB 255`) without any grayish-pink muddy tint.
   - **Dynamic Range Compensation (2.2x Chroma Expansion)**: Counteracts the 60% channel compression caused by ARM NEON FP16 activation underflow, restoring vibrant skin tones, clothes, and hair matching the PC baseline at **91.5% similarity**.
   - **Adaptive Scaling**: Automatically adapts compensation between NNAPI (`1.2x`) and ARM CPU (`2.2x`).

3. **15 Granular Tuning Filter Metrics & Presets**:
   - 1-tap presets: **Optimized**, **Colorful**, **Heavy Darkish**, **Brighter**, **Less Color**, **V20**, and **Custom**.
   - **Master Intensity Slider**: Configurable from `0.5x` up to **`30.0x`** across all pipeline modes.
   - **Expanded Limits**:
     - `skinHue` (0°..360° full color wheel)
     - `skinSat` (0%..300%)
     - `blueCap` (0%..150%)
     - `redFlush` (0.50x..3.00x)
     - `skinMinLum` (0%..100%)
     - `paperThresh` (30%..100%) & `paperFeather` (0%..50%)
     - `gamma` (10%..350%) & `contrast` (-100%..+100%)
     - `blackFloor` (-100..+100) & `lineThresh` (0%..90%)
     - `lineExp` (0.10x..6.00x) & `satMul` (0.00x..6.00x)
     - `colorMix` (0%..300%) & `clarity` (0%..400%)

4. **Live Hardware Resource Monitor (`ResourceBar`)**:
   - Ultra-slim inline monitor positioned directly above the engine logs.
   - Reads App CPU %, Mali/Adreno GPU busy % & clock MHz (via Magisk root `su`), Dalvik heap RAM MB, and includes a 1-tap "Free RAM" broom button.

5. **Draggable Multi-Screen Engine Logs (`LogConsoleWindow`)**:
   - Slidable from 140dp up to **72% of screen height** via drag handle pill or ⛶ maximize toggle.
   - Colorized event levels (`AI_STEP`, `SUCCESS`, `WARN`, `ERROR`, `INFO`).
   - Backed by persistent disk logging at `/data/user/0/com.raen.kisaracolorizer.debug/files/logs/colorizer.log`.

6. **3-Way Split Comparison & AI Inspection**:
   - Import PC reference images and compare interactively (`B&W vs Mobile`, `Mobile vs PC`, `B&W vs PC`).
   - Instant 1-tap "AI Pull" ADB command export to copy and inspect colorized outputs.

---

## Reference Image Benchmarks (`.aaa/`)

All reference ground truths and test outputs are archived in `.aaa/` for direct mathematical comparison:

| File | Purpose |
| :--- | :--- |
| `demo_manga_pc_001.png` | **PC Reference Ground Truth (Page 001)** — 1280×1727, FP32 ORT output |
| `demo_manga_pc_002.png` | **PC Reference Ground Truth (Page 002)** — 1280×1799, FP32 ORT output |
| `pc_colorized_001.png` | **Raw Model Tensor Prediction on PC** (576×768) |
| `pc_colorized_002_raw.png` | **Raw Model Tensor Prediction on PC** (576×768) |
| `input_page_001.png` | Original black-and-white test page |
| `mobile_colorized_001_initial.png` | Initial mobile output before color compensation (MAE 44.84) |
| `test_solved_color_001.png` | Compensated mobile output (MAE 21.74, 91.5% similarity) |
| `colorizer_bug_findings.md` | Investigation report on ARM NEON FP16 precision loss |
| `session_colorizer_investigation_2026_09_02.md` | Forensic mathematical analysis comparing PC vs Mobile |
| `context_snapshot.md` | Dedicated session snapshot and architecture state |

---

## Quick ADB Commands

### Inspect Engine Logs Live
```bash
adb shell "su -c 'cat /data/user/0/com.raen.kisaracolorizer.debug/files/logs/colorizer.log | tail -n 50'"
```

### Pull Latest Colorized Output to PC
```bash
adb shell "su -c 'cp /data/user/0/com.raen.kisaracolorizer.debug/files/debug/latest_colorized.png /sdcard/Download/ && chmod 666 /sdcard/Download/latest_colorized.png'"
adb pull /sdcard/Download/latest_colorized.png ./latest_colorized.png
```

### Free RAM On-Device
```bash
adb shell am broadcast -a com.raen.kisaracolorizer.FREE_RAM
```
