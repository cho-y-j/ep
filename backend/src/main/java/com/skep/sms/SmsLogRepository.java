package com.skep.sms;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SmsLogRepository extends JpaRepository<SmsLog, Long> {

    /** 알림톡 발송 이력 — provider 가 DAON 으로 시작(DAON_ALIMTALK/DAON_SMS) 하는 최근 100건. */
    List<SmsLog> findTop100ByProviderStartingWithOrderByIdDesc(String providerPrefix);
}
