# CareerAI — Public Demo Deployment Guide

A step-by-step guide for putting CareerAI online as a **portfolio demo**. Nothing here has been deployed yet. Every value shown in `<angle brackets>` is a placeholder — never paste real keys into files in this repository.

Background and the reasons behind each setting: [deployment-backend.md](deployment-backend.md).

---

## 1. What you are deploying

```
Browser ──HTTPS──▶ Frontend (static React build: Netlify or Vercel)
   │
   ├──HTTPS──▶ Backend (Spring Boot 4, Java 21, Docker) ──▶ MySQL 8 (new, empty)
   │                 └──▶ Gemini · JDoodle · Simli · Razorpay (server-side keys)
   └──HTTPS/WebRTC──▶ Vapi · Simli · Razorpay Checkout (public keys / short-lived tokens only)
```

- The backend needs **no WebSockets**; Vapi and Simli connect from the browser to their own services.
- Resumes are stored **in MySQL** (`resume_file_blobs`) in production, so no disk or storage bucket is needed.
- The demo uses a **new empty database**. Do not copy your local users, resumes, billing history or webhook records.
- Razorpay stays in **Test Mode**.

## 2. Choose a hosting option

| Option | Frontend | Backend | MySQL | Approx. monthly cost* | Trade-offs |
|---|---|---|---|---|---|
| **A (recommended)** | Netlify Free | Railway Hobby (Docker) | Railway MySQL (same project) | ~US$5 plan incl. US$5 usage, plus usage above that — estimate **US$8–15** | Always on, private network between backend and DB, one dashboard. Usage-based bill: set a usage limit. |
| B (cheapest) | Netlify Free | Render Free web service | Aiven for MySQL Developer | **~US$5** (Aiven) | Backend sleeps after 15 idle minutes; first request then waits for wake-up + Java startup (can exceed a minute). 512 MB RAM is tight. 750 free hours/month per workspace. |
| C (fixed price) | Netlify Free | Render Starter | Aiven for MySQL Developer | **~US$12** (US$7 + US$5) | Always on, predictable bill; 512 MB RAM is tight for Spring Boot. |

\*Prices change. Check each provider's pricing page before signing up. Aiven's **free** MySQL tier stops accepting new services on 6 Nov 2026 and is retired on 21 Dec 2026, so it is not an option.

Vercel Hobby also works for the frontend (`frontend/vercel.json` is included) but is limited to non-commercial use; Netlify Free allows commercial use. Pick one.

The steps below follow **Option A**, with notes for B/C.

## 3. Accounts you need

| Account | Used for | Needed when |
|---|---|---|
| GitHub | Hosts the code that the platforms build from | Always (push the repo first) |
| Railway (or Render + Aiven) | Backend + MySQL | Always |
| Netlify (or Vercel) | Frontend | Always |
| Google AI Studio (Gemini) | AI features | Optional |
| Vapi | Voice interviews | Optional |
| Simli | Interviewer avatar | Optional |
| JDoodle | Coding execution | Optional |
| Razorpay (Test Mode) | PRO checkout demo | Optional |

## 4. Before you start (on your PC)

1. All tests pass: `cd careerai-backend; .\mvnw.cmd test` and `cd frontend; npm test; npm run lint; npm run build`.
2. Commit and push to GitHub (your own decision — see the publish commands in your earlier notes). The platforms build from GitHub.
3. Generate a JWT secret and keep it in a password manager (not in the repo):
   PowerShell: `$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`

## 5. Database (Option A: Railway MySQL)

1. Railway → **New Project** → **Deploy MySQL** (database service).
2. Check the version: open the MySQL service → **Data/Query** tab (or connect with a client) and run `SELECT VERSION();`. It must be **8.x** (the migrations are written and tested for MySQL 8.0). If the template offers a newer major version, pick a MySQL 8.4 image instead.
3. Leave the database **empty**. Flyway creates all tables on the backend's first start.
4. Turn on **backups** for the MySQL volume in the dashboard (Railway does not enable them for you).

Option B/C (Aiven): create an **Aiven for MySQL** service on the Developer plan, region close to the backend. Note host, port, user, password and database name (`defaultdb`). Aiven requires TLS: add `sslMode=REQUIRED` to the JDBC URL.

## 6. Backend

### 6.1 Create the service (Option A: Railway)

1. In the same Railway project → **New** → **GitHub Repo** → select the repository.
2. Service **Settings**:
   - **Root Directory:** `/careerai-backend` (Railway then uses `careerai-backend/Dockerfile`).
   - **Healthcheck Path:** `/actuator/health`; healthcheck timeout: 300 seconds (Java startup on small instances is slow).
   - **Networking:** generate a public domain (HTTPS is provided). Note it: `https://<backend-domain>`.
3. **Variables** (service → Variables). Railway can reference the MySQL service's variables; check the exact names in the MySQL service's Variables tab:

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (already set in the Dockerfile; setting it again is fine) |
| `DB_URL` | `jdbc:mysql://<mysql-private-host>:<port>/<database>?serverTimezone=UTC` |
| `DB_USERNAME` | `<mysql user>` |
| `DB_PASSWORD` | `<mysql password>` |
| `JWT_SECRET` | `<the secret you generated>` |
| `CORS_ALLOWED_ORIGINS` | `https://<frontend-domain>` (exact, no trailing slash; add the frontend URL after step 7, then redeploy) |
| `TRUSTED_PROXIES` | `10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,100.64.0.0/10,fc00::/7` (see 6.3) |
| `GEMINI_API_KEY`, `SIMLI_API_KEY`, `JDOODLE_CLIENT_ID`, `JDOODLE_CLIENT_SECRET` | optional |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | optional, **test keys only** (`rzp_test_...`) |

Do **not** set `FLYWAY_BASELINE_ON_MIGRATE` for a new database. Do **not** set `RESUME_STORAGE_PROVIDER` (prod defaults to `database`).

Option B/C (Render): **New → Web Service** → connect the repo → Language **Docker**, Root Directory `careerai-backend`, Health Check Path `/actuator/health`, plan Free or Starter. Add the same variables under **Environment**. Render sets `PORT` itself; the app reads it.

### 6.2 First deploy — what the log should show

```
Successfully validated 2 migrations
Creating Schema History table ...
Migrating schema ... to version "1 - baseline schema"
Migrating schema ... to version "2 - resume file blobs"
Successfully applied 2 migrations ... now at version v2
Resume storage provider: database
Started CareeraiBackendApplication in ... seconds
```

Then open `https://<backend-domain>/actuator/health` → `{"status":"UP",...}`.

### 6.3 Trusted proxies (correct client IPs for login rate limiting)

The platform's proxy terminates HTTPS and forwards requests from a private address. The backend is reachable only through that proxy, so trusting the private ranges above is safe. If you see this log line, your proxy address is not covered yet:

`Requests arrive through a proxy at <address>, but app.security.trusted-proxies (TRUSTED_PROXIES) is not set`

Add that address (or its range) to `TRUSTED_PROXIES` and redeploy.

## 7. Frontend (Netlify)

1. Netlify → **Add new site → Import an existing project** → GitHub → select the repository.
2. Build settings:
   - **Base directory:** `frontend`
   - **Build command:** `npm run build`
   - **Publish directory:** `frontend/dist` (Netlify shows it as `dist` relative to the base directory)
3. **Environment variables** (Site configuration → Environment variables) — these are compiled into the public JavaScript, so only public values:

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | `https://<backend-domain>/api` |
| `VITE_VAPI_PUBLIC_KEY` | `<Vapi public key>` (optional) |
| `VITE_VAPI_ASSISTANT_ID` | `<Vapi assistant id>` (optional) |

4. Deploy. `frontend/public/_redirects` makes every route (for example `/practice/sql` or `/settings/billing`) load the app on refresh.
5. Copy the site URL (`https://<frontend-domain>`), put it in the backend's `CORS_ALLOWED_ORIGINS`, and redeploy the backend.

Changing a `VITE_*` variable requires a **new frontend deploy** (values are baked in at build time).

Vercel instead: import the repo, Root Directory `frontend`, framework Vite; same environment variables. `frontend/vercel.json` provides the rewrites.

## 8. External services: localhost → public HTTPS

| Service | Where the key lives | What to change for the public domain | Safe verification (one small call each) |
|---|---|---|---|
| Gemini | Backend `GEMINI_API_KEY` | Nothing domain-specific. If the key has "HTTP referrer" restrictions, they do not apply to server calls — use API restrictions (Generative Language API) instead. Watch quotas. | Send one AI Coach message; check the reply is not a fallback. |
| Vapi | Frontend `VITE_VAPI_PUBLIC_KEY`, `VITE_VAPI_ASSISTANT_ID` | In the Vapi dashboard, add `https://<frontend-domain>` to the public key's allowed origins (if you restricted it). Microphone needs HTTPS (provided). Calls use paid minutes. | One short voice interview; end it after a question. |
| Simli | Backend `SIMLI_API_KEY` (+ optional `SIMLI_FACE_ID`) | Check the Simli dashboard for allowed domains. The browser only receives short-lived tokens from the backend. Sessions use minutes. | Start one voice interview with the avatar; confirm it appears, then end. |
| JDoodle | Backend `JDOODLE_CLIENT_ID`, `JDOODLE_CLIENT_SECRET` | Nothing domain-specific. Free plan: limited credits per day; one Run or Submit = one credit. | One **Run Code** on a coding question. |
| Razorpay (Test Mode) | Backend `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Create the webhook on the public backend URL (section 9). Keep the Dashboard in Test Mode. | One test-card payment on `/pricing`. |

## 9. Razorpay Test Mode webhook

1. Razorpay Dashboard → switch to **Test Mode** → Account & Settings → **Webhooks** → Add New Webhook.
2. **Webhook URL:** `https://<backend-domain>/api/billing/razorpay/webhook`
3. **Secret:** a new random value you choose; put the same value in the backend's `RAZORPAY_WEBHOOK_SECRET` and redeploy.
4. **Events:** `payment.captured`, `payment.failed`, `order.paid`.
5. Test: buy PRO with a Razorpay **test** card on `/pricing`. In the backend log you should see `WEBHOOK_PROCESSED: payment.captured ... ACTIVATED` (or `ALREADY_ACTIVE` when the browser verification already activated it).

Never switch to Live Mode for the demo.

## 10. Verify the deployed application

Work through this list in a normal browser window and a phone:

- [ ] `https://<backend-domain>/actuator/health` → `UP`
- [ ] Frontend home page loads over HTTPS; no browser console errors
- [ ] Register a new account → onboarding → dashboard
- [ ] Refresh on `/dashboard`, `/practice/sql`, `/settings/billing` → still signed in, no 404
- [ ] Back/Forward buttons work
- [ ] Log out → protected pages redirect to login; log in again
- [ ] SQL practice: Run Query shows rows; Submit saves a result
- [ ] Upload a PDF resume → ATS Analyzer shows a result → download the file → delete the resume
- [ ] Pricing shows FREE/PRO limits; Billing page loads
- [ ] Device check (`/ai-interview/device-check`) asks for microphone/camera and shows "Ready"
- [ ] Optional services: one call each as in section 8
- [ ] Phone: layout fits the screen; the menu shows **Dashboard** when signed in
- [ ] Six wrong passwords for one account → the sixth answers "Too many login attempts"

## 11. Common errors and fixes

| Symptom | Cause | Fix |
|---|---|---|
| Browser console: "blocked by CORS policy" | `CORS_ALLOWED_ORIGINS` does not exactly match the frontend URL | Use `https://<frontend-domain>` exactly (scheme + host, no path, no trailing slash); redeploy backend |
| Frontend calls `http://localhost:8080` | `VITE_API_BASE_URL` was missing at build time | Set it in Netlify/Vercel, then trigger a new frontend deploy |
| "Mixed content" errors | `VITE_API_BASE_URL` uses `http://` | Use the backend's `https://` URL |
| 404 when refreshing a page | Rewrites not applied | Check `_redirects` is in `dist/` (Netlify) or `vercel.json` is in the project root directory (Vercel) |
| Backend exits: "JWT signing secret is not set" / "publicly known placeholder" | `JWT_SECRET` missing or weak | Set a newly generated secret |
| Backend exits: `Could not resolve placeholder 'CORS_ALLOWED_ORIGINS'` | Variable missing | Add it |
| Backend exits: `'url' must start with "jdbc"` | `DB_URL` missing | Add the full JDBC URL |
| `Communications link failure` / `Access denied for user` | Wrong host, port, credentials, or missing TLS | Check the DB variables; add `sslMode=REQUIRED` for Aiven |
| `Found non-empty schema(s) ... but no schema history table` | The database is not empty | Use a new empty database (do not baseline the demo database) |
| `Schema-validation: missing table/column` | Schema differs from the entities | Use a new empty database so Flyway creates it |
| Deploy marked failed while the log says "Started" | Health check timed out during slow startup | Raise the health check timeout (300 s); use a larger instance |
| Service restarts with `OutOfMemoryError` | Instance RAM too small | Use 1 GB RAM, or lower `-XX:MaxRAMPercentage` via `JAVA_TOOL_OPTIONS` |
| Everyone gets "Too many login attempts" | Proxy not trusted, all users share one IP | Set `TRUSTED_PROXIES` (section 6.3) |
| First request takes a minute (Render Free) | Free instance was asleep | Expected on the free tier; use Starter/Railway for always-on |
| Microphone/camera not available | Page opened over `http://` | Use the `https://` frontend URL |
| Razorpay webhook returns 400 | Webhook secret mismatch | Make the dashboard secret and `RAZORPAY_WEBHOOK_SECRET` identical |
| "Payments are temporarily unavailable" | Razorpay keys not set | Add the test keys and redeploy |

## 12. Roll back a failed deployment

- **Backend:** Railway → service → **Deployments** → choose the last working deployment → Redeploy/Rollback. Render → **Events/Deploys** → Rollback to a previous deploy. A failed health check keeps the previous version serving traffic.
- **Frontend:** Netlify → **Deploys** → open a previous deploy → **Publish deploy**. Vercel → **Deployments** → previous deployment → **Promote to Production** (Instant Rollback).
- **Database:** Flyway migrations only move forward. Before deploying a version that adds a migration, take a backup (Railway volume backup / Aiven backup, or `mysqldump`). If a migration breaks the demo database, restore the backup and redeploy the previous backend version. For the demo it is also acceptable to create a fresh empty database and let Flyway rebuild the schema.
- **Secrets:** if a key leaks, revoke it in the provider dashboard first, then set the new value and redeploy.
