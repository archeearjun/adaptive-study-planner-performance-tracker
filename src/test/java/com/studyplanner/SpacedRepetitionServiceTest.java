package com.studyplanner;

import com.studyplanner.model.Topic;
import com.studyplanner.service.spacedrepetition.ReviewUpdateResult;
import com.studyplanner.service.spacedrepetition.SpacedRepetitionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(15, result.topic().getIntervalDays());
        assertEquals(LocalDate.of(2026, 3, 31), result.topic().getNextReviewDate());
        assertEquals(2.6, result.reviewRecord().getEasinessFactorAfter(), 0.0001);
        assertTrue(result.reviewRecord().getEasinessFactorAfter() > result.reviewRecord().getEasinessFactorBefore());
    }

    @Test
    void resetsFailedReviewsWithoutLoweringEasinessFactor() {
        Topic topic = new Topic(
            1,
            1,
            "Operating Systems",
            "",
            4,
            4,
            60,
            null,
            70,
            LocalDate.of(2026, 3, 15),
            2.2,
            5,
            24,
            LocalDate.of(2026, 3, 12),
            false
        );

        SpacedRepetitionService service = new SpacedRepetitionService();
        ReviewUpdateResult result = service.applyReview(topic, 2, LocalDate.of(2026, 3, 16));

        assertEquals(0, result.topic().getRepetitionCount());
        assertEquals(1, result.topic().getIntervalDays());
        assertEquals(LocalDate.of(2026, 3, 17), result.topic().getNextReviewDate());
        assertEquals(2.2, result.topic().getEasinessFactor(), 0.0001);
        assertEquals(2.2, result.reviewRecord().getEasinessFactorAfter(), 0.0001);
    }

    @Test
    void anchorsOverdueReviewsToTheActualReviewDate() {
        Topic topic = new Topic(
            1,
            1,
            "Databases",
            "",
            4,
            3,
            45,
            null,
            65,
            LocalDate.of(2026, 3, 1),
            2.5,
            2,
            6,
            LocalDate.of(2026, 3, 7),
            false
        );

        SpacedRepetitionService service = new SpacedRepetitionService();
        ReviewUpdateResult result = service.applyReview(topic, 4, LocalDate.of(2026, 3, 16));

        assertEquals(15, result.topic().getIntervalDays());
        assertEquals(LocalDate.of(2026, 3, 31), result.topic().getNextReviewDate());
    }

    @Test
    void rejectsBackdatedReviewDates() {
        Topic topic = new Topic(
            1,
            1,
            "Graphs",
            "",
            5,
            4,
            75,
            null,
            60,
            LocalDate.of(2026, 3, 20),
            2.5,
            2,
            6,
            LocalDate.of(2026, 3, 22),
            false
        );

        SpacedRepetitionService service = new SpacedRepetitionService();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.applyReview(topic, 5, LocalDate.of(2026, 3, 19))
        );

        assertEquals("Review date cannot be earlier than the topic's last studied date", exception.getMessage());
    }
}
