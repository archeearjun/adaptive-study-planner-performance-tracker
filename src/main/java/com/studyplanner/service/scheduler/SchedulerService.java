package com.studyplanner.service.scheduler;

import com.studyplanner.dto.PlanGenerationRequest;
import com.studyplanner.dto.RetentionPrediction;
import com.studyplanner.model.DailyPlan;
import com.studyplanner.model.PlanItem;
import com.studyplanner.model.PlanItemType;
import com.studyplanner.model.SessionStatus;
import com.studyplanner.model.StudySession;
import com.studyplanner.model.Subject;
import com.studyplanner.model.Topic;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.service.RetentionPredictionService;
import com.studyplanner.service.pomodoro.PomodoroPlannerService;
import com.studyplanner.utils.DateUtils;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SchedulerService {
    private final TopicRepository topicRepository;
    private final SubjectRepository subjectRepository;
    private final StudySessionRepository studySessionRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final RetentionPredictionService retentionPredictionService;
    private final PomodoroPlannerService pomodoroPlannerService;
    private final ScheduleWeights scheduleWeights = new ScheduleWeights();

    public SchedulerService(TopicRepository topicRepository, SubjectRepository subjectRepository,
                            StudySessionRepository studySessionRepository, DailyPlanRepository dailyPlanRepository,
                            RetentionPredictionService retentionPredictionService,
                            PomodoroPlannerService pomodoroPlannerService) {
        this.topicRepository = topicRepository;
        this.subjectRepository = subjectRepository;
        this.studySessionRepository = studySessionRepository;
        this.dailyPlanRepository = dailyPlanRepository;
        this.retentionPredictionService = retentionPredictionService;
        this.pomodoroPlannerService = pomodoroPlannerService;
    }

    public Optional<DailyPlan> getPlan(LocalDate planDate) {
        return dailyPlanRepository.findByDate(planDate);
    }

    public DailyPlan generateDailyPlan(PlanGenerationRequest request) {
        ValidationUtils.require(request != null, "Plan request is required");
        ValidationUtils.require(request.availableMinutes() > 0, "Available time must be greater than zero");

        LocalDate planDate = request.planDate() != null ? request.planDate() : LocalDate.now();
        Map<Long, String> subjectNames = subjectRepository.findAll().stream()
            .collect(Collectors.toMap(Subject::getId, Subject::getName));
        List<StudySession> allSessions = studySessionRepository.findAll();
        Map<Long, List<StudySession>> sessionsByTopic = allSessions.stream()
            .collect(Collectors.groupingBy(StudySession::getTopicId, HashMap::new, Collectors.toList()));
        Map<Long, List<StudySession>> sessionsTodayByTopic = allSessions.stream()
            .filter(session -> planDate.equals(session.getSessionDate()))
            .collect(Collectors.groupingBy(StudySession::getTopicId, HashMap::new, Collectors.toList()));

        List<Candidate> rankedCandidates = topicRepository.findAll().stream()
            .filter(topic -> !topic.isArchived())
            .map(topic -> scoreTopic(topic, subjectNames.getOrDefault(topic.getSubjectId(), "Unknown Subject"),
                sessionsByTopic.getOrDefault(topic.getId(), List.of()),
                sessionsTodayByTopic.getOrDefault(topic.getId(), List.of()),
                planDate))
            .filter(candidate -> candidate != null)
            .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparing(Candidate::daysToExam)
                .thenComparing(candidate -> candidate.topic().getName()))
            .toList();

        DailyPlan dailyPlan = new DailyPlan();
        dailyPlan.setPlanDate(planDate);
        dailyPlan.setAvailableMinutes(request.availableMinutes());
        dailyPlan.setGeneratedAt(LocalDateTime.now());

        List<PlanItem> items = new ArrayList<>();
        int remainingMinutes = request.availableMinutes();
        int order = 1;
        int adjustedForToday = 0;
        for (Candidate candidate : rankedCandidates) {
            if (remainingMinutes < 20) {
                break;
            }

            int minutesForItem = Math.min(candidate.plannedMinutes(), remainingMinutes);
            if (minutesForItem < 20) {
                continue;
            }

            PlanItem item = new PlanItem();
            item.setTopicId(candidate.topic().getId());
            item.setSubjectId(candidate.topic().getSubjectId());
            item.setTopicName(candidate.topic().getName());
            item.setSubjectName(candidate.subjectName());
            item.setItemType(candidate.itemType());
            item.setRecommendedOrder(order++);
            item.setPlannedMinutes(minutesForItem);
            item.setScore(candidate.score());
            item.setReason(candidate.reason());
            item.setRecallProbability(candidate.prediction().probability());
            item.setStatus(SessionStatus.PLANNED);
            item.setCompletedMinutes(0);
            item.setPomodoroBlocks(pomodoroPlannerService.buildBlocks(
                minutesForItem,
                request.focusMinutes(),
                request.shortBreakMinutes()
            ));
            item.setPomodoroCount(item.getPomodoroBlocks().size());
            items.add(item);
            remainingMinutes -= minutesForItem;
            if (candidate.minutesAlreadyLoggedToday() > 0) {
                adjustedForToday++;
            }
        }

        dailyPlan.setItems(items);
        dailyPlan.setTotalPlannedMinutes(items.stream().mapToInt(PlanItem::getPlannedMinutes).sum());
        long skippedForToday = rankedCandidates.stream().filter(Candidate::skipBecauseAlreadyCoveredToday).count();
        dailyPlan.setSummary(buildSummary(items, remainingMinutes, adjustedForToday, (int) skippedForToday));
        return dailyPlanRepository.saveOrReplace(dailyPlan);
    }

    public ScheduleWeights getScheduleWeights() {
        return scheduleWeights;
    }

    private Candidate scoreTopic(Topic topic, String subjectName, List<StudySession> sessions,
                                 List<StudySession> sessionsToday, LocalDate planDate) {
        RetentionPrediction prediction = retentionPredictionService.predict(topic);
        double priority = topic.getPriority() / 5.0;
        long daysToExam = topic.getTargetExamDate() == null
            ? 999
            : ChronoUnit.DAYS.between(planDate, topic.getTargetExamDate());
        double urgency = topic.getTargetExamDate() == null
            ? 0.15
            : ValidationUtils.clamp(1.0 - (daysToExam / 21.0), 0.05, 1.0);
        double difficulty = topic.getDifficulty() / 5.0;
        double recallRisk = 1.0 - prediction.probability();
        double backlog = backlogScore(topic, sessions, planDate);
        boolean dueReview = topic.getNextReviewDate() != null && !topic.getNextReviewDate().isAfter(planDate);
        boolean lowRecall = prediction.probability() < 0.46;
        PlanItemType itemType = (dueReview || lowRecall) ? PlanItemType.REVIEW : PlanItemType.STUDY;
        int basePlannedMinutes = estimateMinutes(topic, itemType, backlog, recallRisk);
        TodayProgress todayProgress = summarizeTodayProgress(sessionsToday, basePlannedMinutes);
        boolean urgentTopic = daysToExam <= 2;
        boolean criticalReview = dueReview && prediction.probability() < 0.35;

        if (todayProgress.isCoveredForToday() && !urgentTopic && !criticalReview) {
            return new Candidate(
                topic,
                subjectName,
                prediction,
                itemType,
                0.0,
                0,
                daysToExam,
                "Already covered by today's logged work.",
                todayProgress.loggedMinutes(),
                true
            );
        }

        double score = scheduleWeights.getPriorityWeight() * priority
            + scheduleWeights.getUrgencyWeight() * urgency
            + scheduleWeights.getDifficultyWeight() * difficulty
            + scheduleWeights.getRecallRiskWeight() * recallRisk
            + scheduleWeights.getBacklogWeight() * backlog
            + (dueReview ? scheduleWeights.getDueReviewBoost() : 0.0);
        if (todayProgress.loggedMinutes() > 0 && !urgentTopic && !criticalReview) {
            score *= 0.92;
        }

        int plannedMinutes = Math.max(20, basePlannedMinutes - todayProgress.loggedMinutes());
        String reason = buildReason(
            topic,
            prediction,
            dueReview,
            daysToExam,
            priority,
            difficulty,
            backlog,
            sessions,
            todayProgress,
            planDate
        );

        return new Candidate(topic, subjectName, prediction, itemType, score, plannedMinutes, daysToExam, reason,
            todayProgress.loggedMinutes(), false);
    }

    private double backlogScore(Topic topic, List<StudySession> sessions, LocalDate planDate) {
        long overdueDays = topic.getNextReviewDate() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(topic.getNextReviewDate(), planDate));
        double overdueComponent = ValidationUtils.clamp(overdueDays / 7.0, 0.0, 1.0);

        long daysSinceStudy = topic.getLastStudiedDate() == null
            ? 14
            : Math.max(0, ChronoUnit.DAYS.between(topic.getLastStudiedDate(), planDate));
        double inactivityComponent = ValidationUtils.clamp(daysSinceStudy / 14.0, 0.0, 1.0);

        double incompleteRatio = 0.0;
        if (!sessions.isEmpty()) {
            List<StudySession> recent = sessions.stream()
                .sorted(Comparator.comparing(StudySession::getSessionDate).reversed())
                .limit(5)
                .toList();
            for (StudySession session : recent) {
                incompleteRatio += switch (session.getStatus()) {
                    case SKIPPED -> 1.0;
                    case PARTIALLY_COMPLETED -> 0.5;
                    case PLANNED, COMPLETED -> 0.0;
                };
            }
            incompleteRatio = incompleteRatio / recent.size();
        }

        return ValidationUtils.clamp(overdueComponent * 0.5 + inactivityComponent * 0.3 + incompleteRatio * 0.2, 0.0, 1.0);
    }

    private int estimateMinutes(Topic topic, PlanItemType itemType, double backlog, double recallRisk) {
        if (itemType == PlanItemType.REVIEW) {
            int reviewMinutes = Math.max(20, topic.getEstimatedStudyMinutes() / 2);
            if (backlog > 0.7 || recallRisk > 0.65) {
                reviewMinutes += 10;
            }
            return Math.min(reviewMinutes, 55);
        }
        return Math.min(Math.max(25, topic.getEstimatedStudyMinutes()), 95);
    }

    private String buildReason(Topic topic, RetentionPrediction prediction, boolean dueReview, long daysToExam,
                               double priority, double difficulty, double backlog, List<StudySession> sessions,
                               TodayProgress todayProgress, LocalDate planDate) {
        List<ReasonComponent> reasons = new ArrayList<>();
        reasons.add(new ReasonComponent("Priority " + topic.getPriority() + "/5", scheduleWeights.getPriorityWeight() * priority));

        if (topic.getTargetExamDate() != null) {
            if (daysToExam <= 0) {
                reasons.add(new ReasonComponent("exam is due now", scheduleWeights.getUrgencyWeight()));
            } else {
                reasons.add(new ReasonComponent("exam in " + daysToExam + " days", scheduleWeights.getUrgencyWeight() * ValidationUtils.clamp(1.0 - (daysToExam / 21.0), 0.05, 1.0)));
            }
        }

        if (prediction.probability() < 0.55) {
            reasons.add(new ReasonComponent("low recall probability at " + Math.round(prediction.probability() * 100) + "%", scheduleWeights.getRecallRiskWeight() * (1.0 - prediction.probability())));
        }

        if (dueReview) {
            long overdueDays = Math.max(0, DateUtils.daysUntil(topic.getNextReviewDate(), planDate));
            reasons.add(new ReasonComponent("review overdue by " + overdueDays + " days", scheduleWeights.getDueReviewBoost() + 0.05));
        }

        if (difficulty >= 0.75) {
            reasons.add(new ReasonComponent("high difficulty", scheduleWeights.getDifficultyWeight() * difficulty));
        }

        if (backlog > 0.55) {
            reasons.add(new ReasonComponent("backlog is building", scheduleWeights.getBacklogWeight() * backlog));
        }

        if (todayProgress.loggedMinutes() > 0) {
            reasons.add(new ReasonComponent(todayProgress.loggedMinutes() + " minutes already logged today", 0.07));
        }

        long skippedOrPartial = sessions.stream()
            .filter(session -> session.getStatus() == SessionStatus.SKIPPED || session.getStatus() == SessionStatus.PARTIALLY_COMPLETED)
            .count();
        if (skippedOrPartial >= 2) {
            reasons.add(new ReasonComponent("recent sessions were only partially completed", 0.06));
        }

        return reasons.stream()
            .sorted(Comparator.comparingDouble(ReasonComponent::impact).reversed())
            .limit(3)
            .map(ReasonComponent::label)
            .collect(Collectors.joining(" + "));
    }

    private String buildSummary(List<PlanItem> items, int remainingMinutes, int adjustedForToday, int skippedForToday) {
        long reviewCount = items.stream().filter(item -> item.getItemType() == PlanItemType.REVIEW).count();
        int pomodoros = items.stream().mapToInt(PlanItem::getPomodoroCount).sum();
        StringBuilder summary = new StringBuilder();
        summary.append(items.size()).append(" items scheduled, ")
            .append(reviewCount).append(" review blocks, ")
            .append(pomodoros).append(" pomodoros, ")
            .append(remainingMinutes).append(" minutes left unallocated.");
        if (adjustedForToday > 0) {
            summary.append(" ").append(adjustedForToday).append(" items were shortened because progress was already logged today.");
        }
        if (skippedForToday > 0) {
            summary.append(" ").append(skippedForToday).append(" topics were skipped because today's work already covered them.");
        }
        return summary.toString();
    }

    private TodayProgress summarizeTodayProgress(List<StudySession> sessionsToday, int baselineMinutes) {
        int loggedMinutes = sessionsToday.stream()
            .filter(session -> session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.PARTIALLY_COMPLETED)
            .mapToInt(StudySession::getActualMinutes)
            .sum();
        double coverage = baselineMinutes <= 0 ? 0.0 : loggedMinutes / (double) baselineMinutes;
        boolean coveredForToday = !sessionsToday.isEmpty() && coverage >= 1.0;
        return new TodayProgress(loggedMinutes, coveredForToday);
    }

    private record Candidate(
        Topic topic,
        String subjectName,
        RetentionPrediction prediction,
        PlanItemType itemType,
        double score,
        int plannedMinutes,
        long daysToExam,
        String reason,
        int minutesAlreadyLoggedToday,
        boolean skipBecauseAlreadyCoveredToday
    ) {
    }

    private record TodayProgress(
        int loggedMinutes,
        boolean isCoveredForToday
    ) {
    }

    private record ReasonComponent(String label, double impact) {
    }
}
