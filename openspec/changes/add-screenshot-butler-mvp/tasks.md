## 1. Setup (before hour 0)

- [x] 1.1 Scaffold the Android project: Gradle, manifest without network permissions, Compose theme
- [x] 1.2 Install Android Studio with SDK 35
- [x] 1.3 Download Gemma 3 1B int4 `.task` (in Downloads)
- [x] 1.4 Generate the 83 demo screenshots, including 5 CC0 photo screenshots
- [x] 1.5 Build the project and fix compile errors (CLI build via `gradlew.bat` with Android Studio's JDK 21; Gradle 8.14)
- [x] 1.6 Connect the demo phone with USB debugging; `adb devices` lists it (Sony Xperia 5 J9210, Android 11, ~5.4 GB RAM)
- [x] 1.7 Push the model to `/data/local/tmp/llm/model.task` (copy also in `/sdcard/Download/model.task` for Import)
- [x] 1.8 Push the demo screenshots to `Screenshots/ButlerDemo`; MediaStore lists all 85 (set updated with everyday keepers: parking, library, pharmacy, dry cleaning, orders, tracking, cafe receipt, recipe, wishlist item, workout plan, quote, gift card)
- [x] 1.9 (Dropped: prompts were tuned on the phone instead) Install Ollama and run `gemma3:1b` for prompt tuning
- [x] 1.10 Add Demo mode so the phone's ~6,900 personal screenshots stay out of the demo
- [ ] 1.11 Verify Demo mode: only the 83 demo screenshots appear; turning it off re-indexes everything

## 2. Hour 4: LLM go / no-go

- [x] 2.1 Write the model loader (load at launch, import from file, mutex-guarded generation)
- [x] 2.2 App installs; welcome screen and photo permission work (fixed dark-mode text color)
- [x] 2.3 Home stops showing "model not installed" after launch (loads from /data/local/tmp on Android 11)
- [x] 2.4 Gate: a question returns generated text in airplane mode (PASSED: ~3.7 s per answer on the Xperia 5)
- [x] 2.5 (Not needed: gate passed) Only if 2.4 fails: switch `LocalLlm.kt` to LiteRT-LM (fallback: llama.cpp Android example)

## 3. Hour 9: indexing, folders, search and Ask

- [x] 3.1 Write screenshot listing, OCR, image labeling and the Room FTS index
- [x] 3.2 Write regex folder tagging and the folder screens
- [x] 3.3 Write search ranking, the Ask engine and the Answer screen
- [x] 3.4 Verify Home shows 83 screenshots and indexing keeps the UI responsive (~30 s for 83)
- [x] 3.5 Verify folder counts on the demo set: Tickets 3, Receipts 3, Wi-Fi 2, Codes 2, Addresses 3 (rule fixed: chat times and "Dr." no longer match), Chats 45, Recipes 1, Images 6
- [x] 3.6 Verify the 5 photo screenshots land in Images with sensible labels (pizza: food; cat: cat, pet; dog: cat, dog; lake/beach: sky, field)
- [x] 3.7 Verify the right screenshot ranks first for each demo question (logs show the winner for all 8 demo questions)
- [x] 3.8 Tune the Ask prompt (tuned on-device instead of Ollama: prefill "ANSWER:", instructions after the question, chat timestamps stripped, no worked example because the 1B model copies it, answers snapped to the full source token)
- [x] 3.9 Decide `WRAP_GEMMA_TEMPLATE` on or off from real answers (on works)
- [x] 3.10 Verify "wifi password at mom's" returns Sunset2024 with its source in under 10 s (3.7 s; "$85.00" for Alex also correct)
- [x] 3.11 Concert gate and flight booking code questions: added internal event/flight tags and weighted search hints, send only the best match when it leads by 2+, retry once without the prefill on an empty reply, snap squashed answers back to the screenshot's spelling. Results: "Gate 14 - RowF" (3.9 s), "X7K2PQ" (3.8 s)

- [x] 3.12 Label lookup: rejoin "label / value" lines OCR splits ("Spot: C14") and answer straight from the screenshot when the question names a multi-word label or asks where the car is parked. All 8 demo questions correct on the phone
- [x] 3.13 Bug fixes from review: DB version bumped (old installs crashed), answer race, deleted screenshots removed from the index, real screenshot dates, honest "Checked by AI / Facts found" counts, failed OCR not retried, imported model used at once, digest runs don't overlap, honest "Delete my index" wording, app no longer opens a second copy on Home when reopened (singleTask)

## 4. Hour 11.5: charging digest

- [x] 4.1 Write the digest prompt, JSON parsing and the charging-only worker
- [x] 4.1a Fix: all 83 digests failed to parse because Gemma repeats the prefilled "{" (now added only when missing)
- [x] 4.1b Fix: a stopped worker no longer marks the current screenshot as digested with empty facts
- [x] 4.1c Run "Digest now" as a foreground job so Android's 10-minute background limit doesn't pause it
- [x] 4.1d Speed: skip screenshots with nothing to extract (69 of 83), strip noise lines, cap prompt at 600 chars, no cooling pause on "Digest now"
- [x] 4.1e Reliability: stream replies with a length cap and repetition stop (a runaway "7777…" reply took 127 s), repair unclosed JSON, drop the "type" field (the model answered "ticket" for everything), trust the AI's sensitive flag only with a secret-looking value
- [x] 4.2 Verify "Digest now" brings "Read by AI" up to the total (83 of 83 in 123 s, was 10+ min with stalls)
- [x] 4.3 Tune the digest prompt until at least 90% of screenshots return valid JSON (14 of 14 LLM digests parse)
- [x] 4.6 Try the GPU backend (MediaPipe preferred backend), falling back to CPU. GPU works on the Xperia 5 (Adreno 640): answers 2.6-2.8 s (CPU 3.7-3.9 s), digest 86 s (CPU 99 s). First GPU load ~20 s once, then ~3 s. Switch: `TRY_GPU` in LocalLlm.kt
- [x] 4.4 Verify digest values appear on the detail screen with Copy (concert ticket: date, venue, doors, each with Copy)

## 5. Hour 13: sensitive screenshots

- [x] 5.1 Write sensitive detection (Luhn, passwords, backup codes, IDs) and the Sensitive screen
- [x] 5.2 Verify exactly the 4 planted screenshots are flagged, with no false positives (card, password, backup codes, passport)
- [ ] 5.3 Verify confirmed and cancelled deletion on Android 11+ (cancel verified: system dialog appears, DENY keeps all files; confirm path not run, it would delete the demo files)

## 6. Hour 17.5: polish and offline end to end

- [x] 6.1 Write the Copy action, Settings and the privacy indicator
- [x] 6.2 Verify the merged manifest has no INTERNET or ACCESS_NETWORK_STATE and the indicator shows 0 bytes
- [x] 6.3 Add the brand font (Manrope from the Stitch design system, bundled in res/font so it works offline)
- [ ] 6.4 Add a launcher icon
- [x] 6.5 Match the Stitch designs: bento theme (sage, coral, indigo, chartreuse, Manrope, rounded cards), Welcome with the "B" monogram, Home bento grid, Answer in a chartreuse card, thinking blob; other screens inherit the components
- [x] 6.6 Full offline run from a fresh install: index, ask, digest, sensitive (airplane mode, no active network: 85 indexed, digest 148 s with facts in 22, all 8 questions correct, Sensitive 4, 0 bytes sent)
- [ ] 6.7 Tidy fact labels on the detail screen ("Door_7" -> "Doors", "Ticket_type" -> "Ticket type")

## 7. Hour 20: demo ready

- [ ] 7.1 Pre-index and pre-digest the demo phone; open the app once so the model is loaded
- [ ] 7.2 Rehearse the 3-minute script 5 times in airplane mode
- [ ] 7.3 Record the backup demo video
- [ ] 7.4 Set up scrcpy mirroring to the projector
- [ ] 7.5 Prepare judge answers: Google Photos, Microsoft Recall, ChatGPT, "is 1B enough?", "why not a vision model?"
