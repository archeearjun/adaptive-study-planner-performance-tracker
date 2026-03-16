# Adaptive Study Planner & Performance Tracker

Java desktop application for generating adaptive daily study plans, logging execution quality, and predicting topic recall likelihood with a lightweight logistic regression model.

## Motivation

Most study planners stop at static to-do lists. This project treats planning as a feedback loop:

- topics have deadlines, difficulty, confidence, and review state
- study sessions update future scheduling decisions
- retention risk influences revision timing
- analytics make consistency and neglect visible

The result is a resume-ready solo project that feels like a real 2024 productivity tool instead of a classroom CRUD demo.

## Stack

- Java 17
- Maven + Maven Wrapper
- JavaFX
- SQLite
- JUnit 5

## Features

- Subject and topic CRUD with metadata for priority, difficulty, exam date, confidence, estimated time, and review state
- Deterministic daily plan generation using priority, urgency, recall risk, backlog pressure, and available time
- Progress-aware replanning that shortens or skips topics already completed earlier the same day
- SM-2 spaced repetition updates for easiness factor, repetition count, interval, and next review date
- Pomodoro block generation with configurable focus and short-break lengths
- Session logging for completion status, duration, focus quality, confidence, quiz score, and review quality
- Logistic regression retention predictor trained from local study history
- Dashboard and analytics views for streaks, overdue reviews, completion rate, Pomodoros, and topic-level retention
- Seeded demo data with 3 subjects, 12 topics, historical sessions, review records, and model training rows
- Vercel-hostable static portfolio site at the repository root for public sharing

## Product Flow

1. Add subjects and topics with deadlines, difficulty, and confidence.
2. Generate a daily plan for the available minutes.
3. Work through study or review blocks in Pomodoro-sized chunks.
4. Log the session and optionally enter a review quality.
5. The app updates SM-2 review state, stores a new training example, retrains the retention model, and changes future planning output.
6. If the user regenerates the plan later the same day, previously completed work is taken into account instead of being blindly scheduled again.

## Architecture

Package layout:

```text
src/main/java/com/studyplanner/
  model/
  dto/
  persistence/
  service/
  service/scheduler/
  service/spacedrepetition/
  service/pomodoro/
  ml/
  ui/
  utils/
```

High-level responsibilities:

- `model`: domain entities like `Topic`, `StudySession`, `DailyPlan`, and `ReviewRecord`
- `persistence`: SQLite schema bootstrap, repositories, and demo seeding
- `service`: orchestration for CRUD, session logging, analytics, and retention prediction
- `service/scheduler`: weighted planning engine
- `service/spacedrepetition`: SM-2 implementation
- `service/pomodoro`: focus block generation
- `ml`: custom logistic regression implementation
- `ui`: JavaFX tabs for dashboard, management, planner, logger, analytics, and topic details

## Scheduling Logic

Each topic receives a deterministic weighted score:

```text
score =
  priorityWeight   * priority
  + urgencyWeight  * urgency
  + difficultyWeight * difficulty
  + recallRiskWeight * (1 - recallProbability)
  + backlogWeight * backlog
  + dueReviewBoost (when review is already due)
```

Default weights:

- priority: `0.27`
- urgency: `0.22`
- difficulty: `0.13`
- recall risk: `0.28`
- backlog: `0.10`
- due review boost: `0.08`

Supporting heuristics:

- `urgency` rises as the target exam date approaches
- `backlog` combines overdue review days, inactivity, and incomplete recent sessions
- items with overdue reviews or very low recall probability are converted into `REVIEW` blocks
- same-day logged progress reduces or removes duplicate scheduling during regeneration
- the planner writes recommendation reasons such as:
  `Priority 5/5 + low recall probability at 38% + exam in 5 days`

## SM-2 Spaced Repetition

Review quality is recorded on a `0-5` scale.

- quality `< 3`: repetitions reset, next interval becomes 1 day
- first successful review: 1 day
- second successful review: 6 days
- later successful reviews: `round(previousInterval * easinessFactor)`
- easiness factor is updated with the standard SM-2 formula and clamped to `>= 1.3`

Each review writes a `review_record` row and updates the topic’s:

- easiness factor
- repetition count
- interval days
- next review date

## Logistic Regression Retention Model

The app uses a custom logistic regression model implemented in Java.

Training features:

- days since last revision
- topic difficulty
- previous review quality
- confidence score
- completion consistency
- repetition count
- average session quality

How it is used:

- seed coefficients give believable behavior from first launch
- local training data refines weights as the user logs more sessions
- recall probability is displayed in the UI and fed directly into the scheduling score
- after each logged session, the app stores a fresh retention training example and retrains the model

## Database Schema

SQLite tables created automatically on first run:

- `subjects`
- `topics`
- `study_sessions`
- `review_records`
- `daily_plans`
- `plan_items`
- `pomodoro_blocks`
- `retention_training_data`

The database is created at:

```text
%USERPROFILE%/.adaptive-study-planner/studyplanner.db
```

You can override the location with:

- environment variable: `STUDYPLANNER_DB_PATH`
- JVM property: `-Dstudyplanner.db.path=...`

## UI Screens

- `Dashboard`: today’s planned hours, overdue reviews, streak, average recall, due tasks, risk table, trend charts
- `Subjects/Topics`: CRUD management for study structure and metadata
- `Daily Planner`: generate or regenerate the day’s ordered plan and inspect recommendation reasons
- `Session Logger`: log work done and update SM-2 review state
- `Analytics`: weekly consistency, completion rate, Pomodoros, subject breakdown, and topic metrics
- `Topic Details`: confidence trend, session history, review history, recall probability, and next review date

## Demo Data

The first launch seeds the app with:

- 3 subjects
- 12 topics
- varied deadlines, priorities, difficulties, and confidence levels
- historical study sessions
- review records
- retention model training data

That makes the planner, dashboard, and analytics immediately usable without manual setup.

## Running Locally

### Requirements

- JDK 17

### Run

Windows:

```bash
mvnw.cmd javafx:run
```

macOS / Linux:

```bash
./mvnw javafx:run
```

### Build

```bash
./mvnw -DskipTests package
```

### Test

```bash
./mvnw test
```

## Vercel Showcase

This repository now includes a static portfolio page at the repo root:

- `index.html`
- `site.css`
- `vercel.json`

Recommended public setup:

1. Deploy the static site on Vercel for LinkedIn, recruiter sharing, and portfolio traffic.
2. Keep the JavaFX app as the downloadable or locally runnable product.
3. Add real screenshots under `docs/screenshots/` before sharing publicly.

Important note:

- The JavaFX desktop application itself is not the part you deploy to Vercel.
- Vercel is used here as the public-facing showcase layer, not the Java runtime host.

## Screenshots

Add screenshots here after running the app locally:

- `docs/screenshots/dashboard.png`
- `docs/screenshots/planner.png`
- `docs/screenshots/analytics.png`

Placeholder markdown:

```md
![Dashboard](docs/screenshots/dashboard.png)
![Daily Planner](docs/screenshots/planner.png)
![Analytics](docs/screenshots/analytics.png)
```

## Resume-Ready Highlights

- Built a Java-based study planning engine combining SM-2 spaced repetition, priority-weighted scheduling, and Pomodoro-based focus sessions
- Integrated a logistic regression retention model to predict recall likelihood and dynamically adjust revision timing
- Added analytics for study consistency, overdue reviews, session quality, and topic-level retention trends using JavaFX + SQLite

## Tests Included

- SM-2 review update behavior
- logistic regression training behavior
- scheduler integration with seeded SQLite data
- adaptive replanning after work is already completed on the same day

## Future Improvements

- export analytics and plans to CSV or PDF
- package native desktop installers with `jpackage`
- add dark theme and richer chart interactivity
- support multiple user profiles
- tune scheduling weights from observed completion outcomes
- add calendar views and exam-week planning presets
