package app.freerouting.io.specctra.parser;

import app.freerouting.board.Component;
import app.freerouting.core.Package;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.IndentFileWriter;
import app.freerouting.logger.FRLogger;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Class for reading and writing net scopes from dsn-files. */
public class Net {

  public final Id id;

  /** List of elements of type Pin. */
  private Set<Pin> pinList;

  /** Creates a new instance of Net */
  public Net(Id p_net_id) {
    id = p_net_id;
  }

  public static void writeScope(
      WriteScopeParameter p_par,
      app.freerouting.rules.Net p_net,
      Collection<app.freerouting.board.Pin> p_pin_list)
      throws IOException {
    p_par.file.startScope();
    writeNetId(p_net, p_par.file, p_par.identifierType);
    // write the pins scope
    p_par.file.startScope();
    p_par.file.write("pins");
    for (app.freerouting.board.Pin currPin : p_pin_list) {
      if (currPin.containsNet(p_net.netNumber)) {
        writePin(p_par, currPin);
      }
    }
    p_par.file.endScope();
    p_par.file.endScope();
  }

  public static void writeNetId(
      app.freerouting.rules.Net p_net, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.write("net ");
    p_identifier_type.write(p_net.name, p_file);
    p_file.write(" ");
    int subnetNumber = p_net.subnetNumber;
    p_file.write(String.valueOf(subnetNumber));
  }

  public static void writePin(WriteScopeParameter p_par, app.freerouting.board.Pin p_pin)
      throws IOException {
    Component currComponent = p_par.board.components.get(p_pin.getComponentNo());
    if (currComponent == null) {
      FRLogger.warn("Net.write_scope: component not found at '" + currComponent.name + "'");
      return;
    }
    Package.Pin libPin = currComponent.getPackage().getPin(p_pin.getIndexInPackage());
    if (libPin == null) {
      FRLogger.warn("Net.write_scope:  pin number out of range at '" + currComponent.name + "'");
      return;
    }
    p_par.file.newLine();
    p_par.identifierType.write(currComponent.name, p_par.file);
    p_par.file.write("-");
    p_par.identifierType.write(libPin.name, p_par.file);
  }

  public Set<Pin> getPins() {
    return pinList;
  }

  public void setPins(Collection<Pin> p_pin_list) {
    pinList = new TreeSet<>(p_pin_list);
  }

  public static class Id implements Comparable<Id> {

    public final String name;
    public final int subnetNumber;

    public Id(String p_name, int p_subnet_number) {
      name = p_name;
      subnetNumber = p_subnet_number;
    }

    @Override
    public int compareTo(Id p_other) {
      int result = this.name.compareTo(p_other.name);
      if (result == 0) {
        result = this.subnetNumber - p_other.subnetNumber;
      }
      return result;
    }
  }

  /** Sorted tuple of component name and pin name. */
  public static class Pin implements Comparable<Pin> {

    public final String componentName;
    public final String pinName;

    public Pin(String p_component_name, String p_pin_name) {
      componentName = p_component_name;
      pinName = p_pin_name;
    }

    @Override
    public int compareTo(Pin p_other) {
      int result = this.componentName.compareTo(p_other.componentName);
      if (result == 0) {
        result = this.pinName.compareTo(p_other.pinName);
      }
      return result;
    }

    @Override
    public String toString() {
      return "Pin{" + componentName + '-' + pinName + '}';
    }
  }
}
