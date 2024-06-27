module app.freerouting {
  requires java.desktop;
  requires java.logging;
  requires org.apache.logging.log4j;
  requires com.google.gson;
  requires java.net.http;
  requires org.apache.logging.log4j.core;
  opens app.freerouting.gui to com.google.gson;
  opens app.freerouting.autoroute to com.google.gson;
  opens app.freerouting.board to com.google.gson;
  opens app.freerouting.management.segment to com.google.gson;
  opens app.freerouting.management to com.google.gson;
  opens app.freerouting.settings to com.google.gson;
}