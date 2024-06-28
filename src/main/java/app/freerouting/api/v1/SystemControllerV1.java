package app.freerouting.api.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemControllerV1
{
  public SystemControllerV1()
  {
    //FRLogger.info("SystemControllerV1 created");
  }

  @GetMapping("/status")
  public ResponseEntity<String> getSession(@RequestPart String sessionId)
  {
    return ResponseEntity.ok("Session: " + sessionId);
  }

}