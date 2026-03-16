package com.studyplanner.service.spacedrepetition;

import com.studyplanner.model.ReviewRecord;
import com.studyplanner.model.Topic;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;

public class SpacedRepetitionService {
    public ReviewUpdateResult applyReview(Topic topic, int quality, LocalDate reviewDate) {
        ValidationUtils.require(topic != null, "Topic is required");
        ValidationUtils.require(quality >= 0 && quality <= 5, "Review quality must be between 0 and 5");
        ValidationUtils.require(reviewDate != null, "Review date is required");

        double previousEf = topic.getEasinessFactor() > 0 ? topic.getEasinessFactor() : 2.5;
        int previousInterval = Math.max(0, topic.getIntervalDays());
        int repetitions = Math.max(0, topic.getRepetitionCount());

        double updatedEf = previousEf + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        updatedEf = Math.max(1.3, updatedEf);

        int updatedInterval;
        int updatedRepetitions;
        if (quality < 3) {
            updatedRepetitions = 0;
            updatedInterval = 1;
        } else {
            updatedRepetitions = repetitions + 1;
            if (repetitions == 0) {
                updatedInterval = 1;
            } else if (repetitions == 1) {
                updatedInterval = 6;
            } else {
                updatedInterval = Math.max(1, (int) Math.round(previousInterval * updatedEf));
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
}
