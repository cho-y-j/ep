package com.skep.safetyboard;

import com.skep.safetyboard.dto.SafetyBoardDtos.BoardSite;
import com.skep.safetyboard.dto.SafetyBoardDtos.RecipientStatus;
import com.skep.safetyboard.dto.SafetyBoardDtos.SiteBoard;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * P4a 안전 상황판 — 맵 중심 단일 관제. BP·ADMIN·CLIENT 공용(현장 스코프는 서비스에서 재검증).
 * SecurityConfig 에서 /api/safety-board/** = ADMIN/BP/CLIENT (CLIENT 전역 차단의 화이트리스트 예외).
 */
@RestController
@RequestMapping("/api/safety-board")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','BP','CLIENT')")
public class SafetyBoardController {

    private final SafetyBoardService service;

    /** 접근 가능 현장 목록(현장 선택 드롭다운). */
    @GetMapping("/sites")
    public List<BoardSite> sites(@CurrentUser AuthenticatedUser actor) {
        return service.listSites(actor);
    }

    /** 현장 상세 보드 — 지도 마커 + 요약 스트립 + 공지. 접근 불가 현장은 403. */
    @GetMapping("/sites/{id}")
    public SiteBoard board(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.board(id, actor);
    }

    /** 공지 수신자 확인 상태(미확인자 명단 드릴다운). */
    @GetMapping("/announcements/{id}/recipients")
    public List<RecipientStatus> recipients(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return service.recipients(id, actor);
    }
}
