package com.sentinelbank.aiagent.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AskRequest(@NotBlank @Size(max = 500) String question) {
}
