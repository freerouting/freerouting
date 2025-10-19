package app.freerouting.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for plane/copper pour routing settings
 */
public class RouterSettingsPlaneTest
{
  @Test
  void test_default_plane_routing_settings()
  {
    RouterSettings settings = new RouterSettings(4);
    
    // Verify default values for plane routing
    assertTrue(settings.route_to_planes_enabled, "Route to planes should be enabled by default");
    assertEquals(5, settings.scoring.plane_via_costs, "Default plane via costs should be 5");
    assertEquals(10.0, settings.scoring.planeViaDistancePenalty, "Default plane via distance penalty should be 10.0");
    assertEquals(50.0, settings.scoring.planeSharedViaPenalty, "Default plane shared via penalty should be 50.0");
  }
  
  @Test
  void test_plane_routing_settings_cloning()
  {
    RouterSettings original = new RouterSettings(4);
    
    // Modify plane routing settings
    original.route_to_planes_enabled = false;
    original.scoring.plane_via_costs = 10;
    original.scoring.planeViaDistancePenalty = 20.0;
    original.scoring.planeSharedViaPenalty = 100.0;
    
    // Clone the settings
    RouterSettings cloned = original.clone();
    
    // Verify cloned settings match original
    assertEquals(original.route_to_planes_enabled, cloned.route_to_planes_enabled, 
        "route_to_planes_enabled should be cloned correctly");
    assertEquals(original.scoring.plane_via_costs, cloned.scoring.plane_via_costs,
        "plane_via_costs should be cloned correctly");
    assertEquals(original.scoring.planeViaDistancePenalty, cloned.scoring.planeViaDistancePenalty,
        "planeViaDistancePenalty should be cloned correctly");
    assertEquals(original.scoring.planeSharedViaPenalty, cloned.scoring.planeSharedViaPenalty,
        "planeSharedViaPenalty should be cloned correctly");
  }
  
  @Test
  void test_plane_via_costs_getter_setter()
  {
    RouterSettings settings = new RouterSettings(4);
    
    // Test plane via costs setter
    settings.set_plane_via_costs(15);
    assertEquals(15, settings.get_plane_via_costs(), "Plane via costs should be set to 15");
    
    // Test minimum value enforcement (should be at least 1)
    settings.set_plane_via_costs(0);
    assertEquals(1, settings.get_plane_via_costs(), "Plane via costs should not be less than 1");
    
    settings.set_plane_via_costs(-5);
    assertEquals(1, settings.get_plane_via_costs(), "Plane via costs should not be less than 1");
  }
}
