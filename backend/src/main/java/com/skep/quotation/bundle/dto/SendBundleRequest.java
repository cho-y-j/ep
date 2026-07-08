package com.skep.quotation.bundle.dto;

import java.util.List;

public record SendBundleRequest(
        boolean includeEmail,
        String notes,
        /** includeEmail=true 일 때 수신자 명시. null/비어있으면 BP admin 자동. */
        List<String> emails
) {
}
