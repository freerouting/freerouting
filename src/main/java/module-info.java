module app.freerouting {
  requires java.desktop;
  requires java.logging;
  requires org.apache.logging.log4j;
  requires com.google.gson;
  opens app.freerouting.gui to com.google.gson;
  opens app.freerouting.autoroute to com.google.gson;
}
