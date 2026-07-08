package com.skep.clientorg.dto;

import com.skep.clientorg.ClientOrg;

public record ClientOrgResponse(Long id, String name, String code, String note, boolean active) {
    public static ClientOrgResponse from(ClientOrg c) {
        return new ClientOrgResponse(c.getId(), c.getName(), c.getCode(), c.getNote(), c.isActive());
    }
}
