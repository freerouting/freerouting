package app.freerouting.gui;

import app.freerouting.management.TextManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import java.awt.*;
import java.awt.Cursor;
import java.awt.event.*;
import java.util.Map;
import javax.swing.border.AbstractBorder;

public class SegmentedButtons extends JPanel {
  private ButtonGroup buttonGroup;
  private Map<JToggleButton, String> buttonValues;
  private String selectedValue;
  private Color textColor = new Color(0, 0, 0); // Text color
  private Color selectedTextColor = new Color(225, 225, 225); // Text color for selected button
  private Color selectedColor = new Color(30, 30, 30); // Background color for selected button
  private Color hoverColor = new Color(220, 220, 220); // Hover color
  private Color selectedAndHoverColor = new Color(50, 50, 50); // Selected and hover color
  private Color borderColor = new Color(121, 116, 126); // Border color around the buttons
  private int borderWidth = 1; // Border width around the buttons

  private List<Consumer<String>> valueChangedEventListeners = new ArrayList<>();

  public SegmentedButtons(TextManager tm, String heading, String... values) {
    setLayout(new BorderLayout());

    // Set an empty border as a margin around the component
    setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

    // Put the heading above the buttons, centered horizontally
    JLabel headingLabel = new JLabel(heading, SwingConstants.CENTER); // Center the text in the label
    headingLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
    headingLabel.setForeground(textColor);
    add(headingLabel, BorderLayout.NORTH); // Add the label to the NORTH

    // Create a new panel for the buttons
    JPanel buttonPanel = new JPanel();
    buttonPanel.setBorder(BorderFactory.createLineBorder(borderColor, borderWidth));
    buttonPanel.setLayout(new GridBagLayout()); // Set layout of buttonPanel to BoxLayout
    add(buttonPanel, BorderLayout.CENTER); // Add the buttonPanel to the CENTER


    buttonGroup = new ButtonGroup();
    buttonValues = new LinkedHashMap<>();

    Map<String, String> valueTextMap = new LinkedHashMap<>();
    for (String value : values) {
      valueTextMap.put(value, tm.getText(value));
    }

    int maximumWidth = 0;
    for (Map.Entry<String, String> entry : valueTextMap.entrySet()) {
      JToggleButton button = createSegmentButton(entry.getValue(), entry.getKey());

      String tooltip = tm.getText(entry.getKey() + "_tooltip");
      button.setToolTipText(tooltip != null && !tooltip.isEmpty() ? tooltip : entry.getValue());

      buttonPanel.add(button);
      buttonGroup.add(button);
      buttonValues.put(button, entry.getKey());

      // Get the button width
      int buttonWidth = button.getPreferredSize().width;
      if (buttonWidth > maximumWidth) {
        maximumWidth = buttonWidth;
      }
    }

    add(buttonPanel);

    // Iterate over the buttons and update the border
    int buttonCount = buttonGroup.getButtonCount();
    int buttonIndex = 0;
    for (JToggleButton button : buttonValues.keySet()) {
      // Check if the button is the first or last in the group
      boolean isFirst = buttonIndex == 0;
      boolean isLast = buttonIndex == buttonCount - 1;

      // Set the width for all buttons to the maximum width
      button.setPreferredSize(new Dimension((int)(maximumWidth * 1.1), button.getPreferredSize().height));

      // Set the selected button
      if (isFirst)
      {
        this.setSelectedValue(buttonValues.get(button));
      }

      buttonIndex++;
    }
  }

  private JToggleButton createSegmentButton(String text, String value) {
    JToggleButton button = new JToggleButton(text) {
      @Override
      public void setSelected(boolean selected) {
        super.setSelected(selected);
        if (selected) {
          this.setFont(new Font("Dialog", Font.BOLD, 12));
          this.setForeground(selectedTextColor);
          this.setBackground(selectedColor);
          this.setOpaque(true);
        } else {
          this.setFont(new Font("Dialog", Font.PLAIN, 12));
          this.setForeground(textColor);
          this.setOpaque(false);
        }
      }
    };

    button.setFocusPainted(false);
    button.setContentAreaFilled(true);
    button.setOpaque(false);
    button.setFont(new Font("Dialog", Font.PLAIN, 12)); // Set font here if necessary
    button.setMargin(new Insets(0, 10, 0, 10)); // 10 pixels of padding on all sides

    // Disable the default borders
    button.setBorderPainted(false);

    // Hover effect
    button.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent e) {
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            if (button.isSelected()) {
              button.setForeground(selectedTextColor);
              button.setBackground(selectedAndHoverColor);
              button.setOpaque(true);
            } else {
              button.setForeground(textColor);
              button.setBackground(hoverColor);
              button.setOpaque(true);
            }
          }

          @Override
          public void mouseExited(MouseEvent e) {
            button.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            if (button.isSelected()) {
              button.setForeground(selectedTextColor);
              button.setBackground(selectedColor);
              button.setOpaque(true);
            } else {
              button.setForeground(textColor);
              button.setOpaque(false);
            }
          }
        });

    // Action listener for selection changes
    button.addActionListener(
        e -> {
          for (Map.Entry<JToggleButton, String> entry : buttonValues.entrySet()) {
            JToggleButton btn = entry.getKey();
            btn.setFont(new Font("Dialog", Font.PLAIN, 12)); // reset font for all
            btn.setForeground(textColor);
            btn.setOpaque(false);
          }

          selectedValue = buttonValues.get(button);
          button.setFont(new Font("Dialog", Font.BOLD, 12)); // Set bold font for selected
          button.setForeground(selectedTextColor);
          button.setBackground(selectedColor);
          button.setOpaque(true);

          this.valueChangedEventListeners.forEach(listener -> listener.accept(selectedValue));
        });

    return button;
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    for (JToggleButton button : buttonValues.keySet()) {
      button.setEnabled(enabled);
    }
  }

  public void addValueChangedEventListener(Consumer<String> listener) {
    valueChangedEventListeners.add(listener);
  }

  public String getSelectedValue() {
    return selectedValue;
  }

  public void setSelectedValue(String value) {
    for (Map.Entry<JToggleButton, String> entry : buttonValues.entrySet()) {
      JToggleButton button = entry.getKey();
      button.setSelected(entry.getValue().equals(value));
    }
  }
}