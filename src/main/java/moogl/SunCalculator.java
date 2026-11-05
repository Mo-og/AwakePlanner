package moogl;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
import static java.lang.Math.toRadians;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

public final class SunCalculator {

  private static final double COS_ZENITH = cos(toRadians(90.833));
  private static final double MAX_LATITUDE = 89.99;

  private SunCalculator() {}

  public static SunTimes compute(LocalDate date, double latitude, double longitude, ZoneId zone) {
    double g = 2 * PI / date.lengthOfYear() * (date.getDayOfYear() - 1);
    double eqTime =
        229.18
            * (0.000075
                + 0.001868 * cos(g)
                - 0.032077 * sin(g)
                - 0.014615 * cos(2 * g)
                - 0.040849 * sin(2 * g));
    double declination =
        0.006918
            - 0.399912 * cos(g)
            + 0.070257 * sin(g)
            - 0.006758 * cos(2 * g)
            + 0.000907 * sin(2 * g)
            - 0.002697 * cos(3 * g)
            + 0.00148 * sin(3 * g);
    double lat = toRadians(Math.clamp(latitude, -MAX_LATITUDE, MAX_LATITUDE));
    double cosHourAngle = COS_ZENITH / (cos(lat) * cos(declination)) - tan(lat) * tan(declination);
    if (cosHourAngle > 1) {
      return SunTimes.Polar.NIGHT;
    }
    if (cosHourAngle < -1) {
      return SunTimes.Polar.DAY;
    }
    double hourAngle = Math.toDegrees(Math.acos(cosHourAngle));
    return new SunTimes.Regular(
        atUtcMinutes(date, 720 - 4 * (longitude + hourAngle) - eqTime, zone),
        atUtcMinutes(date, 720 - 4 * (longitude - hourAngle) - eqTime, zone));
  }

  private static ZonedDateTime atUtcMinutes(LocalDate date, double minutes, ZoneId zone) {
    return date.atStartOfDay(ZoneOffset.UTC)
        .plusSeconds(Math.round(minutes * 60))
        .withZoneSameInstant(zone);
  }
}
