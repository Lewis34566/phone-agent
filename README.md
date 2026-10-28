# Phone Agent

A conversational agent for managing the software on your Android phone. Native Kotlin + Jetpack Compose. Local intent recognition first, cloud LLM only as fallback.

## Highlights

- **Local-first speed**: common commands are matched by on-device rules and executed in <300 ms with no network and no model call.
- **Real device control**: list apps, find unused apps, inspect permissions, rank background activity, check storage, open / uninstall / jump to system settings for any app — with honest confirmation flows.
- **Replaceable LLM layer**: one `LlmProvider` interface. Ships with an OpenAI-compatible provider (DeepSeek, Kimi, Qwen…), plus one-tap **offline ollama** mode. Cloud calls stream with timeout and retry.
- **Phone-Vibe skills**: multi-step tasks (one sentence → plan → confirm → run → report), notes, web search, photo OCR (on-device ML Kit), vision Q&A, image generation, and a built-in snake game.
- **Memory & sessions**: global habit memory (local only), chat sessions with history, custom quick commands.
- **Honest boundaries**: per-app battery stats, silent uninstall, auto cache clearing need Shizuku / accessibility — reserved interfaces only, never faked. UI clearly states what a normal app can't do.

> Commands can be spoken in Chinese (打开微信, 列出已装应用…) — the intent matchers understand Chinese; the UI is English.

## Build

```bash
gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
gradlew testDebugUnitTest
```

Requirements: JDK 17+, Android SDK (path in `local.properties`).

## Architecture

```
ui/         Compose chat UI (bubbles / result cards / confirm cards) — knows only the ViewModel
core/       Dispatcher: local match() first → execute; miss → LlmProvider streaming fallback
tools/      Tool registry: every tool ships spec + match() + execute(); adding a tool never touches the dispatcher
data/       AppRepository (in-memory cache, TTL 5 min), UsageRepository, Room, Keystore encryption
core/llm/   LlmProvider interface + OpenAI-compatible implementation (swappable)
```

## Tools

| Tool | Permission | Notes |
|---|---|---|
| list_apps | none | installed apps: name / version / install time / APK size (all apps incl. system on request) |
| app_detail | none (+usage access for more) | full details of one app |
| find_unused_apps | usage access | unused apps ranked; guided grant flow, auto-retry on return |
| app_permissions | none | permission list with grant state |
| background_rank | usage access | background activity estimate (clearly labeled, not real battery drain) |
| storage_info | none | storage overview + largest apps |
| open_app / uninstall_app / app_settings | none / REQUEST_DELETE_PACKAGES | uninstall = double confirmation, never silent |
| note | none | local notes (add / list / delete) |
| web_search | none | scrapes Baidu results, tap to open in browser |
| describe_image | vision config | multimodal Q&A about the last photo |
| generate_image | image-gen config | cloud image generation |
| play_game | none | built-in snake game (offline) |

## Configuration (Settings screen)

- **Cloud LLM**: baseUrl / apiKey / model — DeepSeek prefilled; apiKey encrypted with Android Keystore (AES-GCM).
- **Offline ollama**: one tap points at `http://127.0.0.1:11434/v1` (Termux ollama). Cleartext HTTP is allow-listed for localhost + LAN IPs only.
- **Image generation / Vision**: separate OpenAI-compatible endpoints.
- **Usage access**: grant in system settings; unused-apps, full size and background ranking need it.

## Performance

- Cold start < 1 s: zero init in `Application.onCreate`, all singletons lazy.
- App list scanned on background coroutines, cached in memory (TTL 5 min).
- LLM prompts are trimmed: system prompt + last 4 turns + tool schemas only — never the app list.
- Streaming via SSE; 10 s connect / 30 s read timeouts; 2 retries with exponential backoff on 5xx/429.
