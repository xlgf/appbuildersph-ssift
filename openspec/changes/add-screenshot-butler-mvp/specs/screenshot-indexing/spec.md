## Purpose

Turns the phone's screenshots into a searchable, on-device index: the text in each screenshot, plus a description of the picture when a screenshot has little text.

## ADDED Requirements

### Requirement: Screenshot access with consent
The app SHALL ask for photo access before reading any screenshot, SHALL read only images in the device's Screenshots folders, and SHALL accept Android 14+ partial ("selected photos") access.

#### Scenario: First launch without permission
- **WHEN** the user opens the app for the first time
- **THEN** a welcome screen explains that screenshots stay on the phone and offers "Allow access to screenshots"
- **AND** no screenshot is read until access is granted

#### Scenario: Only screenshots are read
- **WHEN** the phone has camera photos and screenshots
- **THEN** only images in Screenshots folders appear in the index

### Requirement: Demo mode
The app SHALL offer a Demo mode in Settings that limits indexing to the Screenshots/ButlerDemo folder. It SHALL be on by default in debug builds and off in release builds, and switching it SHALL clear the index and rebuild it from the selected folders.

#### Scenario: Presenter's own phone
- **WHEN** Demo mode is on and the phone also holds thousands of personal screenshots
- **THEN** only the 83 demo screenshots appear anywhere in the app

#### Scenario: Turning Demo mode off
- **WHEN** the user turns Demo mode off in Settings
- **THEN** the index is cleared and all screenshots are indexed

### Requirement: On-device text extraction
The app SHALL extract the text from every newly found screenshot on the device, without network access, and SHALL show indexing progress.

#### Scenario: First indexing run
- **WHEN** access is granted and 83 screenshots exist
- **THEN** Home shows "Reading N of 83" while indexing and then "83 screenshots, read and sorted on this phone."

#### Scenario: Indexing speed
- **WHEN** 200 screenshots are indexed on a mid-range phone
- **THEN** indexing completes in under 2 minutes and the UI stays responsive

#### Scenario: Unreadable image
- **WHEN** one screenshot cannot be decoded
- **THEN** it is skipped and the rest of the batch is indexed

### Requirement: Image labels for low-text screenshots
When a screenshot contains fewer than 40 letters of text, the app SHALL describe the picture with up to 5 on-device image labels (confidence at least 0.7) and SHALL make those labels searchable.

#### Scenario: Photo screenshot
- **WHEN** a screenshot shows a dog photo with no text
- **THEN** it receives labels such as "dog" and appears in the Images folder

#### Scenario: Text-heavy screenshot
- **WHEN** a screenshot is a chat with several messages
- **THEN** no image labels are computed for it

### Requirement: Index stays current
The app SHALL index screenshots taken while it was in the background the next time it is opened, without re-reading screenshots already indexed.

#### Scenario: New screenshot
- **WHEN** the user takes a screenshot and reopens the app
- **THEN** the new screenshot is searchable within 5 seconds
