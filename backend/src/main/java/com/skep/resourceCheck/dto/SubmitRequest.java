package com.skep.resourceCheck.dto;

import jakarta.validation.constraints.NotNull;

public record SubmitRequest(@NotNull Long documentId) {}
