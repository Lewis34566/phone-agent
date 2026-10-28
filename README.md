# Phone Automation Assistant

A native Android agent (Kotlin + Jetpack Compose) that does things on your phone from a single sentence — a real tool-use agent loop, not a command parser. The UI is English; conversations are in Chinese.

> Commands are spoken in Chinese (打开微信 / 把错题存进错题本 / 今天复习什么). The system prompt and tool outputs stay Chinese by design; UI chrome is English.

## What changed in v0.2.0 (the big one)

The previous version was two half-products: this native app (a shell with a chat box — tools-first, LLM as fallback, queued execution, no feedback loop) and the Termux-based Phone-Vibe agent (a real agent loop, trapped in a terminal). v0.2.0 merges them: **the Phone-Vibe brain now runs inside the native shell**.

- **Real agent loop** (`core/agent/AgentLoop.kt`): LLM-led → tool call → result fed back with `tool_call_id` → model reasons about the result and continues. Up to 10 rounds; a circuit breaker stops after 3 consecutive all-failed rounds instead of burning tokens. Safe tools execute automatically; destructive ones (uninstall) pause on a confirm card.
- **Byte-stable system prompt + tool schema order** — DeepSeek's context cache keys on the prefix, taking first-byte latency from 4–10s down to ~0.1s. Edit the prompt and you pay one cold start.
- **Mistake notebook loop (v1 flagship scenario)**: snap a photo → on-device ML Kit OCR → auto-saved to the notebook via the agent loop → spaced repetition at 1/3/7/15/30/30 days → daily 9:00 reminder (inexact `AlarmManager`, no special permission) → "今天复习什么" quizzes one card at a time; correct advances a stage, wrong resets to stage 0.
- Renamed from "phone manager" to **automation assistant**: a third-party app can never out-manage the OEM system app (the platform won't allow force-stop / cache-clearing / permission revocation), so the product no longer competes there.

## Highlights

- **Two dispatch paths**: on-device intent matching executes common commands offline in <300ms; everything else goes to the agent loop.
- **Replaceable LLM layer**: one `LlmProvider` interface, OpenAI-compatible streaming provider (DeepSeek / Kimi / Qwen…), one-tap offline ollama mode. Streaming with timeout + retry.
- **Tools**: mistake notebook (add / review due / mark / search), notes, web search, photo OCR, vision Q&A, image gen, app queries, snake game. Adding a tool = one class + one registry line.
- **Honest boundaries**: Shizuku / accessibility are reserved interfaces only, never faked. The UI states plainly what a normal app cannot do.
- **Memory & sessions**: global habit memory (local only), chat sessions with history, Room migrations preserved across upgrades.

## Build

```bash
gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
gradlew testDebugUnitTest      # 18 tests: intent rules, formatting, spaced-repetition curve
```

Requirements: JDK 17+, Android SDK (path in `local.properties`).

## Architecture

```
ui/         Compose chat UI (bubbles / result cards / confirm cards) — knows only the ViewModel
core/       Dispatcher: local match() fast path → AgentLoop for everything else
core/agent/ AgentLoop: LLM-led rounds, tool results fed back, failure circuit breaker
tools/      Tool registry: every tool ships spec + match() + execute()
data/       MistakeRepository (spaced repetition), AppRepository, Room v4, Keystore encryption
data/remind/ Daily 9:00 AlarmManager reminder → notification if mistakes are due
core/llm/   LlmProvider interface + OpenAI-compatible implementation (swappable)
```

## Mistake notebook loop

```
photo → ML Kit OCR → agent loop (extracts question/answer/subject) → Room
     → review due at 1/3/7/15/30/30-day intervals → 9:00 notification
     → quiz one card at a time → correct: stage+1, wrong: stage 0
```

The spaced-repetition schedule is a pure function covered by unit tests (`MistakeScheduleTest`).
