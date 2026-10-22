package app.dialtone.phone.vvm.compat;

/** JobScheduler ids used by the voicemail engine. Keep unique within the app. */
public final class ScheduledJobIds {
  private ScheduledJobIds() {}

  public static final int VVM_TASK_SCHEDULER_JOB = 50;
  public static final int VVM_STATUS_CHECK_JOB = 51;
  public static final int VVM_DEVICE_PROVISIONED_JOB = 52;
}
