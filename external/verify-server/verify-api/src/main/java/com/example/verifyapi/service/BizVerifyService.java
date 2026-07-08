package com.example.verifyapi.service;

import com.example.verifyapi.dto.biz.InternalBizRequest;
import com.example.verifyapi.dto.biz.InternalBizResponse;
import com.example.verifyapi.provider.BizLicenseProvider;
import org.springframework.stereotype.Service;

@Service
public class BizVerifyService {

    private final BizLicenseProvider bizLicenseProvider;

    public BizVerifyService(BizLicenseProvider bizLicenseProvider) {
        this.bizLicenseProvider = bizLicenseProvider;
    }

    public InternalBizResponse verify(InternalBizRequest request) {
        return bizLicenseProvider.verify(request);
    }
}
