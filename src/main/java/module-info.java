module app.freerouting {
  requires java.desktop;
  requires java.logging;
  requires java.net.http;
  requires com.google.gson;
  requires org.apache.logging.log4j;
  requires org.apache.logging.log4j.core;
  requires org.apache.logging.log4j.to.slf4j;
  requires spring.web;
  requires spring.context;
  requires spring.boot;
  requires spring.beans;
  requires org.springdoc.openapi.common;
  requires spring.boot.autoconfigure;
  requires org.apache.commons.lang3;
  requires io.swagger.v3.oas.models;

  opens app.freerouting.gui to com.google.gson;
  opens app.freerouting.autoroute to com.google.gson;
  opens app.freerouting.board to com.google.gson;
  opens app.freerouting.management.segment to com.google.gson;
  opens app.freerouting.management to com.google.gson;
  opens app.freerouting.settings to com.google.gson;

  // Open the package for deep reflection at runtime
  opens app.freerouting to spring.core, spring.beans, spring.context;
  opens app.freerouting.api to spring.core, spring.beans, spring.context;
  opens app.freerouting.api.v1 to spring.core, spring.beans, spring.context;

  // Export the app.freerouting package to the spring.beans module
  exports app.freerouting to spring.beans;

  // If you have other packages that need to be accessed by Spring modules, export them as well
  exports app.freerouting.api to spring.beans, spring.context, spring.web;
  exports app.freerouting.api.v1 to spring.beans, spring.context, spring.web;
}