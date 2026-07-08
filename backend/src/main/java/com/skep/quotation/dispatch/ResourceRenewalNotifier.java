package com.skep.quotation.dispatch;

import com.skep.company.Company;
import com.skep.company.CompanyRepository;
import com.skep.document.OwnerType;
import com.skep.notification.NotificationService;
import com.skep.quotation.QuotationRequest;
import com.skep.quotation.QuotationRequestRepository;
import com.skep.quotation.bundle.DocumentBundleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 공급사가 보완요청 없이 자발적으로 서류를 갱신했을 때,
 * 그 자원(장비/인원)을 배차받고 서류 묶음까지 받은 진행 중 BP 에게 1건 알림.
 *
 * 서류는 자원에 붙은 것이라 특정 BP 소유가 아니다. "이미 서류 묶음을 받은 BP" 만
 * 갱신 사실이 의미 있으므로 배차 + 묶음 발송 관계를 역추적해 수신자를 좁힌다.
 */
@Service
@RequiredArgsConstructor
public class ResourceRenewalNotifier {

    private final DispatchedEquipmentRepository dispatchedEq;
    private final DispatchedPersonRepository dispatchedPerson;
    private final DocumentBundleRepository bundles;
    private final QuotationRequestRepository requests;
    private final CompanyRepository companies;
    private final NotificationService notifications;

    /** 갱신된 서류의 자원을 받은 BP 들에게 알림. 수신자 없으면 no-op. */
    @Transactional
    public void notifyRenewal(OwnerType ownerType, Long ownerId, String documentTypeName, Long supplierCompanyId) {
        // (quotationRequestId, supplierCompanyId) 쌍 수집
        record Dispatch(Long requestId, Long supplierId) {}
        List<Dispatch> dispatches;
        if (ownerType == OwnerType.EQUIPMENT) {
            dispatches = dispatchedEq.findByEquipmentId(ownerId).stream()
                    .map(d -> new Dispatch(d.getQuotationRequestId(), d.getSupplierCompanyId())).toList();
        } else if (ownerType == OwnerType.PERSON) {
            dispatches = dispatchedPerson.findByPersonId(ownerId).stream()
                    .map(d -> new Dispatch(d.getQuotationRequestId(), d.getSupplierCompanyId())).toList();
        } else {
            return; // COMPANY 서류는 자원 배차 대상이 아님
        }
        if (dispatches.isEmpty()) return;

        Set<Long> bpCompanyIds = new HashSet<>();
        for (Dispatch d : dispatches) {
            // 서류 묶음을 실제로 받은 BP 만 (= 그 서류를 본 적 있음)
            if (!bundles.existsByQuotationRequestIdAndSupplierCompanyId(d.requestId(), d.supplierId())) continue;
            QuotationRequest qr = requests.findById(d.requestId()).orElse(null);
            if (qr == null) continue;
            Long bp = qr.getBpCompanyId() != null ? qr.getBpCompanyId() : qr.getOnBehalfOfBpCompanyId();
            if (bp != null) bpCompanyIds.add(bp);
        }
        if (bpCompanyIds.isEmpty()) return;

        String supplierName = supplierCompanyId != null
                ? companies.findById(supplierCompanyId).map(Company::getName).orElse("공급사") : "공급사";
        String docLabel = documentTypeName != null ? documentTypeName : "서류";
        for (Long bp : bpCompanyIds) {
            notifications.sendToCompany(bp, "DOCUMENT_RENEWED",
                    "공급사 서류 갱신",
                    supplierName + " — " + docLabel + " 서류가 갱신되었습니다.",
                    null, null, null);
        }
    }
}
