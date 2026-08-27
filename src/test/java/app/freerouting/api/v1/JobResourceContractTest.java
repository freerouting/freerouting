package app.freerouting.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.api.FreeroutingApplication;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JobResourceContractTest {

  private static final Set<Class<?>> JOB_RESOURCES =
      Set.of(JobInputResource.class, JobOutputResource.class, JobProgressResource.class);

  @Test
  void applicationRegistersAllSplitResourcesWithoutRegisteringFacade() {
    Set<Class<?>> registered = new FreeroutingApplication().getClasses();

    assertTrue(registered.containsAll(JOB_RESOURCES));
    assertFalse(registered.contains(JobControllerV1.class));
  }

  @Test
  void splitResourcesPreserveAllJobEndpointPaths() {
    Set<String> actual = new HashSet<>();
    for (Class<?> resource : JOB_RESOURCES) {
      Path classPath = resource.getAnnotation(Path.class);
      assertEquals("/v1/jobs", classPath.value());
      for (Method method : resource.getDeclaredMethods()) {
        String httpMethod = httpMethod(method);
        Path methodPath = method.getAnnotation(Path.class);
        if (httpMethod != null && methodPath != null) {
          actual.add(httpMethod + " " + classPath.value() + methodPath.value());
        }
      }
    }

    assertEquals(
        Set.of(
            "POST /v1/jobs/enqueue",
            "GET /v1/jobs/list/{sessionId}",
            "GET /v1/jobs/{jobId}",
            "PUT /v1/jobs/{jobId}/start",
            "PUT /v1/jobs/{jobId}/cancel",
            "POST /v1/jobs/{jobId}/settings",
            "POST /v1/jobs/{jobId}/input",
            "POST /v1/jobs/{jobId}/input/json",
            "POST /v1/jobs/{jobId}/rules",
            "GET /v1/jobs/{jobId}/output",
            "GET /v1/jobs/{jobId}/output/json",
            "GET /v1/jobs/{jobId}/output/stream",
            "GET /v1/jobs/{jobId}/output/json/stream",
            "GET /v1/jobs/{jobId}/logs",
            "GET /v1/jobs/{jobId}/logs/stream",
            "GET /v1/jobs/{jobId}/drc"),
        actual);
  }

  private static String httpMethod(Method method) {
    if (method.isAnnotationPresent(GET.class)) {
      return "GET";
    }
    if (method.isAnnotationPresent(POST.class)) {
      return "POST";
    }
    if (method.isAnnotationPresent(PUT.class)) {
      return "PUT";
    }
    if (method.isAnnotationPresent(DELETE.class)) {
      return "DELETE";
    }
    if (method.isAnnotationPresent(PATCH.class)) {
      return "PATCH";
    }
    if (method.isAnnotationPresent(HEAD.class)) {
      return "HEAD";
    }
    if (method.isAnnotationPresent(OPTIONS.class)) {
      return "OPTIONS";
    }
    return null;
  }
}
