package com.studyplanner;

import com.studyplanner.model.Topic;
import com.studyplanner.service.spacedrepetition.ReviewUpdateResult;
import com.studyplanner.service.spacedrepetition.SpacedRepetitionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpacedRepetitionServiceTest {
    @Test
    void appliesSm2ReviewUpdateForSuccessfulRecall() {
        Topic topic = new Topic(
            1,
            1,
            "Dynamic Programming",
            "",
            5,
            5,
            90,
            LocalDate.now().plusDays(5),
            55,
            LocalDate.now().minusDays(4),
            2.5,
            2,
            6,
            LocalDate.now(),
            false
        );

        SpacedRepetitionService service = new SpacedRepetitionService();
        ReviewUpdateResult result = service.applyReview(topic, 5, LocalDate.of(2026, 3, 16));

        assertEquals(3, result.topic().getRepetitionCount());
        assertTrue(result.topic().getIntervalDays() > 6);
        assertEquals(LocalDate.of(2026, 3, 16).plusDays(result.topic().getIntervalDays()), result.topic().getNextReviewDate());
        assertTrue(result.reviewRecord().getEasinessFactorAfter() >= result.reviewRecord().getEasinessFactorBefore());
    }
}
