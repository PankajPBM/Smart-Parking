// ICallback.aidl
package woyou.aidlservice.jiuiv5;

/**
 * SUNMI Printer Callback Interface (Official AIDL)
 *
 * Callback interface for asynchronous printer operations
 */
interface ICallback {

    /**
     * Callback for successful operations
     */
    void onRunResult(boolean isSuccess);

    /**
     * Callback with return data
     */
    void onReturnString(String result);

    /**
     * Callback for operations that raise exceptions
     */
    void onRaiseException(int code, String msg);

    /**
     * Callback for print complete
     */
    void onPrintResult(int code, String msg);
}
