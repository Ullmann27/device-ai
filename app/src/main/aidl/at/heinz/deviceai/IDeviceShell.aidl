package at.heinz.deviceai;

interface IDeviceShell {
    void destroy() = 16777114;
    String execute(String command);
}
