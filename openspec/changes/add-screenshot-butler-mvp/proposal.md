## Why

People screenshot tickets, Wi-Fi passwords, receipts, addresses and codes to save them, then can't find them again: the gallery sorts by date and can't search the text inside images. Screenshots are also the most private data on a phone (bank transfers, private messages, passwords), so cloud AI is the wrong tool. This change builds the hackathon MVP that does all of the reading, sorting and answering on the device, and keeps working in airplane mode.

## What Changes

- Index the phone's Screenshots album with on-device OCR, and label low-text screenshots (photos, memes) with on-device image labeling.
- Sort screenshots into smart folders: Tickets, Receipts, Wi-Fi, Codes, Addresses, Chats, Recipes, Images.
- Full-text search, plus Ask: an on-device LLM (Gemma 3 1B) reads the top 3 matches and returns a direct answer with its source screenshot.
- Charging digest: while the phone charges, the LLM reads every screenshot once and stores structured facts.
- Sensitive folder: flag screenshots that expose card numbers, passwords, backup codes or ID numbers, and let the user delete them.
- Privacy enforced by the platform: no internet permission; the UI shows bytes sent (0).
- Swiss editorial UI in the "lanterns at dusk" palette: onboarding, Home, Answer, Folder, Sensitive, Detail and Settings screens.

## Capabilities

### New Capabilities
- `screenshot-indexing`: finding screenshots, on-device OCR, image labels for low-text screenshots, and keeping the index current.
- `smart-folders`: rule-based folder tagging and browsing screenshots by folder.
- `search-and-ask`: keyword search over the index and LLM answers with a source screenshot.
- `charging-digest`: background LLM pass that runs only while charging and stores per-screenshot facts.
- `sensitive-screenshots`: detecting screenshots that expose private details, reviewing them and deleting them.
- `on-device-privacy`: the guarantee that no data leaves the phone, and how the app shows it.

### Modified Capabilities

None. This is the first change in the project.

## Impact

- New Android app in `app/` (package `com.screenshotbutler`); all code is new.
- Dependencies: Compose BOM, Room (KSP), WorkManager, Coil 3, ML Kit text recognition and image labeling (bundled), MediaPipe tasks-genai 0.10.27.
- Runtime requirement: the Gemma 3 1B int4 `.task` model (~555 MB) on the device, installed by adb or imported in the app.
- Demo tooling in `tools/`: generator for 83 demo screenshots (including 5 CC0 photos) and an adb push script.
