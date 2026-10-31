package app.freerouting.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.gui.a11y.GuiA11yHarness;
import app.freerouting.gui.a11y.GuiLocators;
import app.freerouting.gui.windows.board.DonorService;
import app.freerouting.gui.windows.board.WindowSponsor;
import java.awt.Component;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.accessibility.AccessibleRole;
import javax.swing.JButton;
import javax.swing.JComponent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Accessibility and component behavior test for WindowSponsor. */
@Tag("gui")
class WindowSponsorA11yTest {

  @Test
  void englishSponsorComponentIsAccessible() {
    AtomicBoolean donateClicked = new AtomicBoolean(false);
    DonorService.DonorPayload testPayload =
        new DonorService.DonorPayload(
            "2026-10-26",
            List.of(
                new DonorService.DonorTier(
                    "Gold Sponsors",
                    List.of(new DonorService.Donor("Test Sponsor", "https://example.com")))));

    DonorService testService =
        new DonorService() {
          @Override
          public CompletableFuture<DonorPayload> fetchDonorsAsync() {
            return CompletableFuture.completedFuture(testPayload);
          }
        };

    JComponent comp =
        GuiA11yHarness.onEdt(
            () ->
                WindowSponsor.createComponentOnly(
                    Locale.ENGLISH, testService, () -> donateClicked.set(true)));

    GuiA11yHarness.onEdt(
        () -> {
          Component root = GuiA11yHarness.findByLocator(comp, GuiLocators.WINDOW_SPONSOR);
          assertNotNull(root, "Root WindowSponsor panel must be locatable");
          assertEquals("Sponsor Freerouting", GuiA11yHarness.accessibleName(root));

          Component donateBtn =
              GuiA11yHarness.findByLocator(comp, GuiLocators.WINDOW_SPONSOR_DONATE_BUTTON);
          assertNotNull(donateBtn, "Donate button must be locatable");
          GuiA11yHarness.requireRole(
              donateBtn, GuiLocators.WINDOW_SPONSOR_DONATE_BUTTON, AccessibleRole.PUSH_BUTTON);

          Component donorsList =
              GuiA11yHarness.findByLocator(comp, GuiLocators.WINDOW_SPONSOR_DONORS_LIST);
          assertNotNull(donorsList, "Donors list container must be locatable");

          ((JButton) donateBtn).doClick();
          assertTrue(donateClicked.get(), "Clicking donate button must trigger listener");

          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }

  @Test
  void hungarianSponsorComponentHasLocalizedAccessibleName() {
    DonorService testService = new DonorService();

    JComponent comp =
        GuiA11yHarness.onEdt(
            () -> WindowSponsor.createComponentOnly(Locale.of("hu"), testService, () -> {}));

    GuiA11yHarness.onEdt(
        () -> {
          Component root = GuiA11yHarness.findByLocator(comp, GuiLocators.WINDOW_SPONSOR);
          assertNotNull(root);
          assertEquals("Freerouting támogatása", GuiA11yHarness.accessibleName(root));
          GuiA11yHarness.requireNoLeakedGuiResources();
        });
  }
}
