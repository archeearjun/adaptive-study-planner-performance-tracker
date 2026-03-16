package com.studyplanner.service.pomodoro;

import com.studyplanner.model.PomodoroBlock;
import com.studyplanner.model.PomodoroStatus;

import java.util.ArrayList;
import java.util.List;

public class PomodoroPlannerService {
    public List<PomodoroBlock> buildBlocks(int plannedMinutes, int focusMinutes, int shortBreakMinutes) {
        int remaining = Math.max(0, plannedMinutes);
        int blockIndex = 1;
        List<PomodoroBlock> blocks = new ArrayList<>();

        while (remaining > 0) {
            int focus = Math.min(Math.max(15, focusMinutes), remaining);
            int breakMinutes = remaining > focus ? Math.max(0, shortBreakMinutes) : 0;
            blocks.add(new PomodoroBlock(0, 0, blockIndex++, focus, breakMinutes, PomodoroStatus.PLANNED, null));
            remaining -= focus;
        }

        if (blocks.isEmpty() && plannedMinutes > 0) {
            blocks.add(new PomodoroBlock(0, 0, 1, plannedMinutes, 0, PomodoroStatus.PLANNED, null));
        }
        return blocks;
    }
}
