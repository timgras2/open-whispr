# OpenWhispr Technical Reference for AI Assistants

This document provides comprehensive technical details about the OpenWhispr project architecture for AI assistants working on the codebase.

## Project Overview

OpenWhispr is an Electron-based desktop dictation application (v1.0.12) that uses OpenAI Whisper for speech-to-text transcription. It supports both local (privacy-focused) and cloud (OpenAI API) processing modes, with optional AI-powered text cleanup via multiple providers.

## Quick Reference

```
npm run dev          # Start dev mode (Vite + Electron concurrently)
npm run dev:renderer # Vite dev server only (port 5174)
npm run dev:main     # Electron main process only
npm run build        # Production build (vite build + electron-builder)
npm run lint         # ESLint (runs in src/)
npm run pack         # Unsigned build for local testing
npm run clean        # Clean build dirs and dev database
```

No test framework is configured. Quality assurance relies on ESLint, TypeScript type checking, and manual testing.

## Architecture Overview

### Core Technologies
- **Frontend**: React 19, TypeScript, Tailwind CSS v4, Vite 6
- **Desktop Framework**: Electron 36 with context isolation
- **Database**: better-sqlite3 for local transcription history
- **UI Components**: shadcn/ui with Radix primitives
- **Speech Processing**: OpenAI Whisper (local Python bridge + cloud API)
- **Audio Processing**: FFmpeg (bundled via ffmpeg-static)
- **Local AI**: llama.cpp for on-device inference (Qwen, LLaMA, Mistral models)

### Key Architectural Decisions

1. **Dual Window Architecture**:
   - Main Window: Minimal overlay for dictation (draggable, always on top)
   - Control Panel: Full settings interface (normal window)
   - Both use same React codebase with URL-based routing (`src/main.jsx`)

2. **Process Separation**:
   - Main Process: Electron main (`main.js`), IPC handlers, database operations
   - Renderer Process: React app with context isolation
   - Preload Script (`preload.js`): Secure bridge between processes via `window.electronAPI`

3. **Audio Pipeline**:
   - MediaRecorder API → Blob → ArrayBuffer → Base64 → IPC → File → FFmpeg → Whisper
   - Automatic cleanup of temporary files after processing

4. **AI Provider Routing**:
   - Model ID string determines provider via `getModelProvider()` in `src/utils/languages.ts`
   - Pattern matching: `claude*` → Anthropic, `gemini*` → Gemini, `gpt*`/`o3`/`o4` → OpenAI, `qwen`/`llama`/`mistral` → Local
   - OpenAI supports both Responses API and Chat Completions with automatic fallback

## File Structure and Responsibilities

### Top-Level Files

- **main.js**: Electron main process entry point, initializes all managers
- **preload.js**: Exposes safe IPC methods to renderer via `window.electronAPI`
- **whisper_bridge.py**: Python script for local Whisper transcription (527 lines)
- **electron-builder.json**: Build configuration for DMG/NSIS/AppImage packaging
- **setup.js**: Initial setup script creating `.env` template
- **cleanup.js**: Dev cleanup script for build dirs and databases

### Source Code (`src/`)

All frontend code lives in `src/`. Vite config, ESLint config, and `index.html` are inside `src/`.

#### Components (`src/components/`)

- **App.jsx**: Main dictation panel UI with recording controls and hover states
- **ControlPanel.tsx**: Settings interface and history management
- **OnboardingFlow.tsx**: 8-step first-time setup wizard
- **SettingsPage.tsx**: Comprehensive settings interface
- **AIModelSelectorEnhanced.tsx**: Advanced model selection with AI provider management
- **UnifiedModelPicker.tsx**: Unified interface for selecting AI models
- **WhisperModelPicker.tsx**: Whisper model selection and download UI
- **TitleBar.tsx**: Custom window title bar
- **WindowControls.tsx**: Window minimize/maximize/close controls
- **SettingsModal.tsx**: Modal settings dialog
- **ui/**: ~30 shadcn/ui components (buttons, cards, dialogs, dropdowns, inputs, selects, tabs, etc.)

#### Hooks (`src/hooks/`)

- **useAudioRecording.js**: MediaRecorder API wrapper with error handling
- **useClipboard.ts**: Clipboard operations hook
- **useDialogs.ts**: Electron dialog integration
- **useHotkey.js**: Hotkey state management
- **useLocalModels.ts**: Local model management and download state
- **useLocalStorage.ts**: Type-safe localStorage wrapper
- **usePermissions.ts**: System permission checks
- **usePython.ts**: Python installation state
- **useSettings.ts**: Application settings management
- **useWhisper.ts**: Whisper model management
- **useWindowDrag.js**: Window drag behavior

#### Services (`src/services/`)

- **BaseReasoningService.ts**: Abstract base class with shared prompt construction and token calculation
- **ReasoningService.ts**: Main AI processing service routing to OpenAI/Anthropic/Gemini/Local
- **LocalReasoningService.ts**: Local model inference via llama.cpp
- **localReasoningBridge.js**: IPC bridge for local reasoning calls

#### Configuration (`src/config/`)

- **aiProvidersConfig.ts**: AI mode/provider/model definitions for Cloud and Local AI dropdowns
- **constants.ts**: API endpoints, token limits, cache config, retry strategies, URL normalization
- **InferenceConfig.ts**: Inference parameter configuration

#### Helpers (`src/helpers/`) — Main Process Only

- **audioManager.js**: Audio device enumeration and stream management
- **clipboard.js**: Cross-platform clipboard operations with AppleScript fallback
- **database.js**: SQLite operations for transcription history
- **debugLogger.js**: Debug logging system with file output
- **devServerManager.js**: Vite dev server integration for development
- **dragManager.js**: Window dragging functionality
- **environment.js**: Environment variable and API key management (`.env` persistence)
- **globeKeyManager.js**: macOS Globe key listener integration
- **hotkeyManager.js**: Global hotkey registration and management
- **ipcHandlers.js**: Centralized IPC handler registration (all main-process operations)
- **llamaCppInstaller.js** / **llamaCppInstaller.ts**: llama.cpp runtime download and management
- **menuManager.js**: Application menu configuration
- **ModelManager.ts**: Model registry and download management
- **modelManagerBridge.js**: IPC bridge for model operations
- **pythonInstaller.js**: Automatic Python installation for all platforms
- **tray.js**: System tray icon and menu
- **whisper.js**: Local Whisper integration and Python bridge invocation
- **windowConfig.js**: Centralized window configuration
- **windowManager.js**: Window creation, lifecycle, positioning, and focus management

#### Models (`src/models/`)

- **ModelRegistry.ts**: Singleton registry for local model providers (Qwen, Mistral, LLaMA)
- **modelRegistryData.json**: Model definitions with sizes, quantization levels, context lengths

#### Stores (`src/stores/`)

- **transcriptionStore.ts**: External store for transcription history using `useSyncExternalStore`

#### Utilities (`src/utils/`)

- **languages.ts**: 58 language definitions for Whisper + `getModelProvider()` function
- **constants.ts**: Shared constants
- **agentName.ts**: Agent name utilities
- **hotkeys.ts**: Hotkey definitions
- **retry.ts**: Retry strategies with exponential backoff (`withRetry`, `createApiRetryStrategy`)
- **formatBytes.ts**: Byte formatting utility
- **SecureCache.ts**: Secure in-memory cache with TTL and auto-cleanup
- **debugLoggerRenderer.js**: Client-side debug logging

#### Types (`src/types/`)

- **electron.ts**: TypeScript definitions for `window.electronAPI` interface

#### Other `src/` Files

- **index.html**: HTML template
- **main.jsx**: React entry point with routing (control panel vs dictation panel based on URL)
- **index.css**: Tailwind CSS v4 with light/dark theme
- **vite.config.mjs**: Vite config with React plugin and Tailwind
- **eslint.config.js**: ESLint flat config for JS/JSX
- **components.json**: shadcn/ui component configuration
- **utils.js**: Common utilities (e.g., `cn()` class name merger)
- **updater.js**: Auto-update handling with electron-updater

### Platform Resources (`resources/`)

- **mac/entitlements.mac.plist**: macOS app entitlements
- **linux/after-remove.sh**: Post-uninstall cleanup for Debian packages
- **nsis/cleanup-models.nsh**: Windows installer model cleanup
- **macos-globe-listener.swift**: Swift source for macOS Globe key detection

### Scripts (`scripts/`)

- **build-globe-listener.js**: Compiles the Swift Globe key listener binary (macOS only)
- **complete-uninstall.sh**: Full uninstall script

## Key Implementation Details

### 1. IPC Communication Pattern

All main-process operations go through `ipcHandlers.js`. To add a new IPC channel:

1. Add the handler in `src/helpers/ipcHandlers.js`
2. Expose the method in `preload.js` via `contextBridge.exposeInMainWorld`
3. Add the TypeScript definition in `src/types/electron.ts`
4. Call via `window.electronAPI.yourMethod()` from the renderer

Recent commits added IPC listener cleanup functions to prevent memory leaks — follow the same pattern for new listeners.

### 2. FFmpeg Integration

FFmpeg is bundled with the app and doesn't require system installation:
```javascript
// FFmpeg is unpacked from ASAR to app.asar.unpacked/node_modules/ffmpeg-static/
// Python bridge receives FFmpeg path via environment variables:
// FFMPEG_PATH, FFMPEG_EXECUTABLE, FFMPEG_BINARY
```

### 3. Audio Recording Flow

1. User presses hotkey → MediaRecorder starts
2. Audio chunks collected in array
3. User presses hotkey again → Recording stops
4. Blob created from chunks → Converted to ArrayBuffer
5. Sent via IPC as Base64 string (10MB size limit)
6. Main process writes to temporary WAV file
7. Whisper processes file → Result sent back via IPC
8. Temporary file deleted

### 4. Local Whisper Models

Models stored in `~/.cache/whisper/`:
- tiny: 39MB (fastest, lowest quality)
- base: 74MB (recommended balance)
- small: 244MB (better quality)
- medium: 769MB (high quality)
- large: 1.5GB (best quality)
- turbo: 809MB (fast with good quality)

### 5. Database Schema

```sql
CREATE TABLE transcriptions (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
  original_text TEXT NOT NULL,
  processed_text TEXT,
  is_processed BOOLEAN DEFAULT 0,
  processing_method TEXT DEFAULT 'none',
  agent_name TEXT,
  error TEXT
);
```

### 6. Settings Storage

Settings stored in localStorage with these keys:
- `whisperModel`: Selected Whisper model
- `useLocalWhisper`: Boolean for local vs cloud
- `openaiApiKey`: Encrypted API key
- `anthropicApiKey`: Encrypted API key
- `geminiApiKey`: Encrypted API key
- `language`: Selected language code
- `agentName`: User's custom agent name
- `reasoningModel`: Selected AI model for processing (defaults to `gpt-4o-mini`)
- `reasoningProvider`: AI provider (openai/anthropic/gemini/local)
- `cloudReasoningBaseUrl`: Custom OpenAI-compatible base URL
- `openAiEndpointPreference`: Cached API endpoint format preference per base URL
- `customPrompts`: JSON with custom agent/regular prompts
- `hotkey`: Custom hotkey configuration
- `hasCompletedOnboarding`: Onboarding completion flag

### 7. Language Support

58 languages supported (see `src/utils/languages.ts`):
- Each language has a two-letter code and label
- "auto" for automatic detection
- Passed to Whisper via `--language` parameter

### 8. Agent Naming System

- User names their agent during onboarding (step 6/8)
- Name stored in localStorage and database
- `BaseReasoningService.getReasoningPrompt()` detects agent name in text
- AI processes command and removes agent reference from output
- Supports custom prompt templates via localStorage `customPrompts` key

### 9. AI Provider Integration

#### Cloud Providers (configured in `src/config/aiProvidersConfig.ts`)

**OpenAI**:
- Models in UI picker: GPT-3.5 Turbo, GPT-4o Mini, GPT-4 Turbo
- Supports both Responses API (`/v1/responses`) and Chat Completions (`/v1/chat/completions`)
- Auto-detects endpoint format: tries Responses first, falls back to Chat Completions on 404/405
- Remembers working endpoint per base URL in localStorage
- Configurable base URL for OpenAI-compatible endpoints (e.g., Azure, local proxies)
- Temperature only set for older models (`gpt-4*`, `gpt-3*`)
- Default model: `gpt-4o-mini`
- Provider detection also supports `o1`/`o3`/`o4` reasoning model prefixes

**Anthropic**:
- Models in UI picker: Claude 3 Haiku, Claude 3 Sonnet, Claude 3 Opus
- Routes through IPC handler to main process to avoid CORS issues
- Main process makes direct API calls to `https://api.anthropic.com/v1/messages`
- API version: `2023-06-01`

**Google Gemini**:
- Direct API calls from renderer process (no CORS issues)
- Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`
- 2000 minimum token output for thinking models
- Handles `MAX_TOKENS` finish reason gracefully

#### Local AI

- Uses llama.cpp for on-device inference
- Models: Qwen 2.5 (0.5B-7B), Mistral 7B, Mixtral 8x7B, LLaMA 3.x (1B-8B)
- GGUF quantized format (Q4_K_M, Q5_K_M)
- `ModelRegistry` singleton manages provider registration and model lookup
- Routes through IPC to main process for inference

#### Retry and Caching

- All API calls use `withRetry()` from `src/utils/retry.ts`
- Default: 3 retries, 1s initial delay, 2x backoff, 10s max delay
- API keys cached in `SecureCache` with 1-hour TTL and auto-cleanup

### 10. OpenAI-Compatible Endpoint Support

The `ReasoningService` supports custom OpenAI-compatible base URLs:
- Configured via `cloudReasoningBaseUrl` in localStorage
- URL normalization strips common suffixes (`/chat/completions`, `/responses`, `/models`)
- Security: rejects non-HTTPS endpoints (except localhost for development)
- Endpoint preference persisted per base URL

### 11. Debug Mode

Enable with `--debug` flag or `OPENWHISPR_DEBUG=true`:
- Logs saved to platform-specific app data directory
- Comprehensive logging of audio pipeline, FFmpeg resolution, audio levels
- Complete reasoning pipeline debugging with stage-by-stage logging
- Renderer-side logging via `debugLoggerRenderer.js`

## Build System

### Vite Configuration (`src/vite.config.mjs`)

- React plugin with fast refresh
- Tailwind CSS v4 via `@tailwindcss/vite`
- Base path: `./` (relative for Electron `file://` protocol)
- Alias: `@` → `src/` directory
- Dev server port: 5174
- External modules: electron, fs, path, child_process, https, http, crypto, os, stream, util, zlib, tar, unzipper, @aws-sdk/client-s3

### electron-builder Configuration

- App ID: `com.herotools.openwispr`
- ASAR unpacking: whisper_bridge.py, ffmpeg-static, better-sqlite3
- macOS: DMG + ZIP, arm64 + x64, code signing (HeroTools Inc.), notarization
- Windows: NSIS + Portable EXE
- Linux: AppImage, DEB, RPM, TAR.GZ (Flatpak opt-in for local builds only)
- Auto-updates via GitHub releases (draft mode)

### Build Scripts

| Script | Purpose |
|--------|---------|
| `npm run dev` | Concurrent Vite + Electron dev mode |
| `npm run build` | Full production build |
| `npm run build:mac/win/linux` | Platform-specific builds |
| `npm run pack` | Unsigned build for local testing (`CSC_IDENTITY_AUTO_DISCOVERY=false`) |
| `npm run lint` | ESLint in `src/` directory |
| `npm run clean` | Remove build artifacts and dev database |
| `npm run compile:globe` | Build macOS Globe key listener (runs automatically as pre-hook) |

### CI/CD (`.github/workflows/`)

**build-and-notarize.yml** (push to main/develop, PRs):
- Node 20, npm ci
- Caches Electron downloads
- Platform-specific builds with artifact upload (7-day retention)
- macOS: certificate import for code signing

**release.yml** (version tags `v*.*.*`):
- Multi-platform builds
- Publishes to GitHub releases with `--publish always`

Secrets: `APPLE_CERTIFICATE_BASE64`, `APPLE_CERTIFICATE_PASSWORD`, `APPLE_API_KEY_BASE64`, `APPLE_API_KEY_ID`, `APPLE_API_ISSUER`, `APPLE_TEAM_ID`, `GH_TOKEN`

## Development Guidelines

### Adding New Features

1. **New IPC Channel**: Add handler in `ipcHandlers.js`, expose in `preload.js`, add types in `types/electron.ts`
2. **New Setting**: Update `useSettings.ts` and `SettingsPage.tsx`
3. **New UI Component**: Follow shadcn/ui patterns in `src/components/ui`
4. **New Manager**: Create in `src/helpers/`, initialize in `main.js`
5. **New AI Provider**: Add to `aiProvidersConfig.ts`, implement in `ReasoningService.ts`, update `getModelProvider()` in `languages.ts`

### Code Style and Conventions

- Use TypeScript for new React components and hooks
- JavaScript (CommonJS) for main process helpers
- Follow existing patterns in `src/helpers/` for backend managers
- Descriptive error messages for users
- Comprehensive debug logging via `debugLogger`
- Clean up resources (files, listeners, IPC handlers) — recent commits focus on preventing memory leaks
- Handle edge cases gracefully
- ESLint rules: `no-unused-vars` (ignores uppercase/underscore prefixed), react-hooks, react-refresh

### Common Issues and Solutions

1. **No Audio Detected**:
   - Check FFmpeg path resolution in debug logs
   - Verify microphone permissions
   - Check audio levels in debug logs

2. **Transcription Fails**:
   - Ensure Python/Whisper installed (auto-installer handles this)
   - Check temporary file creation
   - Verify FFmpeg is executable

3. **Clipboard Not Working**:
   - macOS: Check accessibility permissions
   - Uses AppleScript fallback on macOS

4. **Build Issues**:
   - Use `npm run pack` for unsigned builds
   - Code signing requires Apple Developer account
   - ASAR unpacking needed for FFmpeg/Python bridge
   - `afterSign.js` skips signing when `CSC_IDENTITY_AUTO_DISCOVERY=false`

5. **IPC Memory Leaks**:
   - Always return cleanup functions from IPC listeners
   - Follow the pattern in recent commits (b6b4571, 3d538b0)

### Platform-Specific Notes

**macOS**:
- Requires accessibility permissions for clipboard
- Uses AppleScript for reliable pasting
- Notarization needed for distribution
- Shows in dock (LSUIElement: false)
- Globe key listener compiled from Swift source

**Windows**:
- Python installer handles PATH automatically
- No special permissions needed
- NSIS installer with optional install directory

**Linux**:
- Wayland + X11 support
- Multiple package formats: AppImage, DEB, RPM, TAR.GZ
- Flatpak opt-in for local builds only
- Post-remove cleanup script for Debian packages

## Environment Variables

Configure via `.env` file (see `env.example`):

```
OPENAI_API_KEY=your_key
ANTHROPIC_API_KEY=your_key
GEMINI_API_KEY=your_key
WHISPER_MODEL=whisper-1
LANGUAGE=auto
DEBUG=false
```

Additional build-time env vars:
- `OPENWHISPR_OPENAI_BASE_URL`: Override default OpenAI base URL
- `OPENWHISPR_TRANSCRIPTION_BASE_URL`: Override transcription endpoint
- `OPENWHISPR_DEBUG`: Enable debug mode

## Performance Considerations

- Whisper model size vs speed tradeoff
- Audio blob size limits for IPC (10MB)
- Temporary file cleanup after every transcription
- Local model cache: max 2 models in memory (Python bridge), max 3 in ModelManager
- Process timeout protection (30s for transcription)
- IPC listener cleanup to prevent memory leaks
- `transcriptionStore` uses `useSyncExternalStore` for efficient React updates
- Batched database reads and non-blocking operations (commit 03baf02)

## Security Considerations

- API keys stored in `.env` file and loaded via environment
- API key caching with TTL (1 hour) and secure cleanup
- Context isolation enabled between main and renderer processes
- Custom base URL rejects non-HTTPS (except localhost)
- No remote code execution
- Sanitized file paths
- Limited IPC surface area via centralized `ipcHandlers.js`
- `store: false` on OpenAI API calls for privacy
