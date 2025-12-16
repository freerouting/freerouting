package app.freerouting.api;

import app.freerouting.api.v1.AnalyticsControllerV1;
import app.freerouting.api.v1.JobControllerV1;
import app.freerouting.api.v1.SessionControllerV1;
import app.freerouting.api.v1.SystemControllerV1;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.glassfish.jersey.media.sse.SseFeature;

import java.util.HashSet;
import java.util.Set;

@ApplicationPath("/")
public class FreeroutingApplication extends Application
{
  @Override
  public Set<Class<?>> getClasses()
  {
    Set<Class<?>> classes = new HashSet<>();
    classes.add(AnalyticsControllerV1.class);
    classes.add(JobControllerV1.class);
    classes.add(SessionControllerV1.class);
    classes.add(SystemControllerV1.class);
    classes.add(ApiExceptionMapper.class);
    classes.add(NotFoundExceptionMapper.class);
    classes.add(SseFeature.class);
    return classes;
  }
}
