package app.freerouting.api.v1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/session")
public class SessionControllerV1
{
  public SessionControllerV1()
  {
    //FRLogger.info("SessionControllerV1 created");
  }

  @GetMapping("/get")
  public ResponseEntity<String> getSession(@RequestPart String sessionId)
  {
    return ResponseEntity.ok("Session: " + sessionId);
  }

  //  @PostMapping("/new")
  //  public ResponseEntity<Session> createData(@RequestBody Session session)
  //  {
  //    // Process data
  //    return ResponseEntity.status(HttpStatus.CREATED).body(session);
  //  }
}