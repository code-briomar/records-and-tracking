"""# Records And Tracking Mobile On Mobile XML

# Design Screens on Stich With Google

This project is designed to support Kenyan court clerks by digitizing and streamlining their daily tasks.

## Auto-Update System

The app includes automatic update detection and installation. When a new version is released on GitHub, users will be notified and can update with one click.

### How It Works
1. On startup, the app checks GitHub for new releases
2. If a newer version is found, an update notification appears
3. User clicks "Download & Install" to download the update
4. After download, user clicks "Restart Now" to install and launch the new version

### Creating Releases

To release a new version:

1. **Build the release:**
   ```bash
   mvn clean package -DskipTests
   mkdir -p target/libs
   cp target/records-and-tracking-0.1.0-SNAPSHOT.jar target/libs/
   export TMPDIR=~/jpackage-tmp
   export JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMPDIR"
   jpackage \
     --input target/libs \
     --name records-and-tracking \
     --main-jar records-and-tracking-0.1.0-SNAPSHOT.jar \
     --main-class com.courttrack.AppLauncher \
     --type app-image \
     --app-version X.Y.Z \
     --description "Records and Tracking" \
     --dest target/jpackage \
     --temp $TMPDIR
   ```

2. **Create ZIPs for each platform:**
   ```bash
   cd target/jpackage
   zip -r ../records-and-tracking-linux.zip records-and-tracking/
   # For Windows: zip -r ../records-and-tracking-windows.zip records-and-tracking/
   ```

3. **Upload to GitHub:**
   ```bash
   gh release create vX.Y.Z --title "Version X.Y.Z" --notes "Release X.Y.Z"
   gh release upload vX.Y.Z target/records-and-tracking-linux.zip
   gh release upload vX.Y.Z target/records-and-tracking-windows.zip
   ```

### Requirements
- ZIP filenames must contain the platform: `records-and-tracking-linux.zip` or `records-and-tracking-windows.zip`
- Release tag must be higher than the current version in `app.properties`
- Use `jpackage` to create self-contained app images (includes Java runtime)

## Features

### Streamlining Case Management & Reducing Workload
*   **Integrated Case Dashboard:** A central hub that syncs with the existing Case Tracking System (CTS), offering a real-time summary of assigned cases, deadlines, status, and pending tasks.
*   **Automated Document Generation:** Templates for common documents (summonses, court orders, notices) that auto-populate with case data to reduce manual entry.
*   **Digital Cause List Management:** Enables clerks to electronically create, update, and publish the daily cause list, with SMS/email notifications for lawyers and litigants.
*   **Task and Deadline Tracker:** A personalized task management system with automated reminders for case responsibilities and critical deadlines.

### Enhancing Legal Research & Preparation
*   **Mobile Legal Resource Library:** Offline access to essential Kenyan legal resources, including the Constitution, key statutes, and court rules.
*   **Precedent and Case Law Search:** A simple tool to quickly search and retrieve relevant case law from the Kenya Law Reports database.
*   **Voice-to-Text for Minute Taking:** Transcription feature for court proceedings and meetings to save time on manual minute-taking.

### Improving Communication & Information Sharing
*   **Secure Messaging:** In-app messaging for confidential communication between clerks, judges, and other court staff.
*   **Digital File Sharing and Annotation:** A secure tool for sharing case files with judges, allowing for digital annotation to support the paperless policy.
*   **Public Information Portal:** A simplified public interface for searching case information, checking hearing dates, and understanding court procedures to reduce direct inquiries.

### Supporting Administrative & Financial Tasks
*   **Fee and Fine Calculator:** An automated calculator to determine court fees and fines based on the type of offense.
*   **Digital Payment Integration:** Integration with mobile money services (like M-Pesa) for collecting fees and fines, with automatic receipt generation.
*   **Basic Bookkeeping Module:** A simple ledger for tracking and reporting collected fines and fees, with easy export functionality.

## Database Design

This database schema is designed to be comprehensive, supporting all current and planned features of the application. The design is normalized to reduce redundancy and uses Room for persistence, enabling a robust offline-first architecture. Foreign keys (FK) are used to establish clear relationships between entities.

### 1. Core Entities
These are the foundational models of the application.
#### **`Person`**

* **Purpose**: The single source of truth for any human being in the system (Judge, Clerk, Offender, Witness).

* **Columns**:

    * person_id (PK, UUID)

    * national_id (Indexed, Unique)

    * first_name, last_name, other_names

    * gender, dob

    * phone_number, email

    * photo_local_uri (String - path to file)

    * photo_remote_url (String - Firebase URL)

    * bio_data_hash (For fingerprint/biometric future-proofing)

    * _sync_flags (Base class: is_new, is_deleted, last_updated)

#### **`AppUser`**

* **Purpose**: Credentials for people who can actually login to the app (Clerks, Admins). Links to Person.

* **Columns**:

* user_id (PK, UUID)

* person_id (FK -> Person)

* username, password_hash (salted)

* role (Enum: COURT_ADMIN, JUDGE, HEAD_OF_STATION, MARGISTRATE, ADMIN)

* assigned_court_id (FK -> Court)

#### **`Case`**
* **Purpose**: A single legal case
* **Columns**:
    * `case_id`(PK,UUID)
    * `case_number`(Unique e.g E123/2024)
    * `court_id` (FK->Court)
    * `filing_date`
    * `case_status`(Enum: OPEN, CLOSED *TBD)
    * `case_category` (ENUM: Traffic,Criminal,Civil)
    * `_sync_flags`

#### **`CaseParticipant`**
* **Purpose**: Links a `Person` to a `Case` with a specific `Role`
* **Example**: A case can now have 5 Accussed, 2 Complainants, and a judge without changing the database structure
* **Columns**:
    * `participant_id` (PK,UUID)
    * `case_id` (FK -> Case)
    * `person_id` (FK -> Person)
    * `role_type` (ENUM:Judge,Margistrate,Accused, Complainant,Advocate,Witness,Officer)
    * `is_active` ( Boolean e.g if a lawyer withdraws )

#### **`Charge`**
* **Purpose**: Specific offenses linked to a case. Essential for 'Fine Calculator'
* **Columns**:
    * `charge_id` (PK, UUID)
    * `case_id` (FK->Case)
    * `accused_person_id` (FK->Person)
    * `offense_code` (e.g "Traffic Act Sec 12")
    * `particulars` (Text description)
    * `plea` (ENUM: GUILTY, NOT_GUILTY)
    * `verdict`
    * `sentence_notes`

#### **`Hearing`**
* **Purpose**: Specific event where a case appears before a judge
* **Columns**:
    * `hearing_id`(PK,UUID)
    * `case_id`(FK->Case)
    * `presiding_judge_id`(FK->Person)
    * `scheduled_start_time` (Datetime)
    * `court_room`
    * `outcome_notes`(Minute taking happens here)
    * `next_hearing_date`

#### **`CaseDocument`**
* **Purpose**: Digital files
* **Columns**:
    * `document_id`(PK,UUID)
    * `case_id` (FK -> Case)
    * `uploader_id`(FK->Person)
    * `file_name`
    * `file_type` (PDF,JPG, WORD,TEXT, etc )
    * `local_path` (Crucial: /data/user/0/.../evidence.doc,pdf)
    * `remote_path`
    * `file_size_bytes` *Consider saving files as BLOB

#### **`Transaction`**
* **Purpose**: Logs Payments
* **Columns**:
    * `transaction_id` (PK, UUID)
    * `case_id` (FK->Case)
    * `payer_id` (FK->Person)
    * `amount_due`
    * `amount_paid`
    * `payment_mode` (ENUM: Mpesa, Cash, Card, ETC)
    * `mpesa_reference_code`
    * `receipt_number`
### 2. Workflow and Case Management
These models support the daily administrative and procedural tasks of court clerks.

#### **`Task`**
*   **Purpose**: Manages tasks and deadlines related to cases or general duties.
*   **Fields**:
    *   `taskId` (PK)
    *   `caseId` (FK to `Case`, optional)
    *   `userId` (FK to `User`, assigned to)
    *   `description`, `dueDate`, `priority`, `status` (Pending, Completed)

#### **`Document`**
*   **Purpose**: Represents a digital document or evidence file attached to a case.
*   **Fields**:
    *   `documentId` (PK)
    *   `caseId` (FK to `Case`)
    *   `name`, `mimeType`, `url` (storage path)
    *   `fileData` (for offline caching)

#### **`Annotation`**
*   **Purpose**: Stores annotations made by users on documents.
*   **Fields**:
    *   `annotationId` (PK)
    *   `documentId` (FK to `Document`)
    *   `userId` (FK to `User`)
    *   `content`, `position` (e.g., coordinates on page)
    *   `createdAt`

#### **`CauseList`**
*   **Purpose**: Manages the daily court schedule (cause list).
*   **Fields**:
    *   `causeListId` (PK)
    *   `courtId` (FK to `Court`)
    *   `date`
    *   `isPublished` (boolean)
    *   `casesJson` (A JSON array of case numbers and times for that day)

### 3. Legal Research
Models to support offline access to legal resources.

#### **`LegalResource`**
*   **Purpose**: Stores legal reference materials like the Constitution and statutes.
*   **Fields**:
    *   `resourceId` (PK)
    *   `title`, `category` (e.g., "Statute", "Court Rule")
    *   `content` (HTML or Markdown)
    *   `version`, `datePublished`

#### **`Precedent`**
*   **Purpose**: Stores summarized case law and precedents for quick searching.
*   **Fields**:
    *   `precedentId` (PK)
    *   `citation`, `caseTitle`
    *   `court`, `judgmentDate`
    *   `summary`, `fullTextUrl` (link to Kenya Law)

### 4. Financial Management

#### **`FinancialTransaction`**
*   **Purpose**: Tracks all financial activities, including court fees and fines.
*   **Fields**:
    *   `transactionId` (PK)
    *   `caseId` (FK to `Case`, optional)
    *   `payerName`
    *   `type` ("Fee", "Fine"), `description`
    *   `amount`, `paymentMethod` (M-Pesa, Cash), `status` (Paid, Pending)
    *   `receiptNumber`, `timestamp`

### 5. Communication

#### **`Message`**
*   **Purpose**: Represents a single message within a conversation.
*   **Fields**:
    *   `messageId` (PK)
    *   `conversationId` (FK to a `Conversation` model if implemented, or a self-managed thread ID)
    *   `senderId` (FK to `User`)
    *   `recipientId` (FK to `User`)
    *   `content`, `timestamp`, `status` (Sent, Delivered, Read)

### 6. Security and Auditing
Models for ensuring application security and creating an audit trail.

#### **`AuditLog`**
*   **Purpose**: Tracks significant user actions for accountability.
*   **Fields**:
    *   `logId` (PK)
    *   `userId` (FK to `User`)
    *   `action` (e.g., "CASE_UPDATE", "USER_LOGIN_FAILED")
    *   `entityType`, `entityId`
    *   `timestamp`, `ipAddress`, `details`

#### **`AuthToken` & `LoginAttempt`**
*   **Purpose**: Manage user sessions and monitor for security threats.
*   **Note**: The existing designs for these are robust and will be maintained.

### 7. Data Synchronization

#### **`SyncQueueItem`**
*   **Purpose**: Manages the queue of local changes to be sent to the server.
*   **Note**: The existing design is appropriate for its purpose.


# Sync Flags and Logic

This project uses special flags and timestamps to manage syncing between the local database and Firestore for both cases and offenders.

## Sync Flags
- **_is_new**: `true` if the record is newly created locally and needs to be synced to the server. Set to `false` after successful sync.
- **_has_changes**: `true` if the record has been modified locally and needs to be updated on the server. Set to `false` after successful sync.
- **_is_deleted**: `true` if the record is deleted locally (soft delete). The record is not removed, but marked as deleted.
- **_deleted_at**: Timestamp (Long) of when the record was marked as deleted. Used for retention and cleanup.
- **_last_synced_at**: Timestamp (Long) of the last successful sync between local and server. Used for conflict resolution.

## Sync Process
1. **Local Creation**: When a record is created, `_is_new` and `_has_changes` are set to `true`.
2. **Local Update**: When a record is updated, `_has_changes` is set to `true`.
3. **Local Deletion**: When a record is deleted, `_is_deleted` is set to `true` and `_deleted_at` is set to the current time. The record is not removed from the database.
4. **Sync to Server**: During sync, only records with `_is_new` or `_has_changes` as `true` are uploaded/updated. Soft deletes are synced by updating the flags on the server.
5. **After Sync**: After a successful sync, `_is_new` and `_has_changes` are set to `false`, and `_last_synced_at` is updated.
6. **Server Cleanup**: The server can periodically remove records that have been marked as deleted for more than 30 days.

## Example
- Creating a case: `_is_new = true`, `_has_changes = true`
- Updating a case: `_has_changes = true`
- Deleting a case: `_is_deleted = true`, `_deleted_at = now`, `_has_changes = true`

This logic ensures reliable syncing and safe soft deletes for all records.

## Bugs

- Implement a custom toast for consistent notifications throughout the app.
- Sync logic in the UI is not working as expected;

### Auth
- User profiles are not loaded from the sync database, so no profile is available for selection on first login.
- After login, the app does not display the name of the logged-in user.
- Add a dropdown next to "Full Name" on the sign-up form to select a professional title (e.g., Hon, Mr, etc.).
- SharedPreferences does not update when logging in with a different user; previous user data is still shown.

### Cases
- "Last Updated" timestamp is not being saved to the database.
- The "View Case" button layout is distorted.
- Cannot select a judge's name when adding a case.
- Editing a case does not preload the case data in `add_case.xml`.
- `add_case.xml` only shows "Submit" and "Save Case" buttons; it should also have "Cancel" and "Save as Draft".
- "View Case" should be a clickable link, not a button.

### Offenders
- Offender images appear sideways after adding; this issue persists throughout the app.
- Offender details are not loaded when clicking "Edit".
- Soft delete does not work when deleting an offender from the detail page.
- Adding an offender causes the case to be added multiple times (e.g., 4 times).

> **Supporting Logs**
- `getFilteredOffenders` returns 12 results, but offenders are duplicated in the logs.
- `updateOffendersList` shows 12 offenders, but the same offender appears multiple times.

- Search functionality is broken.
- In offender details, the linked case is not correct.
- **FATAL EXCEPTION:** OutOfMemoryError when clicking the "Add" button, caused by large data allocation in the database cursor.

### Staff
- Most staff features are missing.
""