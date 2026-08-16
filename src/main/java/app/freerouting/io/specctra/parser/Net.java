package app.freerouting.io.specctra.parser;

import app.freerouting.board.Component;
import app.freerouting.core.library.Package;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Class for reading and writing net scopes from dsn-files. */
@SuppressWarnings({"checkstyle:MissingJavadocMethod", "checkstyle:MissingJavadocType"})
public class Net {

  public final Id id;

  /** List of elements of type Pin. */
  private Set<Pin> pinList;

  /** Creates a new instance of Net. */
  public Net(Id netId) {
    id = netId;
  }

  public static void writeScope(
      WriteScopeParameter scopeParameter,
      app.freerouting.rules.Net net,
      Collection<app.freerouting.board.Pin> pinList)
      throws IOException {
    scopeParameter.file.startScope();
    writeNetId(net, scopeParameter.file, scopeParameter.identifierType);
    // write the pins scope
    scopeParameter.file.startScope();
    scopeParameter.file.write("pins");
    for (app.freerouting.board.Pin currentPin : pinList) {
      if (currentPin.containsNet(net.netNumber)) {
        writePin(scopeParameter, currentPin);
      }
    }
    scopeParameter.file.endScope();
    scopeParameter.file.endScope();
  }

  public static void writeNetId(
      app.freerouting.rules.Net net, IndentFileWriter file, IdentifierType identifierType)
      throws IOException {
    file.write("net ");
    identifierType.write(net.name, file);
    file.write(" ");
    int subnetNumber = net.subnetNumber;
    file.write(String.valueOf(subnetNumber));
  }

  public static void writePin(WriteScopeParameter scopeParameter, app.freerouting.board.Pin pin)
      throws IOException {
    Component currentComponent = scopeParameter.board.components.get(pin.getComponentId());
    if (currentComponent == null) {
      FRLogger.warn("Net.write_scope: component not found at '" + currentComponent.name + "'");
      return;
    }
    Package.Pin libPin = currentComponent.getPackage().getPin(pin.getPinIndex());
    if (libPin == null) {
      FRLogger.warn("Net.write_scope:  pin number out of range at '" + currentComponent.name + "'");
      return;
    }
    scopeParameter.file.newLine();
    scopeParameter.identifierType.write(currentComponent.name, scopeParameter.file);
    scopeParameter.file.write("-");
    scopeParameter.identifierType.write(libPin.name, scopeParameter.file);
  }

  public Set<Pin> getPins() {
    return pinList;
  }

  public void setPins(Collection<Pin> pinList) {
    this.pinList = new TreeSet<>(pinList);
  }

  public static class Id implements Comparable<Id> {

    public final String name;
    public final int subnetNumber;

    public Id(String name, int subnetNumber) {
      this.name = name;
      this.subnetNumber = subnetNumber;
    }

    @Override
    public int compareTo(Id other) {
      int result = this.name.compareTo(other.name);
      if (result == 0) {
        result = this.subnetNumber - other.subnetNumber;
      }
      return result;
    }
  }

  /** Sorted tuple of component name and pin name. */
  public static class Pin implements Comparable<Pin> {

    public final String componentName;
    public final String pinName;

    public Pin(String componentName, String pinName) {
      this.componentName = componentName;
      this.pinName = pinName;
    }

    @Override
    public int compareTo(Pin other) {
      int result = this.componentName.compareTo(other.componentName);
      if (result == 0) {
        result = this.pinName.compareTo(other.pinName);
      }
      return result;
    }

    @Override
    public String toString() {
      return "Pin{" + componentName + '-' + pinName + '}';
    }
  }
}
