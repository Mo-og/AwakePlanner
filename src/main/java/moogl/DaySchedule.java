package moogl;

import java.time.LocalTime;

public record DaySchedule(boolean enabled, LocalTime start, LocalTime end) {

  public boolean isValid() {
    return start.isBefore(end);
  }

  public boolean contains(LocalTime time) {
    return enabled && isValid() && !time.isBefore(start) && time.isBefore(end);
  }
}
