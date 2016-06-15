// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License

/*
 *******************************************************************************
 * Copyright (C) 2012, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package com.ibm.icu.dev.tztools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public class SupplementalDataUpdater {
    private static final String MARKER = "@";
    private static final String INDENT = "    ";

    public static void main(String... args) {
        if (args.length != 3) {
            System.err.println("Bad arguments - requires 3 arguments: <ver> <template file> <output file>");
            return;
        }
        String ver = args[0];
        String templateFilePath = args[1];
        String outFilePath = args[2];

        if (!ver.equals("38") && !ver.equals("40") && !ver.equals("42")) {
            System.err.println("Bad version argument - " + ver);
        }
        File templateFile = new File(templateFilePath);
        File outFile = new File(outFilePath);

        BufferedReader templateReader = null;
        try {
            templateReader = new BufferedReader(new InputStreamReader(new FileInputStream(templateFile), "UTF-8"));
        } catch (IOException e) {
            System.err.println("Cannot open " + templateFilePath + " to read");
        }

        PrintWriter outPrintWriter = null;
        try {
            outPrintWriter = new PrintWriter(outFile, "UTF-8");
        } catch (IOException e) {
            System.err.println("Cannot open " + outFilePath + " to write");
        }

        if (templateReader != null && outPrintWriter != null) {
            // Read and write
            try {
                while (true) {
                    String line = templateReader.readLine();
                    if (line == null) {
                        break;
                    }
                    String text = line.trim();
                    if (text.startsWith(MARKER) && text.endsWith(MARKER)) {
                        String type = text.substring(1, text.length() - 1);
                        String baseIndent = line.substring(0, line.indexOf(MARKER));

                        if (type.equals("mapTimezones")) {
                            MapTimezones mptz = new MapTimezones(ver);
                            mptz.write(outPrintWriter, baseIndent);
                        } else if (type.equals("metazoneMappings")) {
                            MetazoneMappings mzmp = new MetazoneMappings();
                            mzmp.write(outPrintWriter, baseIndent);
                        } else if (type.equals("zoneFormatting")) {
                            ZoneFormatting zfmt = new ZoneFormatting(ver);
                            zfmt.write(outPrintWriter, baseIndent);
                        } else {
                            throw new UnsupportedOperationException("Unknown type @" + type + "@");
                        }
                    } else {
                        outPrintWriter.println(line);
                    }
                }
            } catch (Exception e) {
                System.out.println("Exception " + e.getMessage());
            }
        }

        if (outPrintWriter != null) {
            outPrintWriter.close();
        }
        if (templateReader != null) {
            try {
                templateReader.close();
            } catch (IOException e) {
                System.err.println("Cannot close " + templateFilePath);
            }
        }
    }


    static void println(PrintWriter pw, String baseIndent, int offsets, String text)
            throws IOException {
        pw.write(baseIndent);
        for (int i = 0; i < offsets; i++) {
            pw.write(INDENT);
        }
        pw.println(text);
    }
}
