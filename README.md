# CareerAI

**CareerAI — AI-Powered Career Preparation and Mock Interview Platform**

A full-stack web application that helps students and early-career developers prepare for technical jobs in one place: practise coding and SQL against real execution, take AI text and voice mock interviews, improve resumes against job descriptions, and track progress over time.

Built with **React 19 + Vite** on the frontend and **Java 21 + Spring Boot 4** with **MySQL** on the backend.

> Portfolio project — built and tested locally. It is not deployed to production; see [Known limitations](#known-limitations).

---

## Table of contents

- [Problem it solves](#problem-it-solves)
- [Key features](#key-features)
- [Technology stack](#technology-stack)
- [Architecture](#architecture)
- [Installation](#installation)
- [Environment variables](#environment-variables)
- [Running locally](#running-locally)
- [Testing](#testing)
- [Screenshots](#screenshots)
- [Known limitations](#known-limitations)
- [Future improvements](#future-improvements)

---

## Problem it solves

Interview preparation is usually scattered: one site for coding problems, another for SQL, a separate resume checker, and very little realistic interview practice. Candidates rarely get structured feedback or a clear view of their weak areas.

CareerAI brings these together:

- **Practice with real feedback** — code and SQL are actually executed and checked against hidden tests, not judged by an AI guess.
- **Realistic interviews** — text and voice mock interviews with an AI interviewer and a per-question report.
- **Resume readiness** — ATS-style analysis, job-description matching and improvement suggestions.
- **Progress visibility** — performance analytics, skill-gap view and a personal study plan.

## Key features

| Area | What it does |
|---|---|
| **Authentication** | Register / login with BCrypt-hashed passwords and stateless JWT; onboarding captures target role and goals. |
| **Coding practice** | Curated problems in Java, Python, JavaScript and C++. *Run Code* with custom input; *Submit* runs all hidden tests in one sandboxed execution and stores the score. |
| **SQL practice** | Queries run in a disposable in-memory H2 sandbox with a statement guard, timeouts and row limits — never against the application database. Results are compared on visible and hidden data. |
| **Aptitude / technical / system design** | Question sets and descriptive-answer evaluation from Gemini when available, with built-in fallback content (clearly labelled). |
| **AI mock interviews** | Text interviews with AI-generated questions, answer evaluation and a final report. |
| **Voice interviews** | Real-time voice interviewer (Vapi), optional talking avatar (Simli) and an optional local camera self-view (never recorded or uploaded). |
| **AI Career Coach** | Conversational coach with saved conversation history. |
| **Resume tools** | Upload PDF/DOCX or build a resume, version history, ATS analysis (Gemini or rule-based fallback), job-description match, AI wording improvement, and interview preparation from a resume + JD. |
| **Progress** | Dashboard, performance analytics, skill-gap analysis and a personal plan with task tracking. |
| **FREE / PRO plans** | Server-enforced daily/monthly usage limits, usage meters, and a PRO upgrade via Razorpay Checkout (**test mode only**). |

## Technology stack

| Layer | Technologies |
|---|---|
| Frontend | React 19, Vite 8, React Router 7, Axios, Recharts, React Icons, plain CSS |
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation), Hibernate 7, Lombok |
| Database | MySQL 8 (application data), H2 in-memory (SQL practice sandbox and backend tests) |
| Auth | Spring Security, JWT (JJWT, HS256), BCrypt |
| AI & media | Google Gemini API, Vapi Web SDK (voice), Simli (avatar) |
| Code execution | JDoodle Compiler API (default) or Judge0 (optional) |
| Documents | Apache PDFBox, Apache POI |
| Payments | Razorpay Orders API + Checkout (test mode) |
| Tooling | Maven Wrapper, npm, ESLint, JUnit 5, MockMvc |

## Architecture

```
React (Vite SPA) ──HTTPS / JSON + JWT──▶ Spring Boot REST API ──▶ MySQL
                                           │
                                           ├─▶ Gemini API         (AI questions, evaluation, coach, resume AI)
                                           ├─▶ JDoodle / Judge0   (sandboxed code execution)
                                           ├─▶ H2 in-memory       (disposable SQL sandbox)
                                           ├─▶ Simli API          (short-lived avatar session tokens)
                                           └─▶ Razorpay API       (orders, payment verification, webhooks)
Browser ──▶ Vapi (voice, public key) · Simli (avatar stream with a backend-issued token) · Razorpay Checkout
```

**All third-party secrets live only in the backend.** The browser receives only values that are designed to be public (Vapi public key, Razorpay key id, short-lived Simli session tokens).

### Frontend architecture

```
frontend/src
├── pages/        Route-level screens (auth, dashboard, practice, interview, resume, coach,
│                 performance, plan, career/skill gap, settings, subscription, public site)
├── components/   Reusable UI (billing, interview, resume, common, public)
├── layouts/      Public layout and the authenticated user layout (sidebar, plan badge)
├── context/      AuthContext and SubscriptionProvider (React context)
├── services/     API clients — one module per backend area, sharing services/api.js
├── routes/       AppRoutes with protected routes
└── styles/       Page and dashboard stylesheets
```

- `services/api.js` is a shared Axios instance: it attaches the JWT, normalises errors, clears the session on `401`, and raises a global event for `PLAN_LIMIT_REACHED` so one upgrade dialog can respond anywhere in the app.
- Protected routes redirect to login when there is no valid session.
- Some lightweight features (application tracker, achievements/XP, some settings) are stored in the browser's `localStorage` only.

### Backend architecture

Layered Spring Boot application (`com.careerai.backend`):

| Package | Responsibility |
|---|---|
| `controller` | REST endpoints under `/api/**` (auth, profile, practice, coding, sql, interviews, coach, resumes, avatar, performance, skill-gap, plan, billing) |
| `service` | Business logic, ownership checks, AI fallbacks |
| `billing` | Plan catalog, usage metering, entitlements, Razorpay gateway, subscription activation |
| `execution` | Pluggable code-execution providers (JDoodle, Judge0, disabled) |
| `practice` | Question catalogs, hidden-test harness, SQL sandbox and statement guard, rate limiter |
| `ai` | Gemini provider, prompt building, response parsing, retry/timeout handling |
| `security` | JWT service and filter, user details, JSON 401/403 handlers |
| `entity` / `repository` | JPA entities and Spring Data repositories |
| `exception` | Domain exceptions and a global handler that never leaks stack traces |
| `config` | Security/CORS configuration and a guard that stops tests from using a non-test database |

### MySQL database

Main tables (created by Hibernate during development):

- `users`, `profiles`, `profile_skills` — accounts and onboarding data
- `practice_sessions`, `practice_results` — practice attempts and scores
- `interview_sessions`, `interview_questions`, `interview_answers` — mock interviews and reports
- `coach_conversations`, `coach_messages` — AI coach history
- `resumes`, `resume_versions`, `resume_analyses`, `job_match_analyses` — resume tools
- `plan_task_completions` — personal plan progress
- `ai_usage_logs` — AI request log used for per-user hourly AI limits
- `user_usage`, `user_subscriptions`, `payment_webhook_events` — plans, metered usage and payments

### JWT authentication flow

1. `POST /api/auth/register` or `/api/auth/login` — the password is checked against a BCrypt hash.
2. The backend returns a signed HS256 JWT (24 h by default) containing the user id, email, role and plan.
3. The frontend stores the token and sends `Authorization: Bearer <token>` on every request.
4. `JwtAuthenticationFilter` validates the signature and expiry on each request; the API is stateless (no server sessions).
5. The current user is always taken from the token — never from a request body — and every resource lookup is scoped to that user.
6. An invalid or expired token returns a JSON `401`; the frontend clears the session and redirects to login.

### AI integrations

| Service | Used for | How it is protected |
|---|---|---|
| **Gemini** | Interview questions and evaluation, coach replies, practice questions, ATS analysis, job match, resume improvement, interview preparation | Server-side key; timeouts, limited retries, strict JSON parsing, per-user hourly limit; rule-based fallbacks where possible |
| **Vapi** | Real-time voice interviewer | Browser uses the public key only; transcripts are sent to the backend for evaluation |
| **Simli** | Talking interviewer avatar (visual layer only) | API key stays on the server; the browser gets a short-lived session token; per-interview session cap |

If a provider is not configured, the application still starts and the affected feature reports that it is unavailable.

### Coding and SQL practice

- **Candidate code is never executed inside the Spring Boot JVM.** It is sent to an external sandbox (JDoodle by default, Judge0 optional) with server-decided limits.
- On *Submit*, all hidden test cases are combined into a single execution by a test harness (one provider call per submission), and only pass/fail details are returned — expected outputs stay hidden.
- **SQL** runs in a fresh in-memory H2 database per request, seeded with the question's dataset. A statement guard blocks unsafe statements, and query timeouts, row/column limits and a concurrency cap apply. The application's MySQL database is never touched.
- Per-user rate limits apply to run and submit requests.

### FREE / PRO subscription architecture

- **Plan catalog** — FREE and PRO limits per feature (daily for interviews, coach, coding and SQL; monthly for resume AI), overridable through configuration.
- **Atomic usage metering** — each metered action reserves usage with a conditional database update, so parallel requests cannot exceed the limit. If the action fails because of the server or a provider (AI error, execution provider outage), the reservation is released.
- **Enforcement on the server** — limits are checked in the service layer; over-limit requests return `429 PLAN_LIMIT_REACHED` with the usage details, which the frontend shows in an upgrade dialog.
- **Payments (Razorpay test mode)** — the backend creates the order with a server-decided price (₹299/month, ₹2,499/year), verifies the payment signature (HMAC-SHA256) and re-checks the payment with Razorpay before activating PRO. A signature-verified webhook acts as a backstop, with event de-duplication and protection against double activation. Renewals extend from the current expiry.
- PRO is time-boxed (no auto-renew), and the effective plan is re-evaluated on every request, so expired subscriptions fall back to FREE automatically.

---

## Installation

### Prerequisites

- Java 21 (JDK)
- Node.js 20+ and npm
- MySQL 8 running locally
- Git

The Maven Wrapper (`mvnw` / `mvnw.cmd`) is included, so a separate Maven installation is not required.

### Clone

```bash
git clone https://github.com/<your-username>/careerai-fullstack-ai-interview-platform.git
cd careerai-fullstack-ai-interview-platform
```

### Backend

```bash
cd careerai-backend
# Configure environment variables (see below), then:
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

The API starts on `http://localhost:8080`. The `careerai` database is created automatically if it does not exist (`createDatabaseIfNotExist=true`) and the tables are created by Hibernate.

### Frontend

```bash
cd frontend
npm install
cp .env.example .env            # Windows: copy .env.example .env
npm run dev
```

The app opens on `http://localhost:5173`.

## Environment variables

Templates with every variable and comments:

- Backend: [`careerai-backend/.env.example`](careerai-backend/.env.example)
- Frontend: [`frontend/.env.example`](frontend/.env.example)

Real values are never committed: `.env` files and `application-local.properties` are git-ignored.

### Backend

| Variable | Required | Purpose |
|---|---|---|
| `DB_URL` | Optional locally | JDBC URL (defaults to local `careerai`) |
| `DB_USERNAME` | Optional locally | Defaults to `root` |
| `DB_PASSWORD` | **Yes** | MySQL password (locally it can live in `application-local.properties`, see [`application-local.properties.example`](careerai-backend/src/main/resources/application-local.properties.example)) |
| `JWT_SECRET` | **Yes for any deployment** | 32+ random characters; the built-in fallback is for local development only |
| `CORS_ALLOWED_ORIGINS` | Yes for deployment | Allowed frontend origins (default `http://localhost:5173`) |
| `GEMINI_API_KEY` | Optional | AI features |
| `SIMLI_API_KEY` | Optional | Interviewer avatar |
| `CODE_EXECUTION_PROVIDER` | Optional | `jdoodle` (default), `judge0` or `none` |
| `JDOODLE_CLIENT_ID`, `JDOODLE_CLIENT_SECRET` | Optional | Coding execution |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | Optional | PRO checkout — **test-mode keys only** |

### Frontend

| Variable | Required | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | Optional locally | Backend URL (default `http://localhost:8080/api`) |
| `VITE_VAPI_PUBLIC_KEY`, `VITE_VAPI_ASSISTANT_ID` | Optional | Voice interviews |

> Every `VITE_*` value is bundled into the public JavaScript. Only public identifiers belong there — never API secrets.

## Running locally

1. Start MySQL.
2. Set `DB_PASSWORD` (environment variable or `application-local.properties`) and optionally any AI / execution keys.
3. Start the backend: `cd careerai-backend && ./mvnw spring-boot:run`.
4. Start the frontend: `cd frontend && npm run dev`.
5. Open `http://localhost:5173`, register an account and complete onboarding.

Without optional keys the core app (auth, dashboard, practice content, SQL practice, resume upload, rule-based ATS analysis, plans) still works; AI, voice, avatar, code execution and checkout show a clear "unavailable" state.

## Testing

```bash
cd careerai-backend
./mvnw test
```

- **106 backend tests** (JUnit 5, Spring Boot Test, MockMvc) covering authentication, code execution providers, the hidden-test harness, the SQL sandbox, plan limits and concurrent usage, checkout, payment verification and webhooks.
- Tests run on an **in-memory H2 database** with a guard that refuses to start against a non-test database. External services (JDoodle, Judge0, Razorpay) are replaced by local fakes — **no real API calls, credits or payments**.

```bash
cd frontend
npm run lint
npm run build
```

## Screenshots

> Screenshots will be added. Suggested location: `docs/screenshots/`.

| Screen | Preview |
|---|---|
| Landing page | _placeholder — `docs/screenshots/landing.png`_ |
| Dashboard | _placeholder — `docs/screenshots/dashboard.png`_ |
| Coding practice | _placeholder — `docs/screenshots/coding.png`_ |
| SQL practice | _placeholder — `docs/screenshots/sql.png`_ |
| Voice interview | _placeholder — `docs/screenshots/voice-interview.png`_ |
| Interview report | _placeholder — `docs/screenshots/interview-report.png`_ |
| Resume ATS analysis | _placeholder — `docs/screenshots/ats.png`_ |
| Pricing / plans | _placeholder — `docs/screenshots/pricing.png`_ |

## Known limitations

- **Payments are test mode only.** The Razorpay flow has been tested against a mock gateway and is designed for Razorpay test mode; live payments have not been tested and are not enabled. There is no auto-renewal, and webhooks need a publicly reachable backend URL.
- **Not deployed yet.** The project runs locally; deployment configuration is a planned next step.
- **No database migrations.** Tables are managed by Hibernate `ddl-auto=update`; a production setup should use Flyway or Liquibase.
- **Local file storage.** Uploaded resumes are stored on the server's disk, which is not durable on most cloud platforms.
- **External service quotas.** Gemini, Vapi, Simli and JDoodle have free-tier limits (JDoodle's free plan allows a limited number of executions per day).
- **Browser-only data.** The application tracker, achievements/XP and some settings use `localStorage` and are not synced across devices.
- **Fallback content.** When AI is unavailable, aptitude/technical practice uses built-in question sets.
- **Per-instance rate limits.** Practice rate limiting is in memory, so it applies per server instance.
- **No automated frontend test suite** is included in the repository; frontend checks are lint and build. Two existing ESLint findings remain.
- **No admin panel.**

## Future improvements

- Deploy (static frontend host + managed Java host + managed MySQL) with CI on every push
- Flyway migrations and cloud object storage for resumes
- Automated frontend tests (Vitest + React Testing Library) and end-to-end tests
- Admin dashboard for content, users and plan management
- Larger question banks and company-specific interview tracks
- Distributed rate limiting (e.g. Redis) for multi-instance deployments
- Razorpay live-mode readiness review and subscription auto-renewal

---

## Author

**Mugdha Kamble** — Full Stack Developer

<!-- Add your LinkedIn / portfolio links here -->
