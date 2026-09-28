package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.freerouting.board.state.CoordinateTransform;
import app.freerouting.gui.windows.routing.WindowNetClasses;
import app.freerouting.rules.NetClass;
import org.junit.jupiter.api.Test;

class WindowNetClassesTest {

  @Test
  void parseTraceWidthValueHandlesVariousNumericAndStringInputs() {
    assertEquals(200.0, WindowNetClasses.parseTraceWidthValue(200));
    assertEquals(150.5, WindowNetClasses.parseTraceWidthValue(150.5f));
    assertEquals(300.25, WindowNetClasses.parseTraceWidthValue(300.25d));

    assertEquals(200.0, WindowNetClasses.parseTraceWidthValue("200.0000"));
    assertEquals(0.35, WindowNetClasses.parseTraceWidthValue("0.35"));
    assertEquals(0.35, WindowNetClasses.parseTraceWidthValue("0,35"));
    assertEquals(120.0, WindowNetClasses.parseTraceWidthValue("  120.0  "));

    assertNull(WindowNetClasses.parseTraceWidthValue("invalid"));
    assertNull(WindowNetClasses.parseTraceWidthValue(""));
    assertNull(WindowNetClasses.parseTraceWidthValue(null));
    assertNull(WindowNetClasses.parseTraceWidthValue(new Object()));
  }

  @Test
  void applyTraceWidthAppliesCorrectHalfWidthToNetClass() {
    NetClass netClass = mock(NetClass.class);
    CoordinateTransform ct = mock(CoordinateTransform.class);

    when(ct.userToBoard(100.0)).thenReturn(10000.0);

    boolean success = WindowNetClasses.applyTraceWidth(netClass, 200.0, ct);

    assertTrue(success);
    verify(ct).userToBoard(100.0);
    verify(netClass).setTraceHalfWidth(10000);
  }

  @Test
  void applyTraceWidthFailsOnInvalidOrNonPositiveInputs() {
    NetClass netClass = mock(NetClass.class);
    CoordinateTransform ct = mock(CoordinateTransform.class);

    assertFalse(WindowNetClasses.applyTraceWidth(netClass, 0.0, ct));
    assertFalse(WindowNetClasses.applyTraceWidth(netClass, -50.0, ct));
    assertFalse(WindowNetClasses.applyTraceWidth(netClass, "bad_input", ct));
    assertFalse(WindowNetClasses.applyTraceWidth(netClass, 100.0, null));

    verifyNoInteractions(netClass);
  }
}
