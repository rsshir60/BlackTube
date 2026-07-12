<div align="center">
  <picture>
    <img alt="BlackTube Logo" src="assets/icon_readme.png" width="150" height="150">
  </picture>

  <h1>BlackTube</h1>
  
  <p><b>A modern, privacy-respecting YouTube client with AI-powered video summaries.</b></p>
  
  <p>
    <a href="https://github.com/TeamNewPipe/NewPipe/blob/dev/LICENSE"><img alt="License: GPLv3" src="https://img.shields.io/badge/License-GPLv3-blue.svg"></a>
    <img alt="Kotlin Version" src="https://img.shields.io/badge/Kotlin-2.3.10-purple.svg?logo=kotlin">
    <!-- TODO: Add valid GitHub Stars badge once repository is published -->
    <img alt="GitHub Stars" src="https://img.shields.io/github/stars/rsshir60/BlackTube?style=social">
    <!-- TODO: Add Discord/Matrix link if applicable -->
  </p>
</div>

<details>
  <summary><b>Table of Contents</b></summary>

- [⚠️ Upstream Notice](#️-upstream-notice)
- [🌟 Features](#-features)
- [📸 Screenshots](#-screenshots)
- [🛡️ Privacy & Philosophy](#️-privacy--philosophy)
- [🚀 Installation](#-installation)
- [🛠️ Tech Stack](#️-tech-stack)
- [🔮 Roadmap](#-roadmap)
- [🤝 Contributing](#-contributing)
- [⚖️ License & Credits](#️-license--credits)
</details>

## ⚠️ Upstream Notice

**BlackTube is a fork of [NewPipe](https://github.com/TeamNewPipe/NewPipe).** 

All of the core extraction, streaming, and background playback capabilities are inherited directly from the incredible work done by the NewPipe contributors. We claim no originality for these upstream components. This project builds upon their foundation (under the GPLv3 license) to offer additional enhancements, most notably the integration of Bring-Your-Own-Key (BYOK) AI summaries.

## 🌟 Features

### 🤖 AI Summary (BYOK)
The standout feature of BlackTube is the integration of on-demand AI video summaries powered by Google's Gemini AI SDK. 
- **Bring Your Own Key (BYOK)**: In alignment with our privacy philosophy, BlackTube does not bundle any proprietary API keys. You must provide your own Gemini API key to activate the feature.
- **Cached Locally**: Summaries are cached directly on your device (`SharedPreferences`), saving your API quota and providing instant access on re-visits.
- **Markdown Rendering**: Rich formatting via `Markwon` for easy-to-read, structured output.

### 🎬 Playback
- Smooth playback and background audio support inherited from NewPipe, now powered by the modern **AndroidX Media3** library (replacing ExoPlayer).

### ⚙️ Customization
- Retains all of the powerful customization, themes, and settings you expect from a premium client.

## 📸 Screenshots

| AI Summary (No Key) | AI Summary (Generated) | Player View |
| :---: | :---: | :---: |
| <!-- TODO: add screenshot of state_no_key view --> | <!-- TODO: add screenshot of generated markdown summary --> | <!-- TODO: add screenshot of Media3 player --> |

## 🛡️ Privacy & Philosophy

BlackTube respects your privacy. Just like our upstream parent, there is **no tracking**, **no ads**, and **no analytics**. 

Our AI functionality adheres to a strict **Bring-Your-Own-Key (BYOK)** philosophy. We believe that integrating proprietary AI capabilities should never compromise an open-source client's integrity. No developer API keys are bundled with the app, ensuring that you maintain total control over your data and API usage.

## 🚀 Installation

### F-Droid
<!-- TODO: Add actual F-Droid repository link once available -->
*Our F-Droid repository is currently pending. Stay tuned!*

### GitHub Releases
You can download the latest pre-built APK directly from our [Releases page](https://github.com/rsshir60/BlackTube/releases). <!-- TODO: Update URL with actual repo URL -->

### Build from Source
To build BlackTube yourself, you'll need the Android SDK and JDK 17. 

```bash
git clone https://github.com/rsshir60/BlackTube.git
cd BlackTube
./gradlew assembleRelease
```
The compiled APK will be located in `app/build/outputs/apk/release/`.

## 🛠️ Tech Stack

BlackTube is built using modern Android development practices:

- **Language**: Kotlin 2.3.10
- **UI Framework**: Jetpack Compose (BOM 2024.06.00)
- **Media**: AndroidX Media3
- **AI**: Gemini Generative AI SDK (`com.google.ai.client.generativeai`)
- **Core Architecture**: RxJava, Room Database, OkHttp, Coil, Jsoup

## 🔮 Roadmap

- [ ] Complete transition of remaining legacy UI fragments to Jetpack Compose.
- [ ] Regular syncing with upstream NewPipe changes.
- [ ] Refine AI Summary prompt options (e.g., custom prompt lengths and styles).
- [ ] Add explicit screenshots and visual assets to documentation.

## 🤝 Contributing

Contributions are welcome! Currently, we do not have a dedicated `CONTRIBUTING.md` file. In the meantime, please feel free to open an Issue or a Pull Request for any bugs or enhancements. If you're contributing to core playback or extraction, consider submitting your patch directly to [NewPipe](https://github.com/TeamNewPipe/NewPipe) first.

## ⚖️ License & Credits

BlackTube is licensed under the **GNU General Public License v3.0 (GPLv3)**.

```text
Copyright (C) 2025 NewPipe e.V. and BlackTube Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
*Special thanks to the [NewPipe Team](https://github.com/TeamNewPipe/NewPipe) for the original codebase.*
