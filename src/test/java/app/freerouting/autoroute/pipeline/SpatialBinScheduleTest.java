package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.geometry.planar.IntBox;
import org.junit.jupiter.api.Test;

class SpatialBinScheduleTest {

  @Test
  void touchingHalosShareOneBinAndKeepItemOrder() {
    IntBox[] halos = {
      new IntBox(0, 0, 10, 10), new IntBox(100, 0, 110, 10), new IntBox(10, 0, 20, 10)
    };
    SpatialBinSchedule schedule = SpatialBinSchedule.assign(halos);
    assertEquals(schedule.componentOf[0], schedule.componentOf[2]);
    assertFalse(schedule.componentOf[0] == schedule.componentOf[1]);
    assertEquals(2, schedule.components.size());
    assertEquals(0, schedule.components.get(0)[0]);
    assertEquals(2, schedule.components.get(0)[1]);
    assertEquals(1, schedule.components.get(1)[0]);
  }

  @Test
  void nullHaloStaysASerialBarrier() {
    IntBox[] halos = {new IntBox(0, 0, 10, 10), null, new IntBox(100, 0, 110, 10)};
    SpatialBinSchedule schedule = SpatialBinSchedule.assign(halos);
    assertEquals(3, schedule.components.size());
    assertEquals(1, schedule.components.get(1).length);
  }

  @Test
  void copperMustStayInsideTheAirlineBox() {
    IntBox airline = new IntBox(0, 0, 100, 100);
    assertTrue(SpatialBinSchedule.covers(airline, new IntBox(0, 0, 100, 100)));
    assertFalse(SpatialBinSchedule.covers(airline, new IntBox(0, 0, 101, 10)));
    assertFalse(SpatialBinSchedule.covers(null, airline));
  }
}
