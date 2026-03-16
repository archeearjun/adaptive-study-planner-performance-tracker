package com.studyplanner.service.spacedrepetition;

import com.studyplanner.model.ReviewRecord;
import com.studyplanner.model.Topic;

public record ReviewUpdateResult(
    Topic topic,
    ReviewRecord reviewRecord
) {
}
