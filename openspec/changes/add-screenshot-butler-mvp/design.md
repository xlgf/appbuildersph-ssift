## Context

See proposal.md for why. The code for every capability is already written in `app/` but has never been compiled or run on a device; the dev machine has Android Studio and SDK 35 (no phone connected yet). Gemma 3 1B is text-only, so all "seeing" is done by ML Kit before the LLM runs. The work is solo and time-boxed to 20 hours, judged live in airplane mode.

## Goals / Non-Goals

**Goals:**
- Every feature works on a mid-range phone with 4 GB+ RAM, fully offline.
- Answers in under 10 s; indexing never blocks the UI.
- The privacy claim is enforced by Android, not just stated.

**Non-Goals:**
- iOS, cloud sync, accounts, regular camera photos.
- Calendar, Maps and full-screen QR actions; semantic (embedding) search; a RAM-tiered model choice. All post-hackathon.

## Decisions

**1. OCR first, text LLM second.** ML Kit reads text (~0.2 s per screenshot) and labels low-text images; Gemma 3 1B only reasons over text. *Alternative:* a vision LLM such as Gemma 4 E2B: better at layout and pictures, but ~2.6 GB, flagship-only, seconds per image, and gives no search index.

**2. The LLM never runs per screenshot in the foreground.** Search uses a Room FTS4 index with OR-prefix matching and an in-app ranking (folder match = 3 points, word match = 1). The LLM reads only the top 3 matches per question, plus a charging-only background digest. *Alternative:* LLM-tag every screenshot at index time: about an hour for 1,000 screenshots.

**3. MediaPipe LLM Inference 0.10.27 with Gemma 3 1B int4 `.task` (555 MB).** Known-good path for this model; one engine instance loaded at launch and guarded by a mutex; temperature 0.2; Gemma chat template applied by the app (toggle `WRAP_GEMMA_TEMPLATE`). *Alternative:* LiteRT-LM (Google's successor, `.litertlm`), which is the fallback if MediaPipe fails the hour-4 gate. llama.cpp is the second fallback.

**4. Regex rules for folders and sensitive detection; LLM digest only adds.** Deterministic, instant and testable on the demo set (Luhn check for cards; "password:" labels; backup codes need a keyword plus 3 code-like strings). The digest can add a folder or a sensitive flag, never remove one.

**5. Structured digest output.** A fixed JSON shape (type, summary, values, sensitive); the outermost `{...}` is parsed; failures are stored as `{}` so they are not retried.

**6. Privacy by manifest.** `tools:node="remove"` strips INTERNET and ACCESS_NETWORK_STATE merged in by libraries; bytes sent come from `TrafficStats` for the app's UID. Both ML Kit models are the bundled variants so nothing downloads at runtime.

**7. Manual dependency wiring.** One `AppContainer` in the Application class; no DI framework for a 20-hour build.

**8. Prompts tuned off-device.** Iterate in Ollama (`gemma3:1b`) with real OCR text, then copy wording into `ai/Prompts.kt`. Rebuilding the APK per prompt change is too slow.

## Risks / Trade-offs

- [Code has never compiled] → First Android Studio sync is the real hour 0; fix version or API mismatches before the clock starts if allowed.
- [MediaPipe session API differs in 0.10.27] → Fix in `LocalLlm.kt` only; LiteRT-LM fallback at hour 4.
- [1B model gives wrong or wordy answers] → Strict ANSWER/SOURCE format, top-3 context, 64-token cap, source screenshot always shown, "not sure" path.
- [Digest heats the phone or fails JSON] → Batches of 5 with 2 s pauses, charging + battery-not-low constraints; run on the demo set the night before.
- [Media scan misses pushed demo screenshots] → Open Photos once or reboot the phone after pushing.
- [Live demo failure] → 5 airplane-mode rehearsals and a recorded backup video.

## Open Questions

- Which phone is the demo device, and how much RAM does it have? Affects only speed expectations, not the plan.
