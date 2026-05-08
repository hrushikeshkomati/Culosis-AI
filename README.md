# 🫁 Culosis AI

> **AI-Powered Tuberculosis Detection from Chest X-rays on Android**
> 
> An academic research project leveraging TensorFlow and mobile deep learning to detect tuberculosis from chest X-ray images, accessible directly on Android devices.

[![Android](https://img.shields.io/badge/Android-≥8.0-green?logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-purple?logo=kotlin)](https://kotlinlang.org/)
[![TensorFlow](https://img.shields.io/badge/TensorFlow-Lite-orange?logo=tensorflow)](https://www.tensorflow.org/lite)
[![Research](https://img.shields.io/badge/Status-Research-blue)]()
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

---

## 📋 Table of Contents

- [About](#about)
- [Features](#features)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Installation & Setup](#installation--setup)
- [Usage](#usage)
- [Architecture](#architecture)
- [Model Details](#model-details)
- [Contributing](#contributing)
- [Disclaimer](#disclaimer)
- [License](#license)

---

## About

**Culosis AI** is an Android application that detects tuberculosis from chest X-ray images using a fine-tuned deep learning model. This project bridges the gap between AI research and mobile accessibility, enabling TB screening in resource-limited settings without requiring cloud infrastructure.

Designed as an **academic research initiative**, this project demonstrates:
- Mobile deployment of medical imaging AI models
- Optimization of TensorFlow models for on-device inference
- GradCAM visualization for model interpretability
- Real-world healthcare AI applications

> ⚠️ **Research Notice**: This is a research-stage project. It is **NOT** intended for clinical diagnosis and should only be used for educational purposes and research validation.

---

## ✨ Features

- 📱 **On-Device Inference** - All processing happens locally; no cloud dependency
- 🔍 **Chest X-ray Classification** - Binary classification: TB positive/negative
- 🧠 **GradCAM Visualization** - Visual explanations of model predictions
- ⚡ **Optimized TensorFlow Lite Models** - Quantized for mobile efficiency
- 🎨 **Intuitive UI** - Easy image capture and results display
- 🔒 **Privacy-First** - Patient data never leaves the device

---

## 🏗️ Project Structure

```
Culosis-AI/
├── .gitignore
├── build.gradle.kts                    # Root Gradle build config
├── settings.gradle.kts                 # Gradle settings
├── gradle.properties                   # Gradle properties
├── gradle/wrapper/
│   └── gradle-wrapper.properties
├── README.md
└── app/
    ├── build.gradle.kts                # App-level Gradle configuration
    └── src/main/
        ├── AndroidManifest.xml         # App permissions & components
        ├── assets/
        │   ├── model.tflite            # Main TB detection model (TensorFlow Lite)
        │   └── gradcam_model.tflite    # GradCAM visualization model
        ├── res/
        │   ├── drawable/
        │   │   └── ic_logo.png
        │   └── values/
        │       ├── strings.xml         # UI strings & translations
        │       └── themes.xml          # App theming
        └── java/com/sovereignai/tbdetect/
            ├── MainActivity.kt         # Main activity & UI
            └── ImageClassifier.kt      # TensorFlow model inference logic
```

**Key Files:**
- **`model.tflite`** - Pre-trained TensorFlow Lite model for TB detection (quantized for mobile)
- **`gradcam_model.tflite`** - Secondary model for generating visual explanations
- **`ImageClassifier.kt`** - Core inference engine; handles image preprocessing and model predictions
- **`MainActivity.kt`** - Android UI; image capture, result display, visualization

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin 100% |
| **Platform** | Android 8.0+ |
| **ML Framework** | TensorFlow Lite |
| **Build System** | Gradle |
| **Model Format** | TFLite (quantized) |
| **IDE** | Android Studio |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** (latest version recommended)
- **Android SDK 30+** (for compilation)
- **Kotlin 1.8+**
- **Gradle 7.0+**
- **JDK 11+**
- An Android device or emulator running Android 8.0 or higher

### Installation & Setup

#### 1️⃣ Clone the Repository

```bash
git clone https://github.com/hrushikeshkomati/Culosis-AI.git
cd Culosis-AI
```

#### 2️⃣ Open in Android Studio

- Open Android Studio
- Select **File → Open**
- Navigate to the `Culosis-AI` directory and click **Open**
- Wait for Gradle sync to complete

#### 3️⃣ Build the Project

```bash
./gradlew build
```

Or use Android Studio's **Build → Make Project**

#### 4️⃣ Run on Device/Emulator

```bash
./gradlew installDebug
```

Or click **Run → Run 'app'** in Android Studio

---

## 📱 Usage

### Basic Workflow

1. **Launch the App** - Open Culosis AI on your Android device
2. **Select/Capture X-ray** - Choose an image from gallery or capture via camera
3. **Process Image** - The app automatically preprocesses the image
4. **View Results**:
   - TB Detection Score (0-1 confidence)
   - Classification (Positive/Negative)
   - GradCAM heatmap showing key regions
5. **Share Results** (optional) - Export results for research purposes

### Example Usage Code

```kotlin
// Basic inference example
val classifier = ImageClassifier(context)
val bitmap = /* your X-ray image */
val result = classifier.classify(bitmap)

// result contains:
// - prediction: Float (0.0 to 1.0)
// - confidence: Float
// - gradcamMap: Bitmap (visual explanation)
```

---

## 🏛️ Architecture

```
┌─────────────────────────────────────────┐
│          Android UI (MainActivity)      │
│  - Image Selection/Capture              │
│  - Result Display                       │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│     ImageClassifier (Inference Engine)  │
│  - Preprocessing (resize, normalize)    │
│  - TensorFlow Lite Interpreter          │
│  - GradCAM Visualization                │
└──────────────┬──────────────────────────┘
               │
        ┌──────┴──────┐
        ▼             ▼
    ┌────────┐  ┌──────────────┐
    │model   │  │gradcam_model │
    │.tflite │  │.tflite       │
    └────────┘  └──────────────┘
```

---

## 🧠 Model Details

### Main Model (`model.tflite`)
- **Architecture**: Custom CNN (optimized for mobile)
- **Input**: 224×224 RGB chest X-ray image
- **Output**: Binary classification (TB/Non-TB)
- **Format**: TensorFlow Lite (quantized)
- **Optimization**: Post-training quantization for reduced latency

### GradCAM Model (`gradcam_model.tflite`)
- **Purpose**: Visual explanation of predictions
- **Output**: Heatmap showing important image regions
- **Use Case**: Model interpretability for research

### Preprocessing
- Resize input to 224×224
- Normalize pixel values (0-1 range)
- Convert to RGB if grayscale

---

## 🤝 Contributing

We welcome contributions from fellow researchers and developers! 

### How to Contribute

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/your-feature`
3. **Commit** changes: `git commit -m "Add your feature"`
4. **Push** to branch: `git push origin feature/your-feature`
5. **Open** a Pull Request with a clear description

### Contribution Ideas

- 📊 Improve model accuracy with new training data
- 🎨 Enhance UI/UX
- 🧪 Add unit tests
- 📖 Improve documentation
- 🌍 Add language translations
- 🔧 Optimize inference speed
- 📱 Support additional Android versions

---

## ⚠️ Disclaimer

**Important Research Notice:**

This project is a **research-stage prototype** and is **NOT** approved for clinical use. 

- ❌ **Do not use** for actual medical diagnosis
- ❌ **Do not use** as a substitute for professional medical advice
- ✅ **Use only** for educational purposes and research validation
- ✅ **Always consult** qualified radiologists and healthcare professionals

The accuracy and reliability of this model have not been clinically validated. Any deployment in a clinical setting must undergo rigorous validation and regulatory approval.

---

## 📜 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 📧 Questions or Feedback?

Feel free to open an **Issue** or reach out through GitHub discussions. We'd love to hear about your experience using this project!

---

**Made with ❤️ by the Culosis AI Research Team**
