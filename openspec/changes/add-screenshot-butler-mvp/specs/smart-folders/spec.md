## Purpose

Sorts screenshots into folders people recognize (tickets, receipts, Wi-Fi details, codes, addresses, chats, recipes, images) so they can browse without searching.

## ADDED Requirements

### Requirement: Automatic folder tagging
The app SHALL place each screenshot into zero or more of these folders as soon as it is indexed: Tickets, Receipts, Wi-Fi, Codes, Addresses, Chats, Recipes, Images.

#### Scenario: Concert ticket
- **WHEN** a screenshot of a concert ticket with a gate and seat is indexed
- **THEN** it appears in Tickets

#### Scenario: Chat with Wi-Fi details
- **WHEN** a chat screenshot mentions a wifi network and password
- **THEN** it appears in both Wi-Fi and Chats

#### Scenario: Photo with no text
- **WHEN** a screenshot receives image labels
- **THEN** it appears in Images

### Requirement: Folder counts and browsing
Home SHALL list every folder with its current count, and opening a folder SHALL show its screenshots newest first.

#### Scenario: Open a folder
- **WHEN** the user taps Tickets on Home
- **THEN** a list of ticket screenshots opens, newest first, with thumbnail, text snippet and date

### Requirement: Digest refines folders
When the charging digest identifies a screenshot's type, the app SHALL add that folder if the rules missed it, and SHALL never remove a folder the rules assigned.

#### Scenario: Rule missed a receipt
- **WHEN** the digest classifies an untagged screenshot as a receipt
- **THEN** it appears in Receipts afterwards
