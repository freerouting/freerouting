package app.freerouting.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PowerGroundNetsTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "GND",
        "GND1",
        "GND2",
        "GND3",
        "GND_EARTH",
        "GND_POWER",
        "PWR_GND",
        "GNDPWR",
        "GNDREF",
        "GNDS",
        "GNDA",
        "GNDD",
        "AGND",
        "DGND",
        "PGND",
        "SGND",
        "VSS",
        "VSSA",
        "VSSD",
        "VSSP",
        "0V",
        "V-0",
        "V-GND",
        "V-PE",
        "PE",
        "Earth",
        "Earth_Clean",
        "Earth_Protective",
        "CHASSIS",
        "/MCU/GND",
        "/Sheet1/AGND",
        "/POWER/VSS"
      })
  @DisplayName("isGroundNetName correctly classifies ground and return nets")
  void testGroundNetRecognition(String netName) {
    assertTrue(PowerGroundNets.isGroundNetName(netName), "Expected ground net: " + netName);
    assertFalse(
        PowerGroundNets.isPowerNetName(netName), "Ground net should not be power: " + netName);
    assertTrue(
        PowerGroundNets.isSupplyOrGroundNet(netName),
        "Ground net should be supply-or-ground: " + netName);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "+1V0",
        "+1V8",
        "1V8",
        "+2V8",
        "+2v8",
        "2V8",
        "2v8",
        "+2.8V",
        "+2.8v",
        "2.8V",
        "+3V3",
        "3V3",
        "+3.3V",
        "3.3V",
        "+5V",
        "5V",
        "5V0",
        "+12V",
        "12V",
        "+24V",
        "+48V",
        "-5V",
        "-12V",
        "-15V",
        "-2V5",
        "-3V3",
        "+1V35",
        "+5C",
        "+5F",
        "+5P",
        "+5VD",
        "+5VL",
        "+5VP",
        "+7.5V",
        "+9VA",
        "+3.3VA",
        "+3.3VADC",
        "+3.3VDAC",
        "+3.3VP",
        "VCC",
        "+VCC",
        "-VCC",
        "AVCC",
        "DVCC",
        "PVCC",
        "VCC_3V3",
        "VCC3V3",
        "VCCQ",
        "VCCIO",
        "VDD",
        "+VDD",
        "-VDD",
        "AVDD",
        "DVDD",
        "PVDD",
        "VDDA",
        "VDDF",
        "VDD_CORE",
        "VDDIO",
        "VEE",
        "VAA",
        "VPP",
        "VMEM",
        "VCOM",
        "VREF",
        "VDC",
        "VAC",
        "VBUS",
        "VBAT",
        "BAT+",
        "BAT-",
        "BATT+",
        "BATT-",
        "BATTERY",
        "V+",
        "V-",
        "VS+",
        "VS-",
        "V++",
        "V--",
        "VIN",
        "VOUT",
        "VIN_12V",
        "VOUT_5V",
        "PWR",
        "POWER",
        "PWR_MAIN",
        "POWER_IN",
        "CORE",
        "CORE_1V2",
        "VDRIVE",
        "HT",
        "/MCU/+3V3",
        "/POWER/VDD"
      })
  @DisplayName("isPowerNetName correctly classifies DC supply rail nets")
  void testPowerNetRecognition(String netName) {
    assertTrue(PowerGroundNets.isPowerNetName(netName), "Expected power net: " + netName);
    assertFalse(
        PowerGroundNets.isGroundNetName(netName), "Power net should not be ground: " + netName);
    assertTrue(
        PowerGroundNets.isSupplyOrGroundNet(netName),
        "Power net should be supply-or-ground: " + netName);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "RESET",
        "ENABLE",
        "SPI_MISO",
        "SPI_MOSI",
        "I2C_SDA",
        "I2C_SCL",
        "CLK",
        "CLK_P",
        "CLK_N",
        "DATA0",
        "USB_D+",
        "USB_D-",
        "STEP",
        "BUTTON",
        "OPEN",
        "BACKGROUND",
        "PWR_FLAG",
        "+VSW",
        "-VSW",
        "LINE",
        "NEUT",
        "PRI_HI",
        "PRI_LO",
        "PRI_MID",
        // Control, status, enable, and sense signal suffixes
        "VBUS_DET",
        "VDD_PG",
        "VCC_GOOD",
        "3V3_EN",
        "VBAT_SENSE",
        "VIN_UVLO",
        "VCC_FB",
        "VREG_FAULT",
        "PWR_OK",
        // Non-power prefixes containing rail names
        "LED_VCC",
        "PG_3V3",
        "EN_1V8",
        "ADC_VREF",
        // 2-letter token false-positive guards
        "DRV_VS_A",
        "ADC_VD_IN",
        "SENS_HT_1",
        // Unprefixed pin/connector designators
        "1P",
        "2P",
        "1C",
        "2C",
        // Auto-generated EDA net names
        "Net-(U7-VI)",
        "Net-(R1-Pad2)"
      })
  @DisplayName("Non-power and non-ground nets are rejected by both classifiers")
  void testNonPowerNonGroundNets(String netName) {
    assertFalse(PowerGroundNets.isPowerNetName(netName), "Should not be power: " + netName);
    assertFalse(PowerGroundNets.isGroundNetName(netName), "Should not be ground: " + netName);
    assertFalse(
        PowerGroundNets.isSupplyOrGroundNet(netName), "Should not be supply-or-ground: " + netName);
  }

  @Test
  @DisplayName("Null and blank nets return false safely")
  void testNullAndBlank() {
    assertFalse(PowerGroundNets.isGroundNetName(null));
    assertFalse(PowerGroundNets.isGroundNetName(""));
    assertFalse(PowerGroundNets.isGroundNetName("   "));

    assertFalse(PowerGroundNets.isPowerNetName(null));
    assertFalse(PowerGroundNets.isPowerNetName(""));
    assertFalse(PowerGroundNets.isPowerNetName("   "));

    assertFalse(PowerGroundNets.isSupplyOrGroundNet(null));
    assertFalse(PowerGroundNets.isSupplyOrGroundNet(""));
    assertFalse(PowerGroundNets.isSupplyOrGroundNet("   "));
  }

  @Test
  @DisplayName("User configured additional power and ground nets are respected")
  void testCustomConfiguredNets() {
    String customPower = "MY_SPECIAL_RAIL";
    String customGround = "ANODE_RETURN";

    assertFalse(PowerGroundNets.isPowerNetName(customPower));
    assertFalse(PowerGroundNets.isGroundNetName(customGround));

    assertTrue(PowerGroundNets.isPowerNetName(customPower, List.of(customPower)));
    assertTrue(PowerGroundNets.isPowerNetName("/MCU/" + customPower, List.of(customPower)));
    assertTrue(PowerGroundNets.isSupplyOrGroundNet(customPower, List.of(customPower), null));

    assertTrue(PowerGroundNets.isGroundNetName(customGround, List.of(customGround)));
    assertTrue(PowerGroundNets.isGroundNetName("/POWER/" + customGround, List.of(customGround)));
    assertTrue(PowerGroundNets.isSupplyOrGroundNet(customGround, null, List.of(customGround)));
  }
}
