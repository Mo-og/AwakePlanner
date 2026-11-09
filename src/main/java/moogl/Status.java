package moogl;

import java.time.LocalDateTime;
import java.util.Optional;

public record Status(boolean active, String description, Optional<LocalDateTime> until) {

  public boolean adjustable() {
    return !active || until.isPresent();
  }
}
