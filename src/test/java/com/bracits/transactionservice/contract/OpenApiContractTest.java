package com.bracits.transactionservice.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * The authored contract is valid OpenAPI 3.1, is served at /openapi.yaml, and matches the
 * implemented endpoints.
 */
class OpenApiContractTest extends ApiTestSupport {

  private static String yaml;
  private static SwaggerParseResult parsed;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping mappings;

  @Autowired
  MockMvc mvc;

  @BeforeAll
  static void parse() throws IOException {
    yaml = new ClassPathResource("openapi/transaction-api.yaml").getContentAsString(
        StandardCharsets.UTF_8);

    ParseOptions options = new ParseOptions();
    options.setResolve(true);
    parsed = new OpenAPIParser().readContents(yaml, null, options);
  }

  @Test
  void contractIsValidOpenApi31() {
    assertThat(parsed.getMessages()).isEmpty();
    assertThat(parsed.getOpenAPI()).isNotNull();
    assertThat(parsed.getOpenAPI().getOpenapi()).startsWith("3.1");
  }

  @Test
  void contractMatchesImplementedEndpoints() {
    assertThat(specOperations()).isEqualTo(implementedOperations());
  }

  @Test
  void everyApiOperationIsSecuredAndDocuments401() {
    OpenAPI api = parsed.getOpenAPI();
    api.getPaths().forEach((path, item) -> {
      if (!path.startsWith("/api/")) {
        return;
      }

      item.readOperationsMap().forEach((method, operation) -> {
        assertThat(isSecured(api, operation)).as("%s %s secured", method, path).isTrue();
        assertThat(operation.getResponses()).as("%s %s responses", method, path).containsKey("401");
      });
    });
  }

  @Test
  void contractIsServedWithoutApiKey() throws Exception {
    mvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/yaml"))
        .andExpect(content().bytes(yaml.getBytes(StandardCharsets.UTF_8)));
  }

  private Set<String> specOperations() {
    return parsed.getOpenAPI().getPaths().entrySet().stream()
        .flatMap(path -> path.getValue().readOperationsMap().keySet().stream()
            .map(method -> method.name() + " " + path.getKey()))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private Set<String> implementedOperations() {
    return mappings.getHandlerMethods().keySet().stream()
        .flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream()
            .filter(path -> path.startsWith("/api/") || path.equals("/openapi.yaml"))
            .flatMap(path -> info.getMethodsCondition().getMethods().stream()
                .map(method -> method.name() + " " + path)))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private static boolean isSecured(OpenAPI api, Operation operation) {
    if (operation.getSecurity() != null) {
      return !operation.getSecurity().isEmpty();
    }
    return api.getSecurity() != null && api.getSecurity().stream()
        .anyMatch(requirement -> !requirement.isEmpty());
  }
}
