module app.freerouting {
  requires java.desktop;
  requires java.logging;
  requires java.net.http;
  requires com.google.gson;
  requires org.apache.logging.log4j;
  requires org.apache.logging.log4j.core;

  opens app.freerouting.gui to com.google.gson;
  opens app.freerouting.autoroute to com.google.gson;
  opens app.freerouting.board to com.google.gson;
  opens app.freerouting.management.segment to com.google.gson;
  opens app.freerouting.management to com.google.gson;
  opens app.freerouting.settings to com.google.gson;

  opens app.freerouting.api.v1 to jakarta.ws.rs;
}