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

  opens app.freerouting.api to jakarta.ws.rs;
  opens app.freerouting.api.v1 to jakarta.ws.rs;
  opens app.freerouting.api.v2 to jakarta.ws.rs;

  exports app.freerouting.api.v1 to org.glassfish.hk2.locator, jersey.server;
  exports app.freerouting.api.v2 to org.glassfish.hk2.locator, jersey.server;

  requires jakarta.ws.rs;
  requires jakarta.inject;
  requires jakarta.annotation;
  requires jakarta.activation;
  requires jakarta.json.bind;
  requires jakarta.validation;

  requires org.eclipse.jetty.servlet;

  requires jersey.container.servlet;
  requires jersey.container.servlet.core;
  requires jersey.server;
  requires org.glassfish.jaxb.core;

  requires io.swagger.v3.jaxrs2;
  requires io.swagger.v3.oas.integration;
  requires io.swagger.v3.oas.models;
}