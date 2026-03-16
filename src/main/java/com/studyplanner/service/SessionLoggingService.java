package com.studyplanner.service;

import com.studyplanner.dto.SessionLogRequest;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.service.spacedrepetition.ReviewUpdateResult;
import com.studyplanner.service.spacedrepetition.SpacedRepetitionService;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;

public class SessionLoggingService {
    private final TopicRepository topicRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final SpacedRepetitionService spacedRepetitionService;
    private final RetentionPredictionService retentionPredictionService;

    public SessionLoggingService(TopicRepository topicRepository, StudySessionRepository studySessionRepository,
                                 ReviewRecordRepository reviewRecordRepository, DailyPlanRepository dailyPlanRepository,
                                 SpacedRepetitionService spacedRepetitionService,
                                 RetentionPredictionService retentionPredictionService) {
        this.topicRepository = topicRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.spacedRepetitionService = spacedRepetitionService;
        this.retentionPredictionService = retentionPredictionService;
    }

    public void logSession(SessionLogRequest request) {
        ValidationUtils.require(request != null, "Session request is required");
        ValidationUtils.require(request.status() != null, "Session status is required");
        ValidationUtils.require(request.actualMinutes() >= 0, "Actual minutes must be zero or greater");
        ValidationUtils.require(request.plannedMinutes() > 0, "Planned minutes must be greater than zero");
        ValidationUtils.require(request.focusQuality() >= 1 && request.focusQuality() <= 5, "Focus quality must be between 1 and 5");
        ValidationUtils.require(request.confidenceAfter() >= 0 && request.confidenceAfter() <= 100, "Confidence must be between 0 and 100");
        if (request.quizScore() != null) {
            ValidationUtils.require(request.quizScore() >= 0 && request.quizScore() <= 100, "Quiz score must be between 0 and 100");
        }
        if (request.reviewQuality() != null) {
            ValidationUtils.require(request.reviewQuality() >= 0 && request.reviewQuality() <= 5, "Review quality must be between 0 and 5");
            ValidationUtils.require(request.status() != SessionStatus.SKIPPED, "A skipped session cannot submit a review quality");
        }
        if (request.status() == SessionStatus.SKIPPED) {
            ValidationUtils.require(request.actualMinutes() == 0, "Skipped sessions must log 0 actual minutes");
        }
        if (request.status() == SessionStatus.COMPLETED) {
            ValidationUtils.require(request.actualMinutes() > 0, "Completed sessions must log positive actual minutes");
        }

        Topic topic = topicRepository.findById(request.topicId())
            .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + request.topicId()));

        StudySession session = new StudySession();
        session.setPlanItemId(request.planItemId());
        session.setTopicId(request.topicId());
        session.setSessionDate(request.sessionDate() != null ? request.sessionDate() : LocalDate.now());
        session.setStartedAt(session.getSessionDate().atTime(18, 30));
        session.setPlannedMinutes(request.plannedMinutes());
        session.setActualMinutes(request.actualMinutes());
        session.setStatus(request.status());
        session.setFocusQuality(request.focusQuality());
        session.setConfidenceAfter(request.confidenceAfter());
        session.setQuizScore(request.quizScore());
        session.setReviewSession(request.reviewSession() || request.reviewQuality() != null);
        session.setNotes(request.notes());
        studySessionRepository.save(session);

        topic.setLastStudiedDate(session.getSessionDate());
        topic.setConfidenceLevel(request.confidenceAfter());

        if (request.reviewQuality() != null) {
            ReviewUpdateResult reviewUpdate = spacedRepetitionService.applyReview(topic, request.reviewQuality(), session.getSessionDate());
            topic = reviewUpdate.topic();
            reviewRecordRepository.save(reviewUpdate.reviewRecord());
        } else if (topic.getNextReviewDate() == null) {
            topic.setIntervalDays(Math.max(1, topic.getIntervalDays()));
            topic.setNextReviewDate(session.getSessionDate().plusDays(Math.max(1, topic.getIntervalDays())));
        }

        topicRepository.save(topic);

        if (request.planItemId() != null) {
            dailyPlanRepository.updatePlanItemProgress(request.planItemId(), request.status(), request.actualMinutes(), request.focusQuality());
        }

        retentionPredictionService.captureTrainingExample(topic, request.reviewQuality(), request.status(), request.quizScore());
    }
}
