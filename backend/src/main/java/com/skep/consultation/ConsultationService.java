package com.skep.consultation;

import com.skep.common.ApiException;
import com.skep.consultation.dto.ConsultationResponse;
import com.skep.consultation.dto.CreateConsultationRequest;
import com.skep.notification.NotificationService;
import com.skep.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsultationService {

    private final ConsultationRepository repo;
    private final NotificationService notifications;

    public ConsultationResponse create(CreateConsultationRequest req) {
        Consultation c = Consultation.create(
                req.companyName().trim(),
                req.contactName().trim(),
                req.phone().trim(),
                trimToNull(req.email()),
                trimToNull(req.message()));
        repo.save(c);
        notifications.sendSystem(NotificationType.CONSULTATION_REQUESTED,
                "새 상담 요청",
                c.getCompanyName() + " " + c.getContactName() + " " + c.getPhone(),
                "CONSULTATION", c.getId(), null);
        return ConsultationResponse.from(c);
    }

    @Transactional(readOnly = true)
    public List<ConsultationResponse> list() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(ConsultationResponse::from).toList();
    }

    public ConsultationResponse handle(Long id) {
        Consultation c = repo.findById(id).orElseThrow(() ->
                ApiException.notFound("CONSULTATION_NOT_FOUND", "상담 요청을 찾을 수 없습니다"));
        c.markHandled();
        return ConsultationResponse.from(c);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
