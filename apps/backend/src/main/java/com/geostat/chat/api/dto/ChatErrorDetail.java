package com.geostat.chat.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatErrorDetail(
        @JsonProperty("code") String code,
        @JsonProperty("message") String message) {}
