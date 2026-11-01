// IWoyouService.aidl
package woyou.aidlservice.jiuiv5;

import woyou.aidlservice.jiuiv5.ICallback;

/**
 * SUNMI Printer Service Interface (Official AIDL)
 *
 * This is the official SUNMI printer service interface used across
 * SUNMI POS devices. Reference: SUNMI Developer Documentation
 * https://docs.sunmi.com/
 */
interface IWoyouService {

    /**
     * Printer initialization
     */
    void printerInit(in ICallback callback);

    /**
     * Printer self-check and status
     */
    void printerSelfChecking(in ICallback callback);

    /**
     * Get printer serial number
     */
    String getPrinterSerialNo();

    /**
     * Get printer version
     */
    String getPrinterVersion();

    /**
     * Get printer modal (model name)
     */
    String getPrinterModal();

    /**
     * Get print head printing length
     */
    int getPrintedLength();

    /**
     * Line feed (n lines)
     */
    void lineWrap(int n, in ICallback callback);

    /**
     * Print text
     */
    void printText(String text, in ICallback callback);

    /**
     * Print text with specified font
     */
    void printTextWithFont(String text, String typeface, float fontsize, in ICallback callback);

    /**
     * Print a row of the table in the specified format
     */
    void printColumnsText(in String[] colsTextArr, in int[] colsWidthArr, in int[] colsAlign, in ICallback callback);

    /**
     * Print vector image
     */
    void printBitmap(in Bitmap bitmap, in ICallback callback);

    /**
     * Print 1D/2D barcode
     */
    void printBarCode(String data, int symbology, int height, int width, int textposition, in ICallback callback);

    /**
     * Print QR code
     */
    void printQRCode(String data, int modulesize, int errorlevel, in ICallback callback);

    /**
     * Print original byte data
     */
    void sendRAWData(in byte[] data, in ICallback callback);

    /**
     * Set alignment mode
     * 0-left, 1-center, 2-right
     */
    void setAlignment(int alignment, in ICallback callback);

    /**
     * Set font size
     */
    void setFontSize(float fontsize, in ICallback callback);

    /**
     * Set font weight (bold)
     */
    void setFontName(String typeface, in ICallback callback);

    /**
     * Open cash drawer (if available)
     */
    void openDrawer(in ICallback callback);

    /**
     * Get current density of the printer
     */
    int getPrinterDensity();

    /**
     * Query printing status
     * Return: 1-Printer is busy, 2-Out of paper, 3-Overheated, 4-Cover open
     */
    int updatePrinterState();

    /**
     * Cut paper (full or partial)
     */
    void cutPaper(in ICallback callback);

    /**
     * Get cutter cutting times
     */
    int getCutPaperTimes();

    /**
     * Print and feed paper (print buffer and feed n lines)
     */
    void printAndFeedPaper(int n, in ICallback callback);

    /**
     * Enter transaction mode (buffered printing)
     */
    void enterPrinterBuffer(in boolean clean);

    /**
     * Exit transaction mode and print buffer
     */
    void exitPrinterBuffer(in boolean commit);

    /**
     * Get printer firmware version
     */
    String getServiceVersion();
}
