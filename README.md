# TsukiNotes

A personal notes app for saving links, notes, images, text files and code files, organising them into folders, and finding them again.

- **Accounts:** sign up and log in with an email and password.
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

**1. Start the database**

```bash
docker compose up -d
```

This starts PostgreSQL on port 5432 with the database `tsukinote`.

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

## Configuration

Backend settings are in [`api/src/main/resources/application.yaml`](api/src/main/resources/application.yaml):

| Setting | Default | Purpose |
|---|---|---|
| `spring.datasource.*` | local Docker database | Database connection |
| `app.invite-token` | `tsuki-secret-6767` | Required in the `X-Invite-Token` header on every request |
| `app.storage-dir` | `uploads` | Where uploaded files are stored |
| `spring.servlet.multipart.max-file-size` | `10MB` | Upload size limit |

The frontend's API address and invite token are at the top of [`web/src/api.js`](web/src/api.js).
The allowed frontend origin (`http://localhost:5173`) is set in [`CorsConfig.java`](api/src/main/java/com/tsukinotes/api/config/CorsConfig.java).

> The database password and invite token are currently stored in the code. Move them to environment variables before deploying anywhere public.

## Project structure

```
api/                         Spring Boot backend
  src/main/java/com/tsukinotes/api/
    auth/                    Invite-token filter, session-token check (AuthInterceptor)
    config/                  CORS, error handling, which routes need a login
    controller/              REST endpoints (auth, items, folders, search)
    domain/                  JPA entities
    files/                   Upload type detection (FileClassifier) and disk storage
    repository/              Database access (the My files search query is in ItemSearchViewRepository)
    service/                 Business logic (accounts, sessions, items, folders, tags)
  src/main/resources/
    application.yaml         Configuration
    db/migration/            Flyway SQL migrations (V1, V2, ...)
web/                         React frontend
  src/
    api.js                   All calls to the backend
    App.jsx                  Shows the login page or the home screen
    AuthPage.jsx             Log in, sign up, forgot password
    HomeScreen.jsx           Header and tabs
    FilesTab.jsx             My files tab: search bar, item list, add link/note form
    UploadSection.jsx        File upload box, with folder picker
    FolderSection.jsx        Folder list: filter, create, rename, delete
    SearchTab.jsx            Search tab (all-tags search)
    ItemCard.jsx             One item: preview, folders, tags, "Move to folders" and "Delete"
    FilePreview.jsx          Image / text preview and download for uploads
    format.js                Small display helpers (file sizes)
docker-compose.yml           PostgreSQL for local development
```

## Data model

| Table | Holds |
|---|---|
| `identities` | User accounts: name, email, bcrypt password hash |
| `sessions` | Login sessions: user, SHA-256 hash of the token, expiry |
| `items` | Links, notes and uploads. For uploads, also the stored file name, original file name, content type and size |
| `tags`, `item_tags` | Tag names, and which items have which tags |
| `folders`, `item_folders` | Each user's folders, and which items are in which folders |
| `item_search_view` | A database view joining each item with its tags and folder IDs; the item list and both searches read from it |

Deleting a folder removes its `item_folders` rows only; the items stay.

## How login works

1. `POST /auth/login` (or `/auth/signup`) checks the password (stored as a bcrypt hash) and returns a random **session token**.
2. The frontend saves the token and sends it on every request as `Authorization: Bearer <token>`.
3. The server looks the token up in the `sessions` table to see who is asking. Only a SHA-256 hash of each token is stored. Sessions last 30 days.
4. `POST /auth/logout` deletes the session, so the token stops working immediately.

## API

Every request needs the `X-Invite-Token` header. Every endpoint except signup and login also needs `Authorization: Bearer <token>`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/signup` | Create an account. Body: `{ name, email, password }`. Returns `{ token, identity }` |
| `POST` | `/auth/login` | Log in. Body: `{ email, password }`. Returns `{ token, identity }` |
| `GET` | `/auth/me` | The logged-in user |
| `POST` | `/auth/logout` | End the current session |
| `GET` | `/items?q=&folderId=` | Your items, newest first. Optional `q` matches part of a title, filename, link/note text or tag (ignoring capitals); optional `folderId` limits to one folder. Each item includes its `tags` and `folderIds` |
| `POST` | `/items` | Save a link or note. Body: `{ uniqueKey, type, content, tags }` |
| `POST` | `/items/upload` | Upload a file (multipart): `file`, optional `title`, optional comma-separated `tags`, optional `folderIds` (repeat the field for several) |
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

**Every upload must always be in a folder** (images, text and code alike). The server enforces this everywhere, not just in the form:

| Action | Result for an uploaded file |
|---|---|
| Upload with no folder chosen | Rejected: "Uploaded files must be put in at least one folder" |
| Remove it from its last folder | Rejected with the same message |
| Delete the only folder it's in | Rejected: "1 file is only in "X". Move it to another folder or delete it first." |

Links and notes saved through **+ Add link or note** aren't uploads, so they can be in no folder.

Deleting an item asks for confirmation once, then the card disappears immediately and, for uploads, the file is removed from disk.
- To support a new extension, add it to [`FileClassifier.java`](api/src/main/java/com/tsukinotes/api/files/FileClassifier.java) **and** to `ACCEPTED` in [`UploadSection.jsx`](web/src/UploadSection.jsx).

## Database changes

Never edit a migration that has already run. Add a new file in `api/src/main/resources/db/migration/` with the next unused number (the latest is `V10__add_folders.sql`, so the next would be `V11__describe_change.sql`); it runs automatically the next time the API starts.

If you change `item_search_view`, drop and recreate it in the migration (see `V10__add_folders.sql`), and update [`ItemSearchView.java`](api/src/main/java/com/tsukinotes/api/domain/ItemSearchView.java) to match.

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

**Uploaded files show "Couldn't load file"**
The API was probably started from a different folder than before, so it's looking in a different `uploads/` directory. Start it from `api/`.

## Not built yet

- Choosing folders when creating a link or note (use **Move to folders** afterwards)
- Editing an item's title or tags
- Searching inside the text of uploaded files
- Password reset by email (the "Forgot your password?" page is a placeholder)
- Limits on repeated failed login attempts
- Automated tests
