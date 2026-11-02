package at.heinz.deviceai;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * ARM64 Termux build-only transport for the unchanged IDeviceShell.aidl protocol.
 *
 * The regular Android build continues to generate this interface from AIDL.
 * This file is compiled only with -PdeviceAiMobileAidlFallback=true.
 * Transaction IDs and the descriptor must match src/main/aidl/.../IDeviceShell.aidl.
 */
public interface IDeviceShell extends IInterface {
    String execute(String command) throws RemoteException;

    void destroy() throws RemoteException;

    abstract class Stub extends Binder implements IDeviceShell {
        public static final String DESCRIPTOR = "at.heinz.deviceai.IDeviceShell";
        public static final int TRANSACTION_execute = IBinder.FIRST_CALL_TRANSACTION;
        public static final int TRANSACTION_destroy = 16777114;

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static IDeviceShell asInterface(IBinder binder) {
            if (binder == null) {
                return null;
            }
            IInterface local = binder.queryLocalInterface(DESCRIPTOR);
            if (local instanceof IDeviceShell) {
                return (IDeviceShell) local;
            }
            return new Proxy(binder);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == IBinder.INTERFACE_TRANSACTION) {
                if (reply != null) {
                    reply.writeString(DESCRIPTOR);
                }
                return true;
            }
            switch (code) {
                case TRANSACTION_execute: {
                    data.enforceInterface(DESCRIPTOR);
                    final String command = data.readString();
                    final String result = execute(command);
                    if (reply != null) {
                        reply.writeNoException();
                        reply.writeString(result);
                    }
                    return true;
                }
                case TRANSACTION_destroy: {
                    data.enforceInterface(DESCRIPTOR);
                    destroy();
                    if (reply != null) {
                        reply.writeNoException();
                    }
                    return true;
                }
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        }

        private static final class Proxy implements IDeviceShell {
            private final IBinder remote;

            Proxy(IBinder remote) {
                this.remote = remote;
            }

            @Override
            public IBinder asBinder() {
                return remote;
            }

            @Override
            public String execute(String command) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeString(command);
                    if (!remote.transact(TRANSACTION_execute, data, reply, 0)) {
                        throw new RemoteException("IDeviceShell.execute was not handled");
                    }
                    reply.readException();
                    return reply.readString();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public void destroy() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (!remote.transact(TRANSACTION_destroy, data, reply, 0)) {
                        throw new RemoteException("IDeviceShell.destroy was not handled");
                    }
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
