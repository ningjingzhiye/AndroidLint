package com.paincker.lint.core;

import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by jzj on 2017/7/4.
 */
public class LogUtils {

    private static PrintWriter sPrintWriter;

    public static void d(String msg) {
        System.out.println("[custom-lint] " + msg);
        if (sPrintWriter != null) {
            sPrintWriter.println(msg);
            sPrintWriter.flush();
        }
    }

    public static void setLogFile(File logFile) {
        if (logFile == null) {
            IOUtils.closeQuietly(sPrintWriter);
            sPrintWriter = null;
            return;
        }
        try {
            sPrintWriter = new PrintWriter(new FileWriter(logFile, true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
