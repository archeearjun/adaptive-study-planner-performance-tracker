package com.studyplanner.service;

import com.studyplanner.dto.TimeSeriesPoint;
import com.studyplanner.dto.TopicDetailSnapshot;
import com.studyplanner.dto.TopicOverview;
import com.studyplanner.model.ReviewRecord;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

public class TopicService {
    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final RetentionPredictionService retentionPredictionService;

    public TopicService(TopicRepository topicRepository, SubjectRepository subjectRepository,
                        StudySessionRepository studySessionRepository, ReviewRecordRepository reviewRecordRepository,
                        RetentionPredictionService retentionPredictionService) {
        this.topicRepository = topicRepository;
        this.subjectRepository = subjectRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.retentionPredictionService = retentionPredictionService;
    }

    public List<TopicOverview> getTopicOverviews() {
        return topicRepository.findAllOverviews();
    }

    public List<Topic> getTopics() {
        return topicRepository.findAll();
    }

    public Topic getTopic(long topicId) {
        return topicRepository.findById(topicId)
            .orElseThrow(() -> new IllegalArgumentException("Topic not found: " + topicId));
    }

    public Topic saveTopic(Topic topic) {
        ValidationUtils.require(topic != null, "Topic is required");
        ValidationUtils.require(subjectRepository.findById(topic.getSubjectId()).isPresent(), "Subject must exist");
        ValidationUtils.require(topic.getName() != null && !topic.getName().isBlank(), "Topic name is required");
        ValidationUtils.require(topic.getPriority() >= 1 && topic.getPriority() <= 5, "Priority must be between 1 and 5");
        ValidationUtils.require(topic.getDifficulty() >= 1 && topic.getDifficulty() <= 5, "Difficulty must be between 1 and 5");
        ValidationUtils.require(topic.getEstimatedStudyMinutes() > 0, "Estimated study time must be greater than 0");

        topic.setConfidenceLevel(ValidationUtils.clamp(topic.getConfidenceLevel(), 0.0, 100.0));
        if (topic.getEasinessFactor() <= 0) {
            topic.setEasinessFactor(2.5);
        }
        if (topic.getNextReviewDate() == null) {
            LocalDate anchor = topic.getLastStudiedDate() != null ? topic.getLastStudiedDate() : LocalDate.now();
            topic.setNextReviewDate(anchor.plusDays(Math.max(1, topic.getIntervalDays())));
        }
        return topicRepository.save(topic);
    }

    public void deleteTopic(long topicId) {
        topicRepository.delete(topicId);
    }

    public TopicDetailSnapshot getTopicDetail(long topicId) {
        Topic topic = getTopic(topicId);
        Subject subject = subjectRepository.findById(topic.getSubjectId())
            .orElse(new Subject(0, "Unknown Subject", "", "#64748B", null));

        List<StudySession> sessions = studySessionRepository.findByTopicId(topicId).stream()
            .sorted(Comparator.comparing(StudySession::getSessionDate).reversed())
            .toList();
        List<ReviewRecord> reviews = reviewRecordRepository.findByTopicId(topicId).stream()
            .sorted(Comparator.comparing(ReviewRecord::getReviewDate).reversed())
            .toList();

        int totalSessions = sessions.size();
        int totalTimeSpent = sessions.stream().mapToInt(StudySession::getActualMinutes).sum();
        double averageConfidence = sessions.isEmpty()
            ? topic.getConfidenceLevel()
            : sessions.stream().mapToDouble(StudySession::getConfidenceAfter).average().orElse(topic.getConfidenceLevel());

        OptionalDouble revisionSuccess = reviews.stream()
            .mapToInt(review -> review.getQuality() >= 3 ? 1 : 0)
            .average();

        List<TimeSeriesPoint> confidenceTrend = new ArrayList<>();
        sessions.stream()
            .sorted(Comparator.comparing(StudySession::getSessionDate))
            .forEach(session -> confidenceTrend.add(new TimeSeriesPoint(session.getSessionDate(), session.getConfidenceAfter())));
        if (confidenceTrend.isEmpty()) {
            confidenceTrend.add(new TimeSeriesPoint(LocalDate.now(), topic.getConfidenceLevel()));
        }

        return new TopicDetailSnapshot(
            topic,
            subject.getName(),
            totalSessions,
            totalTimeSpent,
            retentionPredictionService.predictProbability(topic),
            revisionSuccess.orElse(0.0),
            averageConfidence,
            confidenceTrend,
            sessions.stream().limit(8).toList(),
            reviews.stream().limit(8).toList()
        );
    }
}
