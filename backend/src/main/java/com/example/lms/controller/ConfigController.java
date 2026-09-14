package com.example.lms.controller;

import com.example.lms.dto.AppConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@Tag(name = "Config", description = "Public application configuration")
public class ConfigController {
  private final boolean swaggerEnabled;

  public ConfigController(@Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerEnabled) {
    this.swaggerEnabled = swaggerEnabled;
  }

  @GetMapping
  @Operation(summary = "App config", description = "Public feature flags for the frontend.")
  public AppConfigResponse config() {
    return new AppConfigResponse(swaggerEnabled);
  }
}
