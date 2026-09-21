# Freerouting Micro-Survey System

## Overview

The **True Zero-Friction Micro-Survey** system (Issue #903) allows Freerouting maintainers to collect targeted, anonymous product feedback directly within the desktop application.

Key design principles:
- **Zero Friction:** Answering a survey requires exactly one click on an option button. There is no submit button, no form, and no external browser redirect.
- **Non-Intrusive:** The survey appears as a subtle, discrete pill button in the status bar at the bottom-right corner of the window (next to the measurement unit label). It never blocks the routing canvas or modal workflows.
- **Ask-Once Guarantee:** Each survey question is asked at most once per user. Once answered or dismissed (via outside click or Escape), it will never be displayed again.
- **Privacy-First:** Responses contain only the survey ID, anonymous installation UUID, selected option, and client version. No board files, network IPs, MAC addresses, or personal data are collected or stored.
- **Opt-In / Opt-Out:** Controlled by the `allow_surveys` user profile setting and the user's telemetry choice. Users can toggle this preference at any time in **Settings > User Settings**.

---

## Architecture & Component Map

```
┌────────────────────────────────────────────────────────┐
│                   Status Bar UI                        │
│   (BoardPanelStatus -> surveyTriggerButton)            │
│                         │                              │
│                         ▼                              │
│              SurveyPopover (Upward Flyout)             │
│                         │                              │
│                         ▼                              │
│              ButtonsSurveyRenderer                     │
└─────────────────────────┬──────────────────────────────┘
                          │ EDT / Presentation
──────────────────────────┼───────────────────────────────
                          │ Headless Pipeline
┌─────────────────────────▼──────────────────────────────┐
│                    SurveyCoordinator                   │
│   ├── Filter eligible surveys (version, expiry, opt-in)│
│   └── Manage lifecycle (dismiss / submit)              │
│            │                             │             │
│            ▼                             ▼             │
│       SurveyCache                   SurveyClient       │
│  (~/.freerouting/surveys.json)      (Async HTTP)       │
└──────────────────────────────────────────┬─────────────┘
                                           │
                                           ▼ REST
┌────────────────────────────────────────────────────────┐
│                   Freerouting API                      │
│   GET  /v1/surveys/active                              │
│   POST /v1/surveys/{surveyId}/response                 │
│                         │                              │
│                         ▼                              │
│                  Google BigQuery                       │
│             (`survey_response` table)                  │
└────────────────────────────────────────────────────────┘
```

### 1. Headless Domain (`app.freerouting.surveys`)
- `SurveyDefinition`: Data-transfer object representing a survey with `id`, `topic`, `question`, `options`, `min_client_version`, `expires_at_utc`, and `schema_version`.
- `SurveyResponsePayload`: Wire payload for submissions containing `survey_id`, `user_id`, `option`, and `client_version`.
- `SurveyCache`: Thread-safe persistence backed by `surveys.json` in the user's Freerouting data directory. Uses atomic file replacement (`.tmp` -> rename) and automatic quarantine recovery if the cache file is corrupted.
- `SurveyClient`: Asynchronous HTTP transport utilizing `CompletableFuture`. Silent failure mode integrated with `AnalyticsErrorAggregator` to suppress repeated network logs when offline.
- `SurveyCoordinator`: Decides whether a survey is eligible (opt-in check, ask-once cache check, version constraint, expiration). Manages one poll per session and immediate cache recording upon response or dismissal.

### 2. GUI Presentation (`app.freerouting.gui.board`, `app.freerouting.gui.surveys`)
- `BoardPanelStatus`: Houses `surveyTriggerButton` in the bottom-right status bar. Hidden when no survey is available. Automatically activates when an active, eligible survey is returned.
- `SurveyPopover`: Lightweight, non-modal `JPopupMenu` with an optimistic 400ms dwell upon selection (providing instant `"✓ <option>"` visual feedback before closing). Employs `showAnchoredAbove()` to open upwards into the board viewport, right-aligned to the trigger button.
- `ButtonsSurveyRenderer`: Renders the topic tag, question label, and prominent one-click option buttons. Disables buttons upon click to prevent double-submissions.

### 3. REST API (`app.freerouting.api.v1`, `app.freerouting.analytics`)
- `GET /v1/surveys/active`: Public endpoint serving the survey JSON defined in the server's `FREEROUTING__SURVEYS__ACTIVE_SURVEY` environment variable (or HTTP 204 No Content if unset/expired).
- `POST /v1/surveys/{surveyId}/response`: Accepts responses and delegates deduplication to BigQuery on `(survey_id, user_id)`.
- `ApiKeyValidationFilter` & `EnvironmentHostValidationFilter`: Bypassed for `/v1/surveys/*` so surveys reach fresh installs without requiring API keys or host header restrictions.

---

## Publishing a Survey (Operator Guide)

To publish a new survey, set or update the `FREEROUTING__SURVEYS__ACTIVE_SURVEY` environment variable on the Freerouting API host. No client release or API redeploy is required.

### Example Active Survey JSON

```json
{
  "schema_version": 1,
  "id": "survey-2026-03-autoroute-quality",
  "topic": "Routing Quality",
  "question": "How satisfied are you with the routing completion rate on your boards?",
  "options": [
    "Very satisfied",
    "Acceptable",
    "Needs improvement"
  ],
  "min_client_version": "2.5.0",
  "expires_at_utc": "2026-12-31T23:59:59Z"
}
```

### Retiring a Survey
To deactivate the current survey, clear `FREEROUTING__SURVEYS__ACTIVE_SURVEY` or set its `expires_at_utc` timestamp to the past. The server will immediately return HTTP 204 No Content.
