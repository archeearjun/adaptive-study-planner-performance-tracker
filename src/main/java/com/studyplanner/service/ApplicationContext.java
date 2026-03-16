package com.studyplanner.service;

import com.studyplanner.dto.PlanGenerationRequest;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.DatabaseManager;
import com.studyplanner.persistence.DemoDataSeeder;
import com.studyplanner.persistence.RetentionTrainingDataRepository;
import com.studyplanner.persistence.ReviewRecordRepository;
import com.studyplanner.persistence.StudySessionRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.persistence.TopicRepository;
import com.studyplanner.service.pomodoro.PomodoroPlannerService;
import com.studyplanner.service.scheduler.SchedulerService;
import com.studyplanner.service.spacedrepetition.SpacedRepetitionService;

import java.time.LocalDate;

public class ApplicationContext {
    private final DatabaseManager databaseManager;
    private final SubjectRepository subjectRepository;
    private final TopicRepository topicRepository;
    private final StudySessionRepository studySessionRepository;
    private final ReviewRecordRepository reviewRecordRepository;
    private final DailyPlanRepository dailyPlanRepository;
    private final RetentionTrainingDataRepository retentionTrainingDataRepository;

    private final SubjectService subjectService;
    private final TopicService topicService;
    private final RetentionPredictionService retentionPredictionService;
    private final SchedulerService schedulerService;
    private final SessionLoggingService sessionLoggingService;
    private final PerformanceAnalyticsService performanceAnalyticsService;

    public ApplicationContext() {
        this.databaseManager = new DatabaseManager();
        databaseManager.initialize();

        this.subjectRepository = new SubjectRepository(databaseManager);
        this.topicRepository = new TopicRepository(databaseManager);
        this.studySessionRepository = new StudySessionRepository(databaseManager);
        this.reviewRecordRepository = new ReviewRecordRepository(databaseManager);
        this.dailyPlanRepository = new DailyPlanRepository(databaseManager);
        this.retentionTrainingDataRepository = new RetentionTrainingDataRepository(databaseManager);

        DemoDataSeeder demoDataSeeder = new DemoDataSeeder(
            subjectRepository,
            topicRepository,
            studySessionRepository,
            reviewRecordRepository,
            retentionTrainingDataRepository
        );
        demoDataSeeder.seedIfEmpty();

        this.retentionPredictionService = new RetentionPredictionService(
            retentionTrainingDataRepository,
            studySessionRepository,
            reviewRecordRepository
        );
        retentionPredictionService.retrainModel();

        PomodoroPlannerService pomodoroPlannerService = new PomodoroPlannerService();
        SpacedRepetitionService spacedRepetitionService = new SpacedRepetitionService();

        this.subjectService = new SubjectService(subjectRepository);
        this.topicService = new TopicService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            reviewRecordRepository,
            retentionPredictionService
        );
        this.schedulerService = new SchedulerService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            dailyPlanRepository,
            retentionPredictionService,
            pomodoroPlannerService
        );
        this.sessionLoggingService = new SessionLoggingService(
            topicRepository,
            studySessionRepository,
            reviewRecordRepository,
            dailyPlanRepository,
            spacedRepetitionService,
            retentionPredictionService
        );
        this.performanceAnalyticsService = new PerformanceAnalyticsService(
            topicRepository,
            subjectRepository,
            studySessionRepository,
            reviewRecordRepository,
            dailyPlanRepository,
            retentionPredictionService
        );

        schedulerService.getPlan(LocalDate.now())
            .orElseGet(() -> schedulerService.generateDailyPlan(
                new PlanGenerationRequest(LocalDate.now(), 180, 25, 5)
            ));
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public SubjectService getSubjectService() {
        return subjectService;
    }

    public TopicService getTopicService() {
        return topicService;
    }

    public RetentionPredictionService getRetentionPredictionService() {
        return retentionPredictionService;
    }

    public SchedulerService getSchedulerService() {
        return schedulerService;
    }

    public SessionLoggingService getSessionLoggingService() {
        return sessionLoggingService;
    }

    public PerformanceAnalyticsService getPerformanceAnalyticsService() {
        return performanceAnalyticsService;
    }
}
