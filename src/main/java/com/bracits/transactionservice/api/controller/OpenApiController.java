package com.bracits.transactionservice.api.controller;

import com.bracits.transactionservice.api.constant.ApiConstants;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the authoritative contract {@code openapi/transaction-api.yaml} (contract-first).
 */
@RestController
public class OpenApiController {

  private final Resource contract = new ClassPathResource(ApiConstants.OPENAPI_CLASSPATH_LOCATION);

  @GetMapping(path = ApiConstants.OPENAPI_PATH, produces = ApiConstants.MEDIA_TYPE_YAML)
  public ResponseEntity<Resource> openApi() {
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(ApiConstants.MEDIA_TYPE_YAML))
        .body(contract);
  }
}
