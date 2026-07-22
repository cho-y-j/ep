package com.skep.company;

import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 내 회사 취급 장비종류 설정 — 조회는 본인 회사 스코프, 저장은 장비공급사 master 전용(서비스에서 강제). */
@RestController
@RequestMapping("/api/companies/me/equipment-types")
public class CompanyEquipmentTypeController {

    private final CompanyEquipmentTypeService service;

    public CompanyEquipmentTypeController(CompanyEquipmentTypeService service) {
        this.service = service;
    }

    @GetMapping
    public List<String> list(@CurrentUser AuthenticatedUser actor) {
        return service.list(actor);
    }

    public record UpdateRequest(List<String> codes) {}

    @PutMapping
    public List<String> replace(@RequestBody UpdateRequest req, @CurrentUser AuthenticatedUser actor) {
        return service.replace(req.codes(), actor);
    }
}
