package com.studyplanner.service;

import com.studyplanner.model.Subject;
import com.studyplanner.persistence.SubjectRepository;
import com.studyplanner.utils.ValidationUtils;

import java.util.List;

public class SubjectService {
    private final SubjectRepository subjectRepository;

    public SubjectService(SubjectRepository subjectRepository) {
        this.subjectRepository = subjectRepository;
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
        return subjectRepository.save(subject);
    }

    public void deleteSubject(long subjectId) {
        subjectRepository.delete(subjectId);
    }
}
