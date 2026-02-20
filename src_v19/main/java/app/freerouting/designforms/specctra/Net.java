package app.freerouting.designforms.specctra;

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
  private Set<Pin> pin_list;

  /** Creates a new instance of Net */
  public Net(Id p_net_id) {
    id = p_net_id;
  }

  public static void write_scope(
      WriteScopeParameter p_par,
      app.freerouting.rules.Net p_net,
      Collection<app.freerouting.board.Pin> p_pin_list)
      throws IOException {
    p_par.file.start_scope();
    write_net_id(p_net, p_par.file, p_par.identifier_type);
    // write the pins scope
    p_par.file.start_scope();
    p_par.file.write("pins");
    for (app.freerouting.board.Pin curr_pin : p_pin_list) {
      if (curr_pin.contains_net(p_net.net_number)) {
        write_pin(p_par, curr_pin);
      }
    }
    p_par.file.end_scope();
    p_par.file.end_scope();
  }

  public static void write_net_id(
      app.freerouting.rules.Net p_net, IndentFileWriter p_file, IdentifierType p_identifier_type)
      throws IOException {
    p_file.write("net ");
    p_identifier_type.write(p_net.name, p_file);
    p_file.write(" ");
    int subnet_number = p_net.subnet_number;
    p_file.write(String.valueOf(subnet_number));
  }

  public static void write_pin(WriteScopeParameter p_par, app.freerouting.board.Pin p_pin)
      throws IOException {
    app.freerouting.board.Component curr_component =
        p_par.board.components.get(p_pin.get_component_no());
    if (curr_component == null) {
      FRLogger.warn("Net.write_scope: component not found at '" + curr_component.name + "'");
      return;
    }
    app.freerouting.library.Package.Pin lib_pin =
        curr_component.get_package().get_pin(p_pin.get_index_in_package());
    if (lib_pin == null) {
      FRLogger.warn("Net.write_scope:  pin number out of range at '" + curr_component.name + "'");
      return;
    }
    p_par.file.new_line();
    p_par.identifier_type.write(curr_component.name, p_par.file);
    p_par.file.write("-");
    p_par.identifier_type.write(lib_pin.name, p_par.file);
  }

  public Set<Pin> get_pins() {
    return pin_list;
  }

  public void set_pins(Collection<Pin> p_pin_list) {
    pin_list = new TreeSet<>(p_pin_list);
  }

  public static class Id implements Comparable<Id> {
    public final String name;
    public final int subnet_number;

    public Id(String p_name, int p_subnet_number) {
      name = p_name;
      subnet_number = p_subnet_number;
    }

    @Override
    public int compareTo(Id p_other) {
      int result = this.name.compareTo(p_other.name);
      if (result == 0) {
        result = this.subnet_number - p_other.subnet_number;
      }
      return result;
    }
  }

  /** Sorted tuple of component name and pin name. */
  public static class Pin implements Comparable<Pin> {
    public final String component_name;
    public final String pin_name;

    public Pin(String p_component_name, String p_pin_name) {
      component_name = p_component_name;
      pin_name = p_pin_name;
    }

    @Override
    public int compareTo(Pin p_other) {
      int result = this.component_name.compareTo(p_other.component_name);
      if (result == 0) {
        result = this.pin_name.compareTo(p_other.pin_name);
      }
      return result;
    }

    @Override
    public String toString() {
      return "Pin{" + component_name + '-' + pin_name + '}';
    }
  }
}
