package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class GuiManagerEdtTest {

  @Test
  void dispatchesInitializerToEdtWhenCalledOffEdt() {
    AtomicBoolean ranOnEdt = new AtomicBoolean();

    boolean initialized =
        GuiManager.invokeOnEdt(
            () -> {
              ranOnEdt.set(SwingUtilities.isEventDispatchThread());
              return true;
            });

    assertTrue(initialized);
    assertTrue(ranOnEdt.get());
  }

  @Test
  void runsInitializerDirectlyWhenAlreadyOnEdt() throws Exception {
    AtomicBoolean ranOnEdt = new AtomicBoolean();
    AtomicBoolean initialized = new AtomicBoolean();

    SwingUtilities.invokeAndWait(
        () ->
            initialized.set(
                GuiManager.invokeOnEdt(
                    () -> {
                      ranOnEdt.set(SwingUtilities.isEventDispatchThread());
                      return true;
                    })));

    assertTrue(initialized.get());
    assertTrue(ranOnEdt.get());
  }
}
