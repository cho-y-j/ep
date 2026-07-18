package com.skep.legalinspection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SafetyCheckTemplateRepository extends JpaRepository<SafetyCheckTemplate, Long> {
    List<SafetyCheckTemplate> findAllByOrderByIdDesc();

    /** 점검원 화면 — target 별 활성 템플릿(가장 최근 1건). */
    Optional<SafetyCheckTemplate> findFirstByTargetAndActiveTrueOrderByIdDesc(String target);
}
