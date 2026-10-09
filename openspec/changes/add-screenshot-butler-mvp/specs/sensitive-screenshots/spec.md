## Purpose

Finds screenshots that expose private details (card numbers, passwords, backup codes, ID numbers) so people can review and delete them, without uploading them anywhere.

## ADDED Requirements

### Requirement: Detect exposed details
The app SHALL flag a screenshot when it contains a valid payment card number (checksum-verified), a labeled password or PIN, a list of backup or recovery codes, or an ID document number, and SHALL also flag screenshots the digest marks as sensitive.

#### Scenario: Demo set
- **WHEN** the 83 demo screenshots are indexed
- **THEN** exactly the 4 planted screenshots are flagged: card number, streaming password, backup codes, passport number

#### Scenario: Not a card
- **WHEN** a screenshot shows a 16-digit order number that fails the card checksum
- **THEN** it is not flagged

#### Scenario: Casual password mention
- **WHEN** a chat says "the pw is Sunset2024" without a "password:" label
- **THEN** rules do not flag it

### Requirement: Review what was found
Home SHALL show a Sensitive row with the count when it is above zero, and the Sensitive screen SHALL list each flagged screenshot with what was detected and state that nothing was uploaded to find them.

#### Scenario: Open Sensitive
- **WHEN** the user taps Sensitive on Home
- **THEN** the screen says "4 screenshots expose private details." and lists Card number, Password, Backup codes and ID number

### Requirement: Delete flagged screenshots
On Android 11 and later the app SHALL let the user delete all flagged screenshots through the system's confirmation dialog, and SHALL remove them from the index only after the user confirms.

#### Scenario: Confirmed deletion
- **WHEN** the user taps "Delete 4 screenshots" and confirms in the system dialog
- **THEN** the screenshots are deleted and the Sensitive count drops to 0

#### Scenario: Cancelled deletion
- **WHEN** the user cancels the system dialog
- **THEN** nothing is deleted and the list is unchanged
