package com.mcpgateway.dto.response;

import java.time.LocalDate;

/** One day of the call chart. */
public record DailyCallCountResponse(LocalDate day, long succeeded, long failed) {
}
