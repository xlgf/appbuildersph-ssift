## Purpose

Has the on-device language model read every screenshot once, while the phone charges, and keep structured facts that improve folders, answers and sensitive detection.

## ADDED Requirements

### Requirement: Runs only while charging
The digest SHALL run in the background only while the phone is charging and the battery is not low, SHALL process screenshots in small batches with pauses, and SHALL stop when the conditions end.

#### Scenario: Phone unplugged
- **WHEN** the phone is not charging
- **THEN** no digest work runs

#### Scenario: Phone plugged in overnight
- **WHEN** the phone charges with 83 undigested screenshots
- **THEN** all 83 are digested without user action

### Requirement: Per-screenshot facts
For each screenshot the digest SHALL store its type, a one-sentence summary, up to 4 key values (such as password, amount, date, code, address) and whether it looks sensitive. A screenshot the model cannot parse SHALL be marked as done so it is not retried forever.

#### Scenario: Flight booking
- **WHEN** the flight booking screenshot is digested
- **THEN** its detail screen shows key values such as the booking code X7K2PQ, each with Copy

#### Scenario: Unparseable output
- **WHEN** the model returns text that is not valid JSON
- **THEN** the screenshot keeps its rule-based folders and is not digested again

### Requirement: Visible progress and manual trigger
Settings SHALL show how many screenshots the AI has read out of the total, and SHALL offer "Digest now" for testing.

#### Scenario: Before the demo
- **WHEN** the presenter taps "Digest now"
- **THEN** "Read by AI" counts up until it equals the total
