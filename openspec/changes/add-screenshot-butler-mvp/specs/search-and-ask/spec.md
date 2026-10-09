## Purpose

Lets people find a screenshot by what is in it, and get a direct answer to a question from their screenshots, with every answer traceable to its source.

## ADDED Requirements

### Requirement: Keyword search
The app SHALL search the text, folders and image labels of all indexed screenshots, ignore filler words, match word prefixes, and rank screenshots that match more of the question's words first.

#### Scenario: Search speed
- **WHEN** the user searches the 83-screenshot demo set
- **THEN** matching screenshots are found in under 300 ms

#### Scenario: Folder word in a question
- **WHEN** the user asks "wifi password at mom's"
- **THEN** screenshots in the Wi-Fi folder that also mention "mom" rank first

### Requirement: Direct answers from the on-device model
For a question, the app SHALL give the on-device language model only the top 3 matching screenshots (their text, image labels and digest facts), and SHALL show a short direct answer, the source screenshot, how many screenshots matched and how long the answer took.

#### Scenario: Wi-Fi password
- **WHEN** the user asks "wifi password at mom's" in airplane mode
- **THEN** the answer "Sunset2024" appears with the Mom chat screenshot as its source within 10 seconds

#### Scenario: Amount from a bank screenshot
- **WHEN** the user asks "how much did I send Alex"
- **THEN** the answer "$85.00" appears with the bank transfer screenshot as its source

#### Scenario: Thinking state
- **WHEN** an answer is being generated
- **THEN** the screen shows an amber "lantern" indicator and "Running on this phone. No internet needed."

### Requirement: Honest fallbacks
The app SHALL NOT invent answers. When the model reports no answer it SHALL say so and list the matching screenshots; when the model is unavailable it SHALL still list matches; when nothing matches it SHALL say so and suggest searching again.

#### Scenario: Not in the screenshots
- **WHEN** the model replies that the answer is not found
- **THEN** the screen says "I'm not sure. These screenshots might help." above the matches

#### Scenario: Model not installed
- **WHEN** the model file is missing
- **THEN** search results still appear and Home offers "Import model file"

#### Scenario: No matches
- **WHEN** the user searches "gym membership" and no screenshot mentions it
- **THEN** the screen says "Nothing found for 'gym membership'." with a "Search again" action

### Requirement: Copy the answer
The answer screen SHALL offer one-tap copy of the answer, and the detail screen SHALL offer copy for each extracted value.

#### Scenario: Copy password
- **WHEN** the user taps Copy on the "Sunset2024" answer
- **THEN** "Sunset2024" is on the clipboard
