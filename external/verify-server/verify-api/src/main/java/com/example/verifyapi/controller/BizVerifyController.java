package com.example.verifyapi.controller;

import com.example.verifyapi.dto.biz.InternalBizRequest;
import com.example.verifyapi.dto.biz.InternalBizResponse;
import com.example.verifyapi.service.BizVerifyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/verify")
public class BizVerifyController {

    private final BizVerifyService bizVerifyService;

    public BizVerifyController(BizVerifyService bizVerifyService) {
        this.bizVerifyService = bizVerifyService;
    }

    @PostMapping("/biz")
    public ResponseEntity<InternalBizResponse> verifyBiz(@RequestBody InternalBizRequest request) {
        InternalBizResponse response = bizVerifyService.verify(request);
        return ResponseEntity.ok(response);
    }
}
