package at.heinz.deviceai;

interface IDeviceShell {
    String execute(String command) = 1;
    void destroy() = 16777114;
}
