## Purpose

Guarantees that screenshots and everything derived from them never leave the phone, and makes that guarantee visible and verifiable to users and judges.

## ADDED Requirements

### Requirement: No network access
The installed app SHALL NOT hold the INTERNET or ACCESS_NETWORK_STATE permissions, and every feature SHALL work in airplane mode once the model is installed.

#### Scenario: Merged manifest
- **WHEN** the release APK's merged manifest is inspected
- **THEN** it contains neither INTERNET nor ACCESS_NETWORK_STATE

#### Scenario: Airplane mode
- **WHEN** the phone is in airplane mode
- **THEN** indexing, search, Ask, the digest and the Sensitive folder all work

### Requirement: Visible privacy indicator
Home and Settings SHALL show "On-device — N bytes sent", where N is the bytes this app has sent over any network since the phone started.

#### Scenario: Normal use
- **WHEN** the user has indexed and asked questions
- **THEN** the indicator shows 0 bytes sent

### Requirement: Local model installation
The app SHALL load the language model from app storage or the development push location, and SHALL let the user import a model file from local storage. It SHALL NOT download a model itself.

#### Scenario: Import from Downloads
- **WHEN** the user taps "Import model file" and picks the .task file
- **THEN** the model is copied into app storage and loaded

### Requirement: User can wipe derived data
Settings SHALL offer "Delete my index", which after confirmation removes all extracted text, labels and facts without touching the screenshots themselves.

#### Scenario: Delete index
- **WHEN** the user confirms "Delete my index"
- **THEN** the index is empty and the screenshots remain in the gallery
