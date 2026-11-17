# TsukiNotes

A personal notes app for saving links, notes, images, text files and code files, organising them into folders, and finding them again.

- **Accounts:** sign up and log in with an email and password. Repeated failed logins are paused for 15 minutes. Forgotten passwords can be reset through a link sent by email.
- **Uploads:** upload images, text files and code files. Each upload is tagged automatically by type (`image`, `text`, or `code` + the language, e.g. `python`).
- **Links and notes:** save a URL or a short note with your own tags.
- **Folders:** group files into folders (e.g. one per class), rename and delete them, and move items between them. A file can be in several folders. **Every uploaded file must always be in at least one folder.**
- **Delete items:** deleting an uploaded file also removes it from disk.

The home screen has two tabs:

| Tab | What it does |
|---|---|
| **My files** | Upload box, a search bar, your folders, and the list of your items. The search matches any part of a title, filename, link/note text or tag (ignoring capitals), and can be combined with a folder. |
| **Search** | Finds items that have **all** of the tags you enter (exact tag names, comma separated). |

Every user sees only their own items and folders.

## Tech stack

| Part | Tech |
|---|---|
| Frontend (`web/`) | React 19, Vite |
| Backend (`api/`) | Java 21, Spring Boot 4, Spring Data JPA |
| Database | PostgreSQL 16 (via Docker), migrations with Flyway |

## Prerequisites

- **Java 21** (no separate Maven install needed; the project includes `mvnw`)
- **Node.js** 20.19+ or 22.12+
- **Docker Desktop** (for the database)

## Getting started

Run each step from the repository root, each long-running command in its own terminal.

**1. Start the database and the local mail server**

```bash
docker compose up -d
```

This starts PostgreSQL on port 5432 (database `tsukinote`) and **Mailpit**, a development mail server that catches every email the API sends instead of delivering it. Open http://localhost:8025 to read them, e.g. to click password reset links.

**2. Start the API** (http://localhost:8080)

```bash
cd api
./mvnw spring-boot:run        # Windows: .\mvnw spring-boot:run
```

Database migrations run automatically on startup. Wait for `Started ApiApplication` in the output.
Start the API from the `api/` folder: uploaded files are saved in `api/uploads/`, relative to where it's started.

**3. Start the frontend** (http://localhost:5173)

```bash
cd web
npm install
npm run dev
```

Open http://localhost:5173 and create an account.

## Running the tests

**Backend** (needs Docker running; your real database is never touched):

```bash
cd api
./mvnw test          # Windows: .\mvnw test
```

Each run starts a throwaway PostgreSQL 16 in Docker (via [Testcontainers](https://testcontainers.com/)), applies every migration to it, and runs the tests against the real API. Emails are captured instead of sent, and uploads go to a temporary folder. It takes about 20 seconds.

| Test class | Covers |
|---|---|
| `AuthTest` | Sign-up validation, passwords stored as bcrypt, log-in, session tokens (hashed, expiring), log-out, the invite token |
| `LoginLimitsTest` | Warnings and the 5-per-email pause, correct password refused while paused, unknown emails behave identically, the 20-per-IP limit |
| `PasswordResetTest` | The email and link, one-time links, logout everywhere, newest-link-only, 3 emails per hour (also under simultaneous requests), expired and fake links |
| `UploadTest` | Automatic tags, fake/binary/unsupported files rejected without leaving files behind, folder required, files served back safely |
| `FoldersAndItemsTest` | Folders (names, rename, delete rules), moving and deleting items, both searches, and that users can never see or change each other's things |
| `SearchViewTest` | The search view is materialized with a GIN index on tags, and never goes stale (folder deletes, direct SQL changes, rolled-back uploads) |
| `FileClassifierTest` | Every allowed extension and every rejection, without a database |
| `ApiApplicationTests` | The app starts and all migrations apply to an empty database |

**Search benchmark** (skipped in normal runs; takes about a minute):

```bash
cd api
./mvnw test -Dtest=SearchBenchmarkTest -Dbenchmark=true
```

It fills a throwaway database with 100,000 items (50 tags, 3 per item), runs the app's multi-tag AND search 30 times against the old plain view and the materialized view, and prints the median PostgreSQL execution times, the cost of a refresh, and both query plans. The report is also saved to `api/target/search-benchmark.txt`. On a Windows laptop with Docker Desktop it measured about **300 ms → 10.5 ms (~28×)** for the search, with each write paying about **0.9 s** to refresh the view.

**Frontend:**

```bash
cd web
npm test             # or: npm run test:watch, to re-run on every save
```

Component tests with [Vitest](https://vitest.dev/) and [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/), in a simulated browser with the API mocked, so the backend doesn't need to be running. They cover log in, sign up, forgot password with resend, choosing a new password, opening a reset link, uploading (folder required, size limit), deleting, moving between folders, and that `javascript:` links are never clickable.

Frontend tests sit next to the code they test (`web/src/*.test.jsx`, shared setup in `web/src/test/setup.js`); backend tests are in `api/src/test/java`.

## Configuration

Backend settings are in [`api/src/main/resources/application.yaml`](api/src/main/resources/application.yaml):

| Setting | Default | Purpose |
|---|---|---|
| `spring.datasource.*` | local Docker database | Database connection |
| `app.invite-token` | `tsuki-secret-6767` | Required in the `X-Invite-Token` header on every request |
| `app.storage-dir` | `uploads` | Where uploaded files are stored |
| `spring.servlet.multipart.max-file-size` | `10MB` | Upload size limit |
| `app.login-limits.max-failures-per-email` | `5` | Failed logins allowed per email within the window |
| `app.login-limits.max-failures-per-ip` | `20` | Failed logins allowed per IP address within the window, across all emails |
| `app.login-limits.window` | `15m` | How far back failed logins are counted |
| `spring.mail.*` | Mailpit (`localhost:1025`) | SMTP server for outgoing email. See [Sending real email](#sending-real-email-eg-to-gmail) |
| `app.mail.from` | `TsukiNotes <no-reply@tsukinotes.local>` | Sender address on emails |
| `app.frontend-url` | `http://localhost:5173` | Where the web app runs; used to build links in emails |
| `app.password-reset.token-lifetime` | `30m` | How long a reset link works |
| `app.password-reset.max-emails-per-hour` | `3` | Reset emails one account can receive per hour |

The frontend's API address and invite token are at the top of [`web/src/api.js`](web/src/api.js).
The allowed frontend origin (`http://localhost:5173`) is set in [`CorsConfig.java`](api/src/main/java/com/tsukinotes/api/config/CorsConfig.java).

> The database password and invite token are currently stored in the code. Move them to environment variables before deploying anywhere public.

### Sending real email (e.g. to Gmail)

By default, **emails never reach a real inbox**: they're caught by Mailpit (http://localhost:8025). To deliver them for real:

1. Copy [`api/local.example.yaml`](api/local.example.yaml) to `api/local.yaml`. Git ignores `local.yaml`, so your password is never committed. Anything in it overrides `application.yaml`.
2. Fill in your provider's SMTP details. The template is set up for **Gmail**, which needs an **app password**, not your normal Google password:
   - turn on 2-Step Verification at https://myaccount.google.com/security
   - create an app password at https://myaccount.google.com/apppasswords and paste the 16 letters into `local.yaml`
   - use your Gmail address as both `username` and `from`
3. Restart the API. Its startup output says where emails go and whether it could log in to the mail server, e.g.
   `Emails go to smtp.gmail.com:587 (real delivery). Connection check OK; sending as ...`

Instead of `local.yaml`, you can set the environment variables `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH=true` and `MAIL_STARTTLS=true`.

Gmail allows roughly 500 emails a day from a personal account, which is fine for development. For a public site, use a transactional email service (Resend, SendGrid, Amazon SES, …); they give you SMTP settings to use the same way. Also set `app.frontend-url` to the site's real address so links in emails point to it.

## Project structure

```
api/                         Spring Boot backend
  src/main/java/com/tsukinotes/api/
    auth/                    Invite-token filter, session-token check (AuthInterceptor), failed-login limits (LoginAttemptService), token helpers (SecureTokens)
    config/                  CORS, error handling, which routes need a login, background tasks (AsyncConfig)
    controller/              REST endpoints (auth, items, folders, search)
    domain/                  JPA entities
    files/                   Upload type detection (FileClassifier) and disk storage
    mail/                    Outgoing email (MailService)
    repository/              Database access (the My files search query is in ItemSearchViewRepository)
    service/                 Business logic (accounts, sessions, password resets, items, folders, tags)
  src/main/resources/
    application.yaml         Configuration
    db/migration/            Flyway SQL migrations (V1, V2, ...)
web/                         React frontend
  src/
    api.js                   All calls to the backend
    App.jsx                  Shows the login page or the home screen; picks up reset links (/reset-password?token=...)
    AuthPage.jsx             Log in, sign up, forgot password, choose a new password
    HomeScreen.jsx           Header and tabs
    FilesTab.jsx             My files tab: search bar, item list, add link/note form
    UploadSection.jsx        File upload box, with folder picker
    FolderSection.jsx        Folder list: filter, create, rename, delete
    SearchTab.jsx            Search tab (all-tags search)
    ItemCard.jsx             One item: preview, folders, tags, "Move to folders" and "Delete"
    FilePreview.jsx          Image / text preview and download for uploads
    format.js                Small display helpers (file sizes)
docker-compose.yml           PostgreSQL and Mailpit (development mail server) for local development
```

## Data model

| Table | Holds |
|---|---|
| `identities` | User accounts: name, email, bcrypt password hash |
| `sessions` | Login sessions: user, SHA-256 hash of the token, expiry |
| `login_failures` | Recent failed logins (email, IP address, time), used for the login limits |
| `password_reset_tokens` | Reset links: user, SHA-256 hash of the token, created, expires, used |
| `items` | Links, notes and uploads. For uploads, also the stored file name, original file name, content type and size |
| `tags`, `item_tags` | Tag names, and which items have which tags |
| `folders`, `item_folders` | Each user's folders, and which items are in which folders |
| `item_search_view` | A **materialized** view: each item with its tags (as an array) and folder IDs; the item list and both searches read from it. A GIN index on `tags` makes multi-tag AND search (`tags @> '{a,b}'`) an index lookup. Database triggers refresh it once when any change to items, tags or folders commits, in the same transaction, so a search right after an upload always finds it |

Deleting a folder removes its `item_folders` rows only; the items stay.

## How login works

1. `POST /auth/login` (or `/auth/signup`) checks the password (stored as a bcrypt hash) and returns a random **session token**.
2. The frontend saves the token and sends it on every request as `Authorization: Bearer <token>`.
3. The server looks the token up in the `sessions` table to see who is asking. Only a SHA-256 hash of each token is stored. Sessions last 30 days.
4. `POST /auth/logout` deletes the session, so the token stops working immediately.

### Failed login limits

Failed logins are recorded in the `login_failures` table and counted over the last 15 minutes:

- **Per email:** after 5 failures, logins for that email are paused. The login page warns when 2 and 1 attempts are left.
- **Per IP address:** after 20 failures from one address (across any emails), logins from it are paused. This stops someone trying one password against many accounts.
- While paused, the server answers `429 Too Many Requests` with a `Retry-After` header **before checking the password**, so even the right password is refused until the pause ends.
- The limits apply the same way whether or not the email has an account, so they don't reveal which emails are registered.
- A successful login clears that email's failures. Rows older than a day are deleted automatically.
- The IP is the direct connection's address. If the API is ever put behind a reverse proxy, change [`AuthController.java`](api/src/main/java/com/tsukinotes/api/controller/AuthController.java) to use the proxy's forwarded address, or every user will share one IP.

### Password reset

1. **Forgot your password?** on the login page asks for an email and calls `POST /auth/password-reset/request`.
2. If the email has an account, the API emails a link to `<frontend-url>/reset-password?token=...`. The page always shows the same "if an account exists, we've sent a link" message, and the request is answered before any work is done, so neither the message nor the response time reveals whether the email is registered.
   The confirmation screen has a **Resend email** button (available again after 60 seconds) and **Use a different email**. Each resend retires the previous link, so only the newest email's link works.
3. Opening the link shows **Choose a new password**. The app removes the token from the address bar straight away so it doesn't stay in the browser history.
4. `POST /auth/password-reset/confirm` sets the new password (same 8–72 character rules as sign-up), then:
   - the link stops working (each link works once, and requesting a new one retires the old one)
   - the user is **logged out on every device**, in case someone else had access
   - any login pause on that email is cleared, so they can log in straight away

Links expire after 30 minutes. Each account gets at most 3 reset emails per hour, so the form can't be used to flood someone's inbox. Only a SHA-256 hash of each token is stored, and links are never written to the logs. If the mail server is down, the user still sees the normal message and the API logs a warning.

## API

Every request needs the `X-Invite-Token` header. Every endpoint except signup, login and the two password-reset endpoints also needs `Authorization: Bearer <token>`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/signup` | Create an account. Body: `{ name, email, password }`. Returns `{ token, identity }` |
| `POST` | `/auth/login` | Log in. Body: `{ email, password }`. Returns `{ token, identity }`, or `429` after too many failures (see above) |
| `POST` | `/auth/password-reset/request` | Email a reset link. Body: `{ email }`. Always `204`, whether or not the account exists |
| `POST` | `/auth/password-reset/confirm` | Set a new password. Body: `{ token, password }`. `204`, or `400` if the link is invalid, used or expired |
| `GET` | `/auth/me` | The logged-in user |
| `POST` | `/auth/logout` | End the current session |
| `GET` | `/items?q=&folderId=` | Your items, newest first. Optional `q` matches part of a title, filename, link/note text or tag (ignoring capitals); optional `folderId` limits to one folder. Each item includes its `tags` and `folderIds` |
| `POST` | `/items` | Save a link or note. Body: `{ uniqueKey, type, content, tags }` |
| `POST` | `/items/upload` | Upload a file (multipart): `file` and **at least one** `folderIds` (repeat the field for several), plus optional `title` and comma-separated `tags` |
| `GET` | `/items/{id}/file` | Download an uploaded file |
| `DELETE` | `/items/{id}` | Delete an item (and its file, for uploads) |
| `PUT` | `/items/{id}/folders` | Set exactly which folders an item is in. Body: `{ folderIds: [...] }`. This is how items are moved |
| `GET` | `/folders` | Your folders, alphabetically, each with `itemCount` |
| `POST` | `/folders` | Create a folder. Body: `{ name }`. Names are unique per user, ignoring capitals, up to 100 characters |
| `PATCH` | `/folders/{id}` | Rename a folder. Body: `{ name }` |
| `DELETE` | `/folders/{id}` | Delete a folder (the files in it are kept). Refused if an uploaded file is only in this folder |
| `GET` | `/search?tags=a&tags=b` | Your items that have **all** the given tags (exact names) |

## File uploads

| Kind | Extensions | Automatic tags |
|---|---|---|
| Image | `.png .jpg .jpeg .gif .webp` | `image` |
| Text | `.txt` / `.md` | `text` / `markdown` |
| Code | `.py .js .jsx .mjs .ts .tsx .java .kt .c .h .cpp .cc .hpp .cs .go .rs .rb .php .swift .lua .sql .sh .bash .ps1 .html .css .json .yaml .yml .xml` | `code` + the language (e.g. `python`, `typescript`) |

- Files are checked against their contents: a renamed binary or a fake image is rejected.
- Files are stored on disk under random names; the original name is kept in the database.
- Text and code files are always served as plain text, so uploaded HTML is shown, never run. SVG is not allowed because it can contain scripts.
- Uploading a file with a title you already use saves it as "title (2)", "title (3)", and so on.
- When a folder is open in My files, it's pre-selected in the upload's folder picker.
- Uploading into a folder that isn't yours is rejected, and nothing is saved.
- To support a new extension, add it to [`FileClassifier.java`](api/src/main/java/com/tsukinotes/api/files/FileClassifier.java) **and** to `ACCEPTED` in [`UploadSection.jsx`](web/src/UploadSection.jsx).

**Every upload must always be in a folder** (images, text and code alike). The server enforces this everywhere, not just in the form:

| Action | Result for an uploaded file |
|---|---|
| Upload with no folder chosen | Rejected: "Uploaded files must be put in at least one folder" |
| Remove it from its last folder | Rejected with the same message |
| Delete the only folder it's in | Rejected: "1 file is only in "X". Move it to another folder or delete it first." |

Links and notes saved through **+ Add link or note** aren't uploads, so they can be in no folder.

Deleting an item asks for confirmation once, then the card disappears immediately and, for uploads, the file is removed from disk.

## Database changes

Never edit a migration that has already run. Add a new file in `api/src/main/resources/db/migration/` with the next unused number (the latest is `V13__materialize_item_search_view.sql`, so the next would be `V14__describe_change.sql`); it runs automatically the next time the API starts.

If you change `item_search_view`, drop and recreate it in the migration with `DROP MATERIALIZED VIEW` / `CREATE MATERIALIZED VIEW`, recreate its three indexes (the unique one on `id` is required by the refresh), and update [`ItemSearchView.java`](api/src/main/java/com/tsukinotes/api/domain/ItemSearchView.java) to match.

## Troubleshooting

**Requests fail with `404` and a JSON body containing `timestamp` / `status` / `path`**
The running API is older than the frontend and doesn't have that endpoint. Stop it and start it again.

**`BUILD FAILURE` when starting the API, with "Port 8080 was already in use" above it**
An old API process is still running. Find and stop it:

```powershell
# Windows (PowerShell)
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object OwningProcess
Stop-Process -Id <that PID>
```

```bash
# macOS / Linux
lsof -i :8080
kill <that PID>
```

**The page only says "Failed to fetch"**
The API isn't running or isn't reachable at `http://localhost:8080`.

**The API can't connect to the database**
Make sure Docker Desktop is running, then run `docker compose up -d`.

**"Too many failed login attempts" while developing locally**
Wait for the pause to end, or clear the recorded failures:

```bash
docker exec tsukinote_db psql -U tsukinote_user -d tsukinote -c "DELETE FROM login_failures"
```

**Password reset emails don't arrive (e.g. nothing in Gmail)**
- Look at the API's startup output for the line starting `Emails go to`. If it says **Mailpit**, emails are caught locally, never delivered: read them at http://localhost:8025, or set up real sending (see [Sending real email](#sending-real-email-eg-to-gmail)).
- If it says the mail server **can't be used**, fix what it reports. For Gmail, an "authentication failed" error almost always means you used your normal password instead of an app password.
- Look for `Could not send password reset email` in the API's output after requesting a reset.
- Check your spam folder. Each account gets at most 3 reset emails per hour; extra requests are silently ignored.

**Frontend tests all fail with "Vitest failed to find the current suite"** (Windows)
Your terminal's path starts with a lowercase `c:\` while files resolve to `C:\`, so Vitest loads itself twice. Open the terminal at a path starting with a capital `C:\` (e.g. `cd C:\Users\...\TsukiNotes\web`) and run `npm test` again.

**Backend tests fail with "Could not find a valid Docker environment"**
Start Docker Desktop: the tests need it to run their throwaway database.

**Uploaded files show "Couldn't load file"**
The API was probably started from a different folder than before, so it's looking in a different `uploads/` directory. Start it from `api/`.

## Not built yet

- Choosing folders when creating a link or note (use **Move to folders** afterwards)
- Editing an item's title or tags
- Searching inside the text of uploaded files
- Email verification (confirming new accounts own their email address)
- Reading the database password and invite token from environment variables instead of the code
