package com.studyplanner.service;

import com.studyplanner.model.Subject;
import com.studyplanner.persistence.DailyPlanRepository;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.utils.ValidationUtils;

import java.time.LocalDate;
import java.util.List;

public class SubjectService {
    private final SubjectRepository subjectRepository;
    private final DailyPlanRepository dailyPlanRepository;

    public SubjectService(SubjectRepository subjectRepository, DailyPlanRepository dailyPlanRepository) {
        this.subjectRepository = subjectRepository;
        this.dailyPlanRepository = dailyPlanRepository;
    }

    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }

    public Subject saveSubject(Subject subject) {
        ValidationUtils.require(subject != null, "Subject is required");
        ValidationUtils.require(subject.getName() != null && !subject.getName().isBlank(), "Subject name is required");
        if (subject.getAccentColor() == null || subject.getAccentColor().isBlank()) {
            subject.setAccentColor("#2563EB");
        }
        Subject savedSubject = subjectRepository.save(subject);
        dailyPlanRepository.markPlansFromDateStale(LocalDate.now());
        return savedSubject;
    }

    public void deleteSubject(long subjectId) {
        subjectRepository.delete(subjectId);
        dailyPlanRepository.markPlansFromDateStale(LocalDate.now());
    }
}
