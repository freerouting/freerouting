package app.freerouting.api.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.ws.rs.core.Application;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds MCP tools from the OpenAPI model so REST and MCP contracts stay synchronized.
 */
public class OpenApiMcpToolRegistry {

  public record ToolOperation(
      String toolName,
      String method,
      String path,
      String summary,
      JsonObject inputSchema,
      JsonObject successSchema,
      List<Parameter> parameters,
      RequestBody requestBody) {
  }

  private final Map<String, ToolOperation> toolsByName;

  private OpenApiMcpToolRegistry(Map<String, ToolOperation> toolsByName) {
    this.toolsByName = toolsByName;
  }

  public static OpenApiMcpToolRegistry fromApplication(Application application)
      throws OpenApiConfigurationException {
    SwaggerConfiguration config = new SwaggerConfiguration()
        .resourcePackages(Set.of("app.freerouting.api"))
        .prettyPrint(true);

    OpenAPI openAPI = new JaxrsOpenApiContextBuilder()
        .application(application)
        .openApiConfiguration(config)
        .buildContext(true)
        .read();

    Map<String, ToolOperation> tools = new LinkedHashMap<>();
    if (openAPI == null || openAPI.getPaths() == null) {
      return new OpenApiMcpToolRegistry(tools);
    }

    for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
      String path = pathEntry.getKey();
      if (!isMcpEligiblePath(path)) {
        continue;
      }

      PathItem pathItem = pathEntry.getValue();
      if (pathItem == null || pathItem.readOperationsMap() == null) {
        continue;
      }

      for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
        PathItem.HttpMethod httpMethod = opEntry.getKey();
        Operation operation = opEntry.getValue();
        if (operation == null) {
          continue;
        }

        String method = httpMethod.name();
        String toolName = buildToolName(method, path);
        List<Parameter> mergedParams = mergeParameters(pathItem.getParameters(), operation.getParameters());

        JsonObject inputSchema = buildInputSchema(mergedParams, operation.getRequestBody());
        JsonObject successSchema = buildSuccessSchema(operation);
        String summary = operation.getSummary() != null ? operation.getSummary() : (method + " " + path);

        tools.put(toolName, new ToolOperation(
            toolName,
            method,
            path,
            summary,
            inputSchema,
            successSchema,
            mergedParams,
            operation.getRequestBody()));
      }
    }

    return new OpenApiMcpToolRegistry(tools);
  }

  public ToolOperation get(String toolName) {
    return toolsByName.get(toolName);
  }

  public JsonArray toMcpToolsArray() {
    JsonArray tools = new JsonArray();

    toolsByName.values().stream()
        .sorted(Comparator.comparing(ToolOperation::toolName))
        .forEach(tool -> {
          JsonObject item = new JsonObject();
          item.addProperty("name", tool.toolName());
          item.addProperty("description", tool.summary());
          item.add("inputSchema", tool.inputSchema());
          item.add("outputSchema", tool.successSchema());
          tools.add(item);
        });

    return tools;
  }

  private static boolean isMcpEligiblePath(String path) {
    return path != null
        && path.startsWith("/v1/")
        && !path.startsWith("/v1/mcp");
  }

  private static String buildToolName(String method, String path) {
    String normalizedPath = path.substring(1).replaceAll("[^A-Za-z0-9]+", "_");
    return (method + "_" + normalizedPath).toLowerCase(Locale.ROOT);
  }

  private static List<Parameter> mergeParameters(List<Parameter> pathParams, List<Parameter> operationParams) {
    List<Parameter> merged = new ArrayList<>();
    if (pathParams != null) {
      merged.addAll(pathParams);
    }
    if (operationParams != null) {
      merged.addAll(operationParams);
    }
    return merged;
  }

  private static JsonObject buildInputSchema(List<Parameter> parameters, RequestBody requestBody) {
    JsonObject root = new JsonObject();
    root.addProperty("type", "object");
    JsonObject properties = new JsonObject();
    JsonArray required = new JsonArray();

    JsonObject pathProperties = new JsonObject();
    JsonArray pathRequired = new JsonArray();
    JsonObject queryProperties = new JsonObject();
    JsonArray queryRequired = new JsonArray();

    for (Parameter parameter : parameters) {
      if (parameter == null || parameter.getName() == null) {
        continue;
      }

      JsonElement parameterSchema = toJsonSchema(parameter.getSchema());
      String in = parameter.getIn();
      if ("path".equalsIgnoreCase(in)) {
        pathProperties.add(parameter.getName(), parameterSchema);
        if (Boolean.TRUE.equals(parameter.getRequired())) {
          pathRequired.add(parameter.getName());
        }
      } else if ("query".equalsIgnoreCase(in)) {
        queryProperties.add(parameter.getName(), parameterSchema);
        if (Boolean.TRUE.equals(parameter.getRequired())) {
          queryRequired.add(parameter.getName());
        }
      }
    }

    if (pathProperties.size() > 0) {
      JsonObject path = new JsonObject();
      path.addProperty("type", "object");
      path.add("properties", pathProperties);
      path.addProperty("additionalProperties", false);
      if (pathRequired.size() > 0) {
        path.add("required", pathRequired);
      }
      properties.add("path", path);
      if (pathRequired.size() > 0) {
        required.add("path");
      }
    }

    if (queryProperties.size() > 0) {
      JsonObject query = new JsonObject();
      query.addProperty("type", "object");
      query.add("properties", queryProperties);
      query.addProperty("additionalProperties", false);
      if (queryRequired.size() > 0) {
        query.add("required", queryRequired);
      }
      properties.add("query", query);
      if (queryRequired.size() > 0) {
        required.add("query");
      }
    }

    JsonElement bodySchema = getJsonRequestBodySchema(requestBody);
    if (bodySchema != null) {
      properties.add("body", bodySchema);
      if (requestBody != null && Boolean.TRUE.equals(requestBody.getRequired())) {
        required.add("body");
      }
    }

    root.add("properties", properties);
    root.addProperty("additionalProperties", false);
    if (required.size() > 0) {
      root.add("required", required);
    }

    return root;
  }

  private static JsonObject buildSuccessSchema(Operation operation) {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");

    JsonObject properties = new JsonObject();
    JsonObject statusSchema = new JsonObject();
    statusSchema.addProperty("type", "integer");
    properties.add("status", statusSchema);

    JsonObject contentTypeSchema = new JsonObject();
    contentTypeSchema.addProperty("type", "string");
    properties.add("contentType", contentTypeSchema);

    JsonObject bodySchema = new JsonObject();
    bodySchema.addProperty("description", "HTTP response body, parsed as JSON when possible");
    properties.add("body", bodySchema);

    schema.add("properties", properties);

    JsonArray required = new JsonArray();
    required.add("status");
    required.add("contentType");
    required.add("body");
    schema.add("required", required);

    if (operation != null && operation.getResponses() != null) {
      ApiResponse okResponse = operation.getResponses().get("200");
      if (okResponse != null && okResponse.getDescription() != null) {
        schema.addProperty("description", okResponse.getDescription());
      }
    }

    return schema;
  }

  private static JsonElement getJsonRequestBodySchema(RequestBody requestBody) {
    if (requestBody == null || requestBody.getContent() == null) {
      return null;
    }

    MediaType jsonMedia = requestBody.getContent().get("application/json");
    if (jsonMedia != null) {
      return toJsonSchema(jsonMedia.getSchema());
    }

    // Fall back to wildcard media type if JSON is not explicitly declared.
    MediaType anyMedia = requestBody.getContent().get("*/*");
    if (anyMedia != null) {
      return toJsonSchema(anyMedia.getSchema());
    }

    return null;
  }

  private static JsonElement toJsonSchema(Schema<?> schema) {
    if (schema == null) {
      JsonObject fallback = new JsonObject();
      fallback.addProperty("type", "string");
      return fallback;
    }

    String json = io.swagger.v3.core.util.Json.pretty(schema);
    return JsonParser.parseString(json);
  }
}