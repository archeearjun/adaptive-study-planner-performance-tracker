package com.studyplanner.persistence;

import com.studyplanner.model.ReviewRecord;
import com.studyplanner.model.RetentionTrainingExample;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class DemoDataSeeder {
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final RetentionTrainingDataRepository trainingDataRepository;

    public DemoDataSeeder(SubjectRepository subjectRepository, TopicRepository topicRepository,
                          StudySessionRepository studySessionRepository, ReviewRecordRepository reviewRecordRepository,
                          RetentionTrainingDataRepository trainingDataRepository) {
        this.subjectRepository = subjectRepository;
        this.topicRepository = topicRepository;
        this.studySessionRepository = studySessionRepository;
        this.reviewRecordRepository = reviewRecordRepository;
        this.trainingDataRepository = trainingDataRepository;
    }

    public void seedIfEmpty() {
        if (subjectRepository.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        Map<String, Long> subjectIds = new HashMap<>();

        subjectIds.put("Algorithms & Data Structures", subjectRepository.save(
            new Subject(0, "Algorithms & Data Structures",
                "Exam-driven preparation covering problem solving and interview fundamentals.",
                "#2563EB", LocalDateTime.now().minusDays(30))
        ).getId());
        subjectIds.put("Operating Systems", subjectRepository.save(
            new Subject(0, "Operating Systems",
                "University course prep with a focus on review-heavy theory topics.",
                "#059669", LocalDateTime.now().minusDays(28))
        ).getId());
        subjectIds.put("Machine Learning", subjectRepository.save(
            new Subject(0, "Machine Learning",
                "Applied ML revision plan with concept reviews and quiz checkpoints.",
                "#D97706", LocalDateTime.now().minusDays(25))
        ).getId());

        Topic[] topics = new Topic[] {
            topic(subjectIds.get("Algorithms & Data Structures"), "Graph Traversal", 5, 4, 75, today.plusDays(6), 58, today.minusDays(4), 2.46, 3, 6, today.minusDays(1), "BFS, DFS, shortest path intuition."),
            topic(subjectIds.get("Algorithms & Data Structures"), "Dynamic Programming", 5, 5, 90, today.plusDays(9), 42, today.minusDays(7), 2.32, 2, 4, today.minusDays(2), "Pattern recognition for state transitions."),
            topic(subjectIds.get("Algorithms & Data Structures"), "Binary Search Trees", 3, 2, 40, today.plusDays(17), 76, today.minusDays(2), 2.68, 4, 12, today.plusDays(4), "Balancing operations and traversal cases."),
            topic(subjectIds.get("Algorithms & Data Structures"), "Hash Tables", 4, 2, 35, today.plusDays(14), 69, today.minusDays(5), 2.55, 3, 8, today.plusDays(1), "Collision resolution tradeoffs."),
            topic(subjectIds.get("Operating Systems"), "Process Scheduling", 5, 4, 70, today.plusDays(5), 51, today.minusDays(6), 2.41, 2, 5, today.minusDays(1), "FCFS, SJF, round robin, starvation."),
            topic(subjectIds.get("Operating Systems"), "Deadlock Avoidance", 4, 5, 60, today.plusDays(8), 38, today.minusDays(8), 2.22, 1, 1, today.minusDays(3), "Banker's algorithm and safe states."),
            topic(subjectIds.get("Operating Systems"), "Virtual Memory", 5, 4, 80, today.plusDays(4), 47, today.minusDays(5), 2.37, 2, 4, today.minusDays(1), "Paging, segmentation, and page replacement."),
            topic(subjectIds.get("Operating Systems"), "File Systems", 3, 3, 45, today.plusDays(18), 72, today.minusDays(3), 2.61, 3, 9, today.plusDays(3), "Directory structure and allocation strategies."),
            topic(subjectIds.get("Machine Learning"), "Linear Regression", 3, 2, 45, today.plusDays(15), 81, today.minusDays(1), 2.74, 4, 14, today.plusDays(6), "Bias-variance and loss function basics."),
            topic(subjectIds.get("Machine Learning"), "Logistic Regression", 4, 3, 60, today.plusDays(7), 63, today.minusDays(4), 2.53, 3, 7, today.minusDays(1), "Sigmoid, decision boundary, regularization."),
            topic(subjectIds.get("Machine Learning"), "Decision Trees", 4, 3, 55, today.plusDays(10), 66, today.minusDays(5), 2.49, 3, 6, today.plusDays(1), "Entropy, information gain, pruning."),
            topic(subjectIds.get("Machine Learning"), "Gradient Descent", 5, 4, 65, today.plusDays(3), 44, today.minusDays(7), 2.31, 2, 3, today.minusDays(2), "Learning rate behavior and convergence.")
        };

        for (Topic topic : topics) {
            topicRepository.save(topic);
            seedHistory(topic, today);
        }
    }

    private Topic topic(long subjectId, String name, int priority, int difficulty, int estimatedMinutes,
                        LocalDate examDate, double confidence, LocalDate lastStudiedDate, double ef,
                        int repetitions, int intervalDays, LocalDate nextReviewDate, String notes) {
        return new Topic(
            0,
            subjectId,
            name,
            notes,
            priority,
            difficulty,
            estimatedMinutes,
            examDate,
            confidence,
            lastStudiedDate,
            ef,
            repetitions,
            intervalDays,
            nextReviewDate,
            false
        );
    }

    private void seedHistory(Topic topic, LocalDate today) {
        int baseMinutes = topic.getEstimatedStudyMinutes();
        createSession(topic, today.minusDays(12), baseMinutes, Math.max(20, baseMinutes - 10),
            SessionStatus.COMPLETED, Math.max(2, topic.getDifficulty() - 1), topic.getConfidenceLevel() - 12, false, 72.0,
            "Initial deep-work session.");
        createSession(topic, today.minusDays(7), baseMinutes, Math.max(20, baseMinutes - 20),
            SessionStatus.PARTIALLY_COMPLETED, Math.max(2, topic.getDifficulty() - 1), topic.getConfidenceLevel() - 7, false, 64.0,
            "Follow-up review with some missed subtopics.");
        createSession(topic, today.minusDays(3), baseMinutes, Math.max(15, baseMinutes - 15),
            SessionStatus.COMPLETED, Math.min(5, topic.getDifficulty()), topic.getConfidenceLevel(), true, 78.0,
            "Revision pass tied to retrieval quiz.");

        int firstQuality = Math.max(2, 5 - topic.getDifficulty());
        int secondQuality = Math.min(5, firstQuality + 1);

        createReview(topic, today.minusDays(9), firstQuality, 2.50, 2.42, 1, 4, today.minusDays(5));
        createReview(topic, today.minusDays(4), secondQuality, 2.42, topic.getEasinessFactor(),
            4, topic.getIntervalDays(), topic.getNextReviewDate());

        createTrainingExample(topic, today.minusDays(9), 6, firstQuality, topic.getConfidenceLevel() / 100.0 - 0.18, 0.58, Math.max(1, topic.getRepetitionCount() - 1), 0.62, firstQuality >= 3 ? 1 : 0);
        createTrainingExample(topic, today.minusDays(4), 4, secondQuality, topic.getConfidenceLevel() / 100.0 - 0.08, 0.71, topic.getRepetitionCount(), 0.74, secondQuality >= 3 ? 1 : 0);
        createTrainingExample(topic, today.minusDays(1), Math.max(1, topic.getIntervalDays() - 2), Math.min(5, secondQuality + 1),
            topic.getConfidenceLevel() / 100.0, 0.84, topic.getRepetitionCount() + 1, 0.81,
            topic.getConfidenceLevel() >= 55 ? 1 : 0);
    }

    private void createSession(Topic topic, LocalDate sessionDate, int plannedMinutes, int actualMinutes,
                               SessionStatus status, int focusQuality, double confidenceAfter,
                               boolean reviewSession, Double quizScore, String notes) {
        StudySession session = new StudySession();
        session.setTopicId(topic.getId());
        session.setSubjectId(topic.getSubjectId());
        session.setSessionDate(sessionDate);
        session.setStartedAt(sessionDate.atTime(18, 0));
        session.setUpdatedAt(sessionDate.atTime(19, 0));
        session.setEndedAt(sessionDate.atTime(19, 0));
        session.setPlannedMinutes(plannedMinutes);
        session.setActualMinutes(actualMinutes);
        session.setStatus(status);
        session.setFocusQuality(focusQuality);
        session.setConfidenceAfter(Math.max(18, confidenceAfter));
        session.setQuizScore(quizScore);
        session.setReviewSession(reviewSession);
        session.setNotes(notes);
        studySessionRepository.save(session);
    }

    private void createReview(Topic topic, LocalDate reviewDate, int quality,
                              double beforeEf, double afterEf, int intervalBefore, int intervalAfter,
                              LocalDate nextReviewDate) {
        ReviewRecord record = new ReviewRecord();
        record.setTopicId(topic.getId());
        record.setReviewDate(reviewDate);
        record.setQuality(quality);
        record.setEasinessFactorBefore(beforeEf);
        record.setEasinessFactorAfter(afterEf);
        record.setIntervalBefore(intervalBefore);
        record.setIntervalAfter(intervalAfter);
        record.setNextReviewDate(nextReviewDate);
        reviewRecordRepository.save(record);
    }

    private void createTrainingExample(Topic topic, LocalDate capturedOn, double daysSinceLastRevision,
                                       double previousReviewQuality, double confidenceScore,
                                       double consistency, double repetitions, double averageSessionQuality,
                                       int label) {
        RetentionTrainingExample example = new RetentionTrainingExample();
        example.setTopicId(topic.getId());
        example.setCapturedOn(capturedOn);
        example.setDaysSinceLastRevision(daysSinceLastRevision);
        example.setDifficulty(topic.getDifficulty());
        example.setPreviousReviewQuality(previousReviewQuality);
        example.setConfidenceScore(Math.max(0.15, confidenceScore));
        example.setCompletionConsistency(consistency);
        example.setRepetitions(repetitions);
        example.setAverageSessionQuality(averageSessionQuality);
        example.setLabel(label);
        trainingDataRepository.save(example);
    }
}
