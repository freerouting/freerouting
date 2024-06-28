package app.freerouting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/* This is a dummy class to use for logging */
@SpringBootApplication
@ComponentScan(basePackages = {"app.freerouting.api"})
public class Freerouting
{
  public static void main(String[] args)
  {
    SpringApplication.run(Freerouting.class, args);
  }
}