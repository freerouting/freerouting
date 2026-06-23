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

    Map<String, Schema> componentsSchemas = openAPI.getComponents() != null ? openAPI.getComponents().getSchemas() : null;

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

        JsonObject inputSchema = buildInputSchema(mergedParams, operation.getRequestBody(), componentsSchemas);
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
    // Register custom encode_base64 tool
    JsonObject encodeInput = new JsonObject();
    encodeInput.addProperty("type", "object");
    JsonObject encodeInputProps = new JsonObject();
    JsonObject encodeTextInput = new JsonObject();
    encodeTextInput.addProperty("type", "string");
    encodeTextInput.addProperty("description", "The UTF-8 text content to encode.");
    encodeInputProps.add("text", encodeTextInput);
    encodeInput.add("properties", encodeInputProps);
    JsonArray encodeRequired = new JsonArray();
    encodeRequired.add("text");
    encodeInput.add("required", encodeRequired);
    encodeInput.addProperty("additionalProperties", false);

    JsonObject encodeOutput = new JsonObject();
    encodeOutput.addProperty("type", "object");
    JsonObject encodeOutputProps = new JsonObject();
    JsonObject encodeBase64Output = new JsonObject();
    encodeBase64Output.addProperty("type", "string");
    encodeBase64Output.addProperty("description", "The resulting Base64 encoded string.");
    encodeOutputProps.add("base64", encodeBase64Output);
    encodeOutput.add("properties", encodeOutputProps);
    JsonArray encodeOutRequired = new JsonArray();
    encodeOutRequired.add("base64");
    encodeOutput.add("required", encodeOutRequired);
    encodeOutput.addProperty("additionalProperties", false);

    tools.put("encode_base64", new ToolOperation(
        "encode_base64",
        "custom",
        "custom",
        "Encodes a UTF-8 text string (like a DSN, JSON, or RULES file content) into a Base64 string. IMPORTANT: You MUST use this tool to perform base64 encoding; do NOT call external terminal shell commands (like powershell or base64) to perform this conversion.",
        encodeInput,
        encodeOutput,
        new ArrayList<>(),
        null
    ));

    // Register custom decode_base64 tool
    JsonObject decodeInput = new JsonObject();
    decodeInput.addProperty("type", "object");
    JsonObject decodeInputProps = new JsonObject();
    JsonObject decodeBase64Input = new JsonObject();
    decodeBase64Input.addProperty("type", "string");
    decodeBase64Input.addProperty("description", "The Base64 string to decode.");
    decodeInputProps.add("base64", decodeBase64Input);
    decodeInput.add("properties", decodeInputProps);
    JsonArray decodeRequired = new JsonArray();
    decodeRequired.add("base64");
    decodeInput.add("required", decodeRequired);
    decodeInput.addProperty("additionalProperties", false);

    JsonObject decodeOutput = new JsonObject();
    decodeOutput.addProperty("type", "object");
    JsonObject decodeOutputProps = new JsonObject();
    JsonObject decodeTextOutput = new JsonObject();
    decodeTextOutput.addProperty("type", "string");
    decodeTextOutput.addProperty("description", "The resulting decoded UTF-8 text string.");
    decodeOutputProps.add("text", decodeTextOutput);
    decodeOutput.add("properties", decodeOutputProps);
    JsonArray decodeOutRequired = new JsonArray();
    decodeOutRequired.add("text");
    decodeOutput.add("required", decodeOutRequired);
    decodeOutput.addProperty("additionalProperties", false);

    tools.put("decode_base64", new ToolOperation(
        "decode_base64",
        "custom",
        "custom",
        "Decodes a Base64 string (like routed SES or JSON output files) back into a UTF-8 text string. IMPORTANT: You MUST use this tool to perform base64 decoding; do NOT call external terminal shell commands (like powershell or base64) to perform this conversion.",
        decodeInput,
        decodeOutput,
        new ArrayList<>(),
        null
    ));

    // Register custom upload_job_input_from_local_file tool
    JsonObject uploadInput = new JsonObject();
    uploadInput.addProperty("type", "object");
    JsonObject uploadInputProps = new JsonObject();
    JsonObject jobIdUploadInput = new JsonObject();
    jobIdUploadInput.addProperty("type", "string");
    jobIdUploadInput.addProperty("description", "Unique identifier of the job");
    JsonObject filePathUploadInput = new JsonObject();
    filePathUploadInput.addProperty("type", "string");
    filePathUploadInput.addProperty("description", "The absolute or relative path to the local PCB design file (typically Specctra DSN format) to upload.");
    uploadInputProps.add("jobId", jobIdUploadInput);
    uploadInputProps.add("filePath", filePathUploadInput);
    uploadInput.add("properties", uploadInputProps);
    JsonArray uploadRequired = new JsonArray();
    uploadRequired.add("jobId");
    uploadRequired.add("filePath");
    uploadInput.add("required", uploadRequired);
    uploadInput.addProperty("additionalProperties", false);

    JsonObject uploadOutput = new JsonObject();
    uploadOutput.addProperty("type", "object");
    JsonObject uploadOutputProps = new JsonObject();
    JsonObject uploadStatus = new JsonObject();
    uploadStatus.addProperty("type", "string");
    uploadStatus.addProperty("description", "Status description of the upload operation.");
    uploadOutputProps.add("message", uploadStatus);
    uploadOutput.add("properties", uploadOutputProps);
    JsonArray uploadOutRequired = new JsonArray();
    uploadOutRequired.add("message");
    uploadOutput.add("required", uploadOutRequired);
    uploadOutput.addProperty("additionalProperties", false);

    tools.put("upload_job_input_from_local_file", new ToolOperation(
        "upload_job_input_from_local_file",
        "custom",
        "custom",
        "Reads a local PCB design file, encodes it to Base64 in-memory, and uploads it to the routing engine. Works in both local (offline) and cloud (online) MCP configurations. Use this tool instead of reading and transmitting file contents to conserve context window.",
        uploadInput,
        uploadOutput,
        new ArrayList<>(),
        null
    ));

    // Register custom download_job_output_to_local_file tool
    JsonObject downloadInput = new JsonObject();
    downloadInput.addProperty("type", "object");
    JsonObject downloadInputProps = new JsonObject();
    JsonObject jobIdDownloadInput = new JsonObject();
    jobIdDownloadInput.addProperty("type", "string");
    jobIdDownloadInput.addProperty("description", "Unique identifier of the job");
    JsonObject filePathDownloadInput = new JsonObject();
    filePathDownloadInput.addProperty("type", "string");
    filePathDownloadInput.addProperty("description", "The path on the local disk where the routed output layout (Specctra SES format) should be saved.");
    downloadInputProps.add("jobId", jobIdDownloadInput);
    downloadInputProps.add("filePath", filePathDownloadInput);
    downloadInput.add("properties", downloadInputProps);
    JsonArray downloadRequired = new JsonArray();
    downloadRequired.add("jobId");
    downloadRequired.add("filePath");
    downloadInput.add("required", downloadRequired);
    downloadInput.addProperty("additionalProperties", false);

    JsonObject downloadOutput = new JsonObject();
    downloadOutput.addProperty("type", "object");
    JsonObject downloadOutputProps = new JsonObject();
    JsonObject downloadStatus = new JsonObject();
    downloadStatus.addProperty("type", "string");
    downloadStatus.addProperty("description", "Status description of the download operation.");
    downloadOutputProps.add("message", downloadStatus);
    downloadOutput.add("properties", downloadOutputProps);
    JsonArray downloadOutRequired = new JsonArray();
    downloadOutRequired.add("message");
    downloadOutput.add("required", downloadOutRequired);
    downloadOutput.addProperty("additionalProperties", false);

    tools.put("download_job_output_to_local_file", new ToolOperation(
        "download_job_output_to_local_file",
        "custom",
        "custom",
        "Downloads the completed routing output, decodes it from Base64 in-memory, and saves it directly to a local file. Works in both local (offline) and cloud (online) MCP configurations. Use this tool instead of retrieving file contents to conserve context window.",
        downloadInput,
        downloadOutput,
        new ArrayList<>(),
        null
    ));

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
    String cleanPath = path.toLowerCase(Locale.ROOT).trim();
    if (cleanPath.endsWith("/")) {
      cleanPath = cleanPath.substring(0, cleanPath.length() - 1);
    }
    
    if (cleanPath.equals("/v1/system/status") && "GET".equalsIgnoreCase(method)) return "get_system_status";
    if (cleanPath.equals("/v1/system/environment") && "GET".equalsIgnoreCase(method)) return "get_system_environment";
    
    if (cleanPath.equals("/v1/sessions/create") && "POST".equalsIgnoreCase(method)) return "create_session";
    if (cleanPath.equals("/v1/sessions") && "GET".equalsIgnoreCase(method)) return "list_sessions";
    if (cleanPath.startsWith("/v1/sessions/") && cleanPath.endsWith("/logs") && "GET".equalsIgnoreCase(method)) return "get_session_logs";
    if (cleanPath.startsWith("/v1/sessions/") && cleanPath.endsWith("/monitor") && "PUT".equalsIgnoreCase(method)) return "monitor_session";
    if (cleanPath.startsWith("/v1/sessions/") && "GET".equalsIgnoreCase(method)) return "get_session_details";
    
    if (cleanPath.equals("/v1/jobs/enqueue") && "POST".equalsIgnoreCase(method)) return "enqueue_job";
    if (cleanPath.startsWith("/v1/jobs/list/") && "GET".equalsIgnoreCase(method)) return "list_jobs";
    
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/drc") && "GET".equalsIgnoreCase(method)) return "get_job_drc_report";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/logs/stream") && "GET".equalsIgnoreCase(method)) return "stream_job_logs";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/logs") && "GET".equalsIgnoreCase(method)) return "get_job_logs";
    
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/output/json/stream") && "GET".equalsIgnoreCase(method)) return "stream_job_output_json";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/output/json") && "GET".equalsIgnoreCase(method)) return "download_job_output_json";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/output/stream") && "GET".equalsIgnoreCase(method)) return "stream_job_output_file";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/output") && "GET".equalsIgnoreCase(method)) return "download_job_output_file";
    
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/input/json") && "POST".equalsIgnoreCase(method)) return "upload_job_input_json";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/input") && "POST".equalsIgnoreCase(method)) return "upload_job_input_file";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/settings") && "POST".equalsIgnoreCase(method)) return "update_job_settings";
    
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/cancel") && "put".equalsIgnoreCase(method)) return "cancel_job";
    if (cleanPath.startsWith("/v1/jobs/") && cleanPath.endsWith("/start") && "put".equalsIgnoreCase(method)) return "start_job";
    if (cleanPath.startsWith("/v1/jobs/") && "GET".equalsIgnoreCase(method)) return "get_job_details";
    
    if (cleanPath.equals("/v1/analytics/identify") && "POST".equalsIgnoreCase(method)) return "identify_user";
    if (cleanPath.equals("/v1/analytics/track") && "POST".equalsIgnoreCase(method)) return "track_user_action";

    // Fallback if not mapped
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

  private static JsonObject buildInputSchema(List<Parameter> parameters, RequestBody requestBody, Map<String, Schema> componentsSchemas) {
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
      parameterSchema = resolveRefs(parameterSchema, componentsSchemas, new java.util.HashSet<>());
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
      bodySchema = resolveRefs(bodySchema, componentsSchemas, new java.util.HashSet<>());
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

  private static JsonElement resolveRefs(JsonElement element, Map<String, Schema> componentsSchemas, Set<String> visited) {
    if (element == null) {
      return null;
    }
    if (element.isJsonObject()) {
      JsonObject obj = element.getAsJsonObject();
      if (obj.has("$ref")) {
        String ref = obj.get("$ref").getAsString();
        String prefix = "#/components/schemas/";
        if (ref.startsWith(prefix)) {
          String schemaName = ref.substring(prefix.length());
          if (!visited.contains(schemaName) && componentsSchemas != null && componentsSchemas.containsKey(schemaName)) {
            visited.add(schemaName);
            Schema<?> referencedSchema = componentsSchemas.get(schemaName);
            JsonElement referencedJson = toJsonSchema(referencedSchema);
            JsonElement resolvedReferenced = resolveRefs(referencedJson, componentsSchemas, visited);
            visited.remove(schemaName);
            return resolvedReferenced;
          }
        }
      }
      
      JsonObject newObj = new JsonObject();
      for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
        newObj.add(entry.getKey(), resolveRefs(entry.getValue(), componentsSchemas, visited));
      }
      return newObj;
    } else if (element.isJsonArray()) {
      JsonArray arr = element.getAsJsonArray();
      JsonArray newArr = new JsonArray();
      for (JsonElement item : arr) {
        newArr.add(resolveRefs(item, componentsSchemas, visited));
      }
      return newArr;
    }
    return element;
  }
}