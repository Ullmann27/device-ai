package at.heinz.deviceai;
/** Host-only stand-in for the AIDL-generated interface (compile/test harness, not shipped). */
public interface IDeviceShell extends android.os.IInterface {
  String execute(String command) throws android.os.RemoteException;
  void destroy() throws android.os.RemoteException;
  abstract class Stub extends android.os.Binder implements IDeviceShell {
    public Stub() {}
    public static IDeviceShell asInterface(android.os.IBinder obj) { return null; }
    @Override public android.os.IBinder asBinder() { return this; }
  }
}
