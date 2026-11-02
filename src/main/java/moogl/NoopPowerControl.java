package moogl;

public final class NoopPowerControl implements PowerControl {

  @Override
  public void keepAwake() {}

  @Override
  public void release() {}
}
