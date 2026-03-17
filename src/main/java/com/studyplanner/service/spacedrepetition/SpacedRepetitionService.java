package com.studyplanner.service.spacedrepetition;

import com.studyplanner.model.ReviewRecord;
import com.studyplanner.model.Topic;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;

public class SpacedRepetitionService {
    private static final double MIN_EASINESS_FACTOR = 1.3;
    private static final double DEFAULT_EASINESS_FACTOR = 2.5;

    public ReviewUpdateResult applyReview(Topic topic, int quality, LocalDate reviewDate) {
        ValidationUtils.require(topic != null, "Topic is required");
        ValidationUtils.require(quality >= 0 && quality <= 5, "Review quality must be between 0 and 5");
        ValidationUtils.require(reviewDate != null, "Review date is required");
        ValidationUtils.require(topic.getLastStudiedDate() == null || !reviewDate.isBefore(topic.getLastStudiedDate()),
            "Review date cannot be earlier than the topic's last studied date");

        double previousEf = topic.getEasinessFactor() > 0 ? topic.getEasinessFactor() : DEFAULT_EASINESS_FACTOR;
        int previousInterval = Math.max(0, topic.getIntervalDays());
        int repetitions = Math.max(0, topic.getRepetitionCount());

        int updatedInterval;
        int updatedRepetitions;
        double updatedEf = previousEf;
        if (quality < 3) {
            updatedRepetitions = 0;
            updatedInterval = 1;
        } else {
            updatedEf = updatedEasinessFactor(previousEf, quality);
            updatedRepetitions = repetitions + 1;
            if (repetitions == 0) {
                updatedInterval = 1;
            } else if (repetitions == 1) {
                updatedInterval = 6;
            } else {
                updatedInterval = Math.max(1, (int) Math.ceil(Math.max(1, previousInterval) * previousEf));
            }
        }

        LocalDate nextReviewDate = reviewDate.plusDays(updatedInterval);

        topic.setEasinessFactor(updatedEf);
        topic.setRepetitionCount(updatedRepetitions);
        topic.setIntervalDays(updatedInterval);
        topic.setLastStudiedDate(reviewDate);
        topic.setNextReviewDate(nextReviewDate);

        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setTopicId(topic.getId());
        reviewRecord.setReviewDate(reviewDate);
        reviewRecord.setQuality(quality);
        reviewRecord.setEasinessFactorBefore(previousEf);
        reviewRecord.setEasinessFactorAfter(updatedEf);
        reviewRecord.setIntervalBefore(previousInterval);
        reviewRecord.setIntervalAfter(updatedInterval);
        reviewRecord.setNextReviewDate(nextReviewDate);

        return new ReviewUpdateResult(topic, reviewRecord);
    }

    private double updatedEasinessFactor(double previousEf, int quality) {
        double updatedEf = previousEf + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        return Math.max(MIN_EASINESS_FACTOR, updatedEf);
    }
}
