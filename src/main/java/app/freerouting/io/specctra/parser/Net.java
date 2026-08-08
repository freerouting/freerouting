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
  public Net(Id pNetId) {
    id = pNetId;
  }

  public static void writeScope(
      WriteScopeParameter pPar,
      app.freerouting.rules.Net pNet,
      Collection<app.freerouting.board.Pin> pPinList)
      throws IOException {
    pPar.file.startScope();
    writeNetId(pNet, pPar.file, pPar.identifierType);
    // write the pins scope
    pPar.file.startScope();
    pPar.file.write("pins");
    for (app.freerouting.board.Pin currPin : pPinList) {
      if (currPin.containsNet(pNet.netNumber)) {
        writePin(pPar, currPin);
      }
    }
    pPar.file.endScope();
    pPar.file.endScope();
  }

  public static void writeNetId(
      app.freerouting.rules.Net pNet, IndentFileWriter pFile, IdentifierType pIdentifierType)
      throws IOException {
    pFile.write("net ");
    pIdentifierType.write(pNet.name, pFile);
    pFile.write(" ");
    int subnetNumber = pNet.subnetNumber;
    pFile.write(String.valueOf(subnetNumber));
  }

  public static void writePin(WriteScopeParameter pPar, app.freerouting.board.Pin pPin)
      throws IOException {
    Component currComponent = pPar.board.components.get(pPin.getComponentNo());
    if (currComponent == null) {
      FRLogger.warn("Net.write_scope: component not found at '" + currComponent.name + "'");
      return;
    }
    Package.Pin libPin = currComponent.getPackage().getPin(pPin.getIndexInPackage());
    if (libPin == null) {
      FRLogger.warn("Net.write_scope:  pin number out of range at '" + currComponent.name + "'");
      return;
    }
    pPar.file.newLine();
    pPar.identifierType.write(currComponent.name, pPar.file);
    pPar.file.write("-");
    pPar.identifierType.write(libPin.name, pPar.file);
  }

  public Set<Pin> getPins() {
    return pinList;
  }

  public void setPins(Collection<Pin> pPinList) {
    pinList = new TreeSet<>(pPinList);
  }

  public static class Id implements Comparable<Id> {

    public final String name;
    public final int subnetNumber;

    public Id(String pName, int pSubnetNumber) {
      name = pName;
      subnetNumber = pSubnetNumber;
    }

    @Override
    public int compareTo(Id pOther) {
      int result = this.name.compareTo(pOther.name);
      if (result == 0) {
        result = this.subnetNumber - pOther.subnetNumber;
      }
      return result;
    }
  }

  /** Sorted tuple of component name and pin name. */
  public static class Pin implements Comparable<Pin> {

    public final String componentName;
    public final String pinName;

    public Pin(String pComponentName, String pPinName) {
      componentName = pComponentName;
      pinName = pPinName;
    }

    @Override
    public int compareTo(Pin pOther) {
      int result = this.componentName.compareTo(pOther.componentName);
      if (result == 0) {
        result = this.pinName.compareTo(pOther.pinName);
      }
      return result;
    }

    @Override
    public String toString() {
      return "Pin{" + componentName + '-' + pinName + '}';
    }
  }
}
