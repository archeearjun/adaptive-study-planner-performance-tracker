package com.studyplanner.service;

import com.studyplanner.dto.SessionLogRequest;
import com.studyplanner.dto.SessionProgressRequest;
import com.studyplanner.dto.SessionStartRequest;
import com.studyplanner.model.RetentionFeatureVector;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.DatabaseManager;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.service.spacedrepetition.ReviewUpdateResult;
import com.studyplanner.service.spacedrepetition.SpacedRepetitionService;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public class SessionLoggingService {
    private final DatabaseManager databaseManager;
    private final TopicRepository topicRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final SpacedRepetitionService spacedRepetitionService;
    private final RetentionPredictionService retentionPredictionService;

    public SessionLoggingService(DatabaseManager databaseManager, TopicRepository topicRepository,
                                 StudySessionRepository studySessionRepository,
                                 ReviewRecordRepository reviewRecordRepository, DailyPlanRepository dailyPlanRepository,
                                 SpacedRepetitionService spacedRepetitionService,
                                 RetentionPredictionService retentionPredictionService) {
        this.databaseManager = databaseManager;
        this.topicRepository = topicRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.spacedRepetitionService = spacedRepetitionService;
        this.retentionPredictionService = retentionPredictionService;
    }

    public StudySession startSession(SessionStartRequest request) {
        ValidationUtils.require(request != null, "Session start request is required");
        ValidationUtils.require(request.plannedMinutes() > 0, "Planned minutes must be greater than zero");

        Topic topic = topicRepository.findById(request.topicId())
            .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + request.topicId()));
        LocalDate sessionDate = request.sessionDate() != null
            ? request.sessionDate()
            : request.startedAt() != null ? request.startedAt().toLocalDate() : LocalDate.now();
        LocalDateTime startedAt = resolveStartTime(request.startedAt(), sessionDate);

        if (request.planItemId() != null) {
            ValidationUtils.require(
                studySessionRepository.findLatestByPlanItemId(request.planItemId()).isEmpty(),
                "A session already exists for this planned item"
            );
            ValidationUtils.require(
                studySessionRepository.findActiveByPlanItemId(request.planItemId()).isEmpty(),
                "An active session already exists for this planned item"
            );
        } else {
            ValidationUtils.require(
                studySessionRepository.findActiveByTopicAndDate(topic.getId(), sessionDate).isEmpty(),
                "An active session already exists for this topic on " + sessionDate
            );
        }

        StudySession session = new StudySession();
        session.setPlanItemId(request.planItemId());
        session.setTopicId(topic.getId());
        session.setSubjectId(topic.getSubjectId());
        session.setSessionDate(sessionDate);
        session.setStartedAt(startedAt);
        session.setUpdatedAt(startedAt);
        session.setEndedAt(null);
        session.setPlannedMinutes(request.plannedMinutes());
        session.setActualMinutes(0);
        session.setStatus(SessionStatus.STARTED);
        session.setFocusQuality(null);
        session.setConfidenceAfter(null);
        session.setQuizScore(null);
        session.setReviewSession(request.reviewSession());
        session.setNotes(cleanText(request.notes()));
        return databaseManager.executeInTransaction(connection -> {
            studySessionRepository.save(connection, session);
            if (request.planItemId() != null) {
                dailyPlanRepository.updatePlanItemProgress(connection, request.planItemId(), SessionStatus.STARTED, 0, null);
            }
            return session;
        });
    }

    public StudySession pauseSession(SessionProgressRequest request) {
        ValidationUtils.require(request != null, "Session progress request is required");
        ValidationUtils.require(request.actualMinutes() >= 0, "Actual minutes must be zero or greater");

        StudySession session = loadExistingSession(request.sessionId());
        ValidationUtils.require(session.getStatus() == SessionStatus.STARTED, "Only started sessions can be paused");
        ValidationUtils.require(request.actualMinutes() >= session.getActualMinutes(), "Actual minutes cannot move backwards");

        LocalDateTime occurredAt = resolveLifecycleTime(request.occurredAt(), session.getSessionDate(), session.getUpdatedAt());
        session.setActualMinutes(request.actualMinutes());
        session.setStatus(SessionStatus.PAUSED);
        session.setUpdatedAt(occurredAt);
        session.setNotes(mergeNotes(session.getNotes(), request.notes()));
        return databaseManager.executeInTransaction(connection -> {
            studySessionRepository.save(connection, session);
            if (session.getPlanItemId() != null) {
                dailyPlanRepository.updatePlanItemProgress(
                    connection,
                    session.getPlanItemId(),
                    SessionStatus.PAUSED,
                    session.getActualMinutes(),
                    null
                );
            }
            return session;
        });
    }

    public StudySession resumeSession(long sessionId, LocalDateTime resumedAt) {
        StudySession session = loadExistingSession(sessionId);
        ValidationUtils.require(session.getStatus() == SessionStatus.PAUSED, "Only paused sessions can be resumed");

        session.setStatus(SessionStatus.STARTED);
        session.setUpdatedAt(resolveLifecycleTime(resumedAt, session.getSessionDate(), session.getUpdatedAt()));
        return databaseManager.executeInTransaction(connection -> {
            studySessionRepository.save(connection, session);
            if (session.getPlanItemId() != null) {
                dailyPlanRepository.updatePlanItemProgress(
                    connection,
                    session.getPlanItemId(),
                    SessionStatus.STARTED,
                    session.getActualMinutes(),
                    null
                );
            }
            return session;
        });
    }

    public StudySession abandonSession(SessionProgressRequest request) {
        ValidationUtils.require(request != null, "Session progress request is required");
        ValidationUtils.require(request.actualMinutes() >= 0, "Actual minutes must be zero or greater");

        StudySession session = loadExistingSession(request.sessionId());
        ValidationUtils.require(
            session.getStatus() == SessionStatus.STARTED || session.getStatus() == SessionStatus.PAUSED,
            "Only started or paused sessions can be abandoned"
        );
        ValidationUtils.require(request.actualMinutes() >= session.getActualMinutes(), "Actual minutes cannot move backwards");

        LocalDateTime occurredAt = resolveLifecycleTime(request.occurredAt(), session.getSessionDate(), session.getUpdatedAt());
        session.setActualMinutes(request.actualMinutes());
        session.setStatus(SessionStatus.ABANDONED);
        session.setUpdatedAt(occurredAt);
        session.setEndedAt(occurredAt);
        session.setNotes(mergeNotes(session.getNotes(), request.notes()));
        return databaseManager.executeInTransaction(connection -> {
            studySessionRepository.save(connection, session);
            if (session.getPlanItemId() != null) {
                dailyPlanRepository.updatePlanItemProgress(
                    connection,
                    session.getPlanItemId(),
                    SessionStatus.ABANDONED,
                    session.getActualMinutes(),
                    null
                );
            }
            dailyPlanRepository.markPlansFromDateStale(connection, session.getSessionDate());
            return session;
        });
    }

    public int abandonStaleSessions(LocalDate referenceDate) {
        ValidationUtils.require(referenceDate != null, "Reference date is required");

        return databaseManager.executeInTransaction(connection -> {
            int abandonedCount = 0;
            LocalDate earliestAffectedDate = null;
            for (StudySession session : studySessionRepository.findOpenSessionsBefore(referenceDate)) {
                session.setStatus(SessionStatus.ABANDONED);
                session.setUpdatedAt(session.getSessionDate().atTime(23, 59));
                session.setEndedAt(session.getUpdatedAt());
                session.setNotes(mergeNotes(session.getNotes(), "Automatically abandoned after app restart or date rollover."));
                studySessionRepository.save(connection, session);
                if (session.getPlanItemId() != null) {
                    dailyPlanRepository.updatePlanItemProgress(
                        connection,
                        session.getPlanItemId(),
                        SessionStatus.ABANDONED,
                        session.getActualMinutes(),
                        null
                    );
                }
                if (earliestAffectedDate == null || session.getSessionDate().isBefore(earliestAffectedDate)) {
                    earliestAffectedDate = session.getSessionDate();
                }
                abandonedCount++;
            }
            if (earliestAffectedDate != null) {
                dailyPlanRepository.markPlansFromDateStale(connection, earliestAffectedDate);
            }
            return abandonedCount;
        });
    }

    public StudySession logSession(SessionLogRequest request) {
        ValidationUtils.require(request != null, "Session request is required");
        ValidationUtils.require(request.status() != null, "Session status is required");
        ValidationUtils.require(request.status().isTerminal(), "Only terminal session outcomes can be logged");
        ValidationUtils.require(request.actualMinutes() >= 0, "Actual minutes must be zero or greater");
        ValidationUtils.require(request.plannedMinutes() > 0, "Planned minutes must be greater than zero");
        if (request.quizScore() != null) {
            ValidationUtils.require(request.quizScore() >= 0 && request.quizScore() <= 100, "Quiz score must be between 0 and 100");
        }
        if (request.reviewQuality() != null) {
            ValidationUtils.require(request.reviewQuality() >= 0 && request.reviewQuality() <= 5, "Review quality must be between 0 and 5");
            ValidationUtils.require(request.status().countsAsStudiedWork(), "Only studied sessions can submit a review quality");
        }
        if (request.status() == SessionStatus.SKIPPED) {
            ValidationUtils.require(request.actualMinutes() == 0, "Skipped sessions must log 0 actual minutes");
        }
        if (request.status() == SessionStatus.COMPLETED) {
            ValidationUtils.require(request.actualMinutes() > 0, "Completed sessions must log positive actual minutes");
        }
        if (request.status() == SessionStatus.PARTIALLY_COMPLETED) {
            ValidationUtils.require(request.actualMinutes() > 0, "Partially completed sessions must log positive actual minutes");
        }
        if (request.status().countsAsStudiedWork()) {
            ValidationUtils.require(request.focusQuality() != null, "Focus quality is required for studied sessions");
            ValidationUtils.require(request.focusQuality() >= 1 && request.focusQuality() <= 5, "Focus quality must be between 1 and 5");
            ValidationUtils.require(request.confidenceAfter() != null, "Confidence is required for studied sessions");
            ValidationUtils.require(request.confidenceAfter() >= 0 && request.confidenceAfter() <= 100, "Confidence must be between 0 and 100");
        }

        Topic topic = topicRepository.findById(request.topicId())
            .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + request.topicId()));
        LocalDate sessionDate = request.sessionDate() != null ? request.sessionDate() : LocalDate.now();
        RetentionFeatureVector preSessionObservation = retentionPredictionService.buildFeatureVector(topic, sessionDate);
        StudySession session = resolveSessionForFinalization(request, sessionDate, topic);
        LocalDateTime finalizedAt = resolveLifecycleTime(null, sessionDate, session.getUpdatedAt() != null ? session.getUpdatedAt() : session.getStartedAt());

        session.setPlannedMinutes(request.plannedMinutes());
        session.setActualMinutes(request.actualMinutes());
        session.setStatus(request.status());
        session.setFocusQuality(request.status().countsAsStudiedWork() ? request.focusQuality() : null);
        session.setConfidenceAfter(request.status().countsAsStudiedWork() ? request.confidenceAfter() : null);
        session.setQuizScore(request.status().countsAsStudiedWork() ? request.quizScore() : null);
        session.setReviewSession(session.isReviewSession() || request.reviewSession() || request.reviewQuality() != null);
        session.setUpdatedAt(finalizedAt);
        session.setEndedAt(finalizedAt);
        session.setNotes(mergeNotes(session.getNotes(), request.notes()));
        FinalizedSessionResult result = databaseManager.executeInTransaction(connection -> {
            studySessionRepository.save(connection, session);

            Topic topicToPersist = topic;
            boolean capturedTraining = false;
            if (request.status().countsAsStudiedWork() && request.reviewQuality() != null) {
                ReviewUpdateResult reviewUpdate = spacedRepetitionService.applyReview(topic, request.reviewQuality(), session.getSessionDate());
                topicToPersist = reviewUpdate.topic();
                topicToPersist.setConfidenceLevel(request.confidenceAfter());
                reviewRecordRepository.save(connection, reviewUpdate.reviewRecord());
            } else if (request.status().countsAsStudiedWork()) {
                topicToPersist.setLastStudiedDate(session.getSessionDate());
                topicToPersist.setConfidenceLevel(request.confidenceAfter());
            }

            if (request.status().countsAsStudiedWork()) {
                topicRepository.save(connection, topicToPersist);
            }

            if (session.getPlanItemId() != null) {
                dailyPlanRepository.updatePlanItemProgress(
                    connection,
                    session.getPlanItemId(),
                    request.status(),
                    request.actualMinutes(),
                    request.focusQuality()
                );
            }

            if (request.status().countsAsStudiedWork()) {
                capturedTraining = retentionPredictionService.captureTrainingExample(
                    connection,
                    topicToPersist,
                    preSessionObservation,
                    request.reviewQuality(),
                    request.quizScore(),
                    sessionDate,
                    session.getId()
                );
            }
            dailyPlanRepository.markPlansFromDateStale(connection, sessionDate);
            return new FinalizedSessionResult(session, capturedTraining);
        });

        if (result.capturedTraining()) {
            retentionPredictionService.retrainModel();
        }
        return result.session();
    }

    private StudySession resolveSessionForFinalization(SessionLogRequest request, LocalDate sessionDate, Topic topic) {
        Optional<StudySession> existingSession = Optional.empty();
        if (request.sessionId() != null) {
            existingSession = Optional.of(loadExistingSession(request.sessionId()));
        } else if (request.planItemId() != null) {
            existingSession = studySessionRepository.findLatestByPlanItemId(request.planItemId());
        }

        if (existingSession.isPresent()) {
            StudySession session = existingSession.get();
            ValidationUtils.require(session.getTopicId() == topic.getId(), "Session topic does not match the requested topic");
            session.setPlanItemId(request.planItemId() != null ? request.planItemId() : session.getPlanItemId());
            session.setSessionDate(sessionDate);
            session.setSubjectId(topic.getSubjectId());
            if (session.getStartedAt() == null) {
                session.setStartedAt(resolveStartTime(null, sessionDate));
            }
            return session;
        }

        StudySession session = new StudySession();
        session.setPlanItemId(request.planItemId());
        session.setTopicId(topic.getId());
        session.setSubjectId(topic.getSubjectId());
        session.setSessionDate(sessionDate);
        session.setStartedAt(resolveStartTime(null, sessionDate));
        session.setUpdatedAt(session.getStartedAt());
        session.setEndedAt(null);
        return session;
    }

    private StudySession loadExistingSession(long sessionId) {
        return studySessionRepository.findById(sessionId)
            .orElseThrow(() -> new IllegalArgumentException("Study session not found: " + sessionId));
    }

    private LocalDateTime resolveStartTime(LocalDateTime requestedStart, LocalDate sessionDate) {
        if (requestedStart != null) {
            ValidationUtils.require(!requestedStart.toLocalDate().isBefore(sessionDate),
                "Start time cannot be earlier than the session date");
            return requestedStart;
        }
        return sessionDate.equals(LocalDate.now()) ? LocalDateTime.now() : sessionDate.atTime(18, 30);
    }

    private LocalDateTime resolveLifecycleTime(LocalDateTime requestedTime, LocalDate sessionDate, LocalDateTime baseline) {
        LocalDateTime resolved = requestedTime != null
            ? requestedTime
            : sessionDate.equals(LocalDate.now()) ? LocalDateTime.now() : sessionDate.atTime(20, 0);
        if (baseline != null && resolved.isBefore(baseline)) {
            return baseline;
        }
        return resolved;
    }

    private String cleanText(String notes) {
        return notes == null ? null : notes.trim().isBlank() ? null : notes.trim();
    }

    private String mergeNotes(String existingNotes, String newNotes) {
        String cleanedNewNotes = cleanText(newNotes);
        if (cleanedNewNotes == null) {
            return existingNotes;
        }
        String cleanedExistingNotes = cleanText(existingNotes);
        if (cleanedExistingNotes == null) {
            return cleanedNewNotes;
        }
        if (cleanedExistingNotes.contains(cleanedNewNotes)) {
            return cleanedExistingNotes;
        }
        return cleanedExistingNotes + System.lineSeparator() + cleanedNewNotes;
    }

    private record FinalizedSessionResult(StudySession session, boolean capturedTraining) {
    }
}
