/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.ucm2xml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.ibm.icu.ucm2xml.UcmCharmap.Illformed_CHARMAP_Line;
import com.ibm.icu.ucm2xml.UcmHeader.Unknown_UCM_Header;
import com.ibm.icu.ucm2xml.UcmStateTable.StateTable;
import com.ibm.icu.ucm2xml.UcmStateTable.TooManyInitialState;
import com.ibm.icu.ucm2xml.UcmStateTable.UnknownRow;

public final class Ucm2Xml {

	private static final String versionNumber = "0.95";
    private static final String eol = System.getProperty("line.separator", "\r\n");

	private static File ucmDir;
	private static File xmlDir;
	private static String[] ucmFiles;
    private static StringBuffer ioErrorFiles;
    private static StringBuffer headerErrorFiles;
    private static StringBuffer mappingErrorFiles;

	public static void main(String[] args) {
		if (!parseArgs(args)) {
			return;
		}
        
        ioErrorFiles = new StringBuffer();
        headerErrorFiles = new StringBuffer();
        mappingErrorFiles = new StringBuffer();
        
        System.out.println("Start:");
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
		for (int i = 0; i < ucmFiles.length; i++) {
			String filename = ucmFiles[i];
            String fullPathName = ucmDir.getPath() + File.separator + filename;
			System.out.println("Parsing " + fullPathName);
			try {
				parseOneFile(filename);
			} catch (Unknown_UCM_Header e) {
                headerErrorFiles.append(fullPathName + eol);
				e.printStackTrace();
			} catch (IOException e) {
                ioErrorFiles.append(fullPathName + eol);
                e.printStackTrace();
			} catch (Illformed_CHARMAP_Line e) {
                mappingErrorFiles.append(fullPathName + eol);
                e.printStackTrace();
			}
			System.out.println("        Parsed.");
		}
        System.out.println("========================================");
        System.out.println("Done.");
                
		if (headerErrorFiles.length() > 0) {
			System.err.println();
			System.err.println("These files' header has error:");
            System.err.println("----------------------------------------");
			System.err.println(headerErrorFiles.toString());
		}
        if (mappingErrorFiles.length() > 0){
            System.err.println();
            System.err.println("These files' CHARMAP has error:");
            System.err.println("----------------------------------------");
            System.err.println(mappingErrorFiles.toString());
        }
        if (ioErrorFiles.length() > 0){
            System.err.println();
            System.err.println("These files have I/O error");
            System.err.println("----------------------------------------");
            System.err.println(ioErrorFiles.toString());
        }
	}

	private static void parseOneFile(String filename) throws Unknown_UCM_Header, IOException, Illformed_CHARMAP_Line {
		BufferedReader ucm;
		ucm = openUcm(filename);

		UcmHeader header = new UcmHeader();
		UcmCharmap mapping = new UcmCharmap(true);
        StateTable stateTable = new StateTable();

		header.parse(ucm);
		mapping.parse(ucm, header);
        
        try {
			stateTable.parse(header,mapping);
		} catch (TooManyInitialState e) {
            throw new Unknown_UCM_Header(e);
		} catch (Unknown_Row e) {
            throw new Unknown_UCM_Header(e);
		}
        
        writeXML(filename, header, stateTable, mapping);
	}

	private static BufferedReader openUcm(String filename) {
		File ucmFile = new File(ucmDir, filename);
        FileInputStream stream_byte;
        InputStreamReader stream_char;  // Unicode code unit 16-bit
		try {
			stream_byte = new FileInputStream(ucmFile);
            stream_char = new InputStreamReader(stream_byte, "UTF8");
		} catch (FileNotFoundException e1) {
            throw new Error("This should not happen.");
		} catch (UnsupportedEncodingException e2) {
            throw new Error("This should not happen.");
		}

        BufferedReader ucm = new BufferedReader(stream_char, 1024);
        return ucm;
	}
    
    private static BufferedWriter openXml(String fileBaseName) throws IOException{
        File xmlFile = new File(xmlDir, fileBaseName + ".xml");
        xmlFile.createNewFile();
        
        FileOutputStream stream_byte;
        OutputStreamWriter stream_char;  // Unicode code unit 16-bit
        stream_byte = new FileOutputStream(xmlFile);
        stream_char = new OutputStreamWriter(stream_byte, "UTF8");
        
        BufferedWriter xml = new BufferedWriter(stream_char, 1024);
        return xml;
    }

    private static void writeXML(String ucmFileName,UcmHeader header, StateTable stateTable, UcmCharmap mapping) throws IOException{
        // assert ucmFileName tailing with .ucm;
        // assert ucmFileName length > 4;
        int length = ucmFileName.length() - 4;
        String fileBaseName = ucmFileName.substring(0, length);
        
        BufferedWriter xml = openXml(fileBaseName);
        
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
//        String date = "2004-05-25";
//        String date = "2004-06-07";
        String validity = stateTable.getXmlExpression();
        String sub = header.subchar.toHexSeq();
        String sub1 = header.subchar1.toHexSeq();
        // assert !sub.equals("")
        sub = " sub=\"" + sub + '"';
        if (!sub1.equals("")){
            sub1 = " sub1=\"" + sub1 + '"';
        }
//        sub1 = "";  // TODO: turn on/off this line for test.
        
        String normalMapping = mapping.getNormalEntries();
        String fubMapping = mapping.getFubEntries();
        String fbuMapping = mapping.getFbuEntries();
        String sub1Mapping = mapping.getSub1Entries();
        
        if (fubMapping.equals("")){
            fubMapping = "  <!-- NONE -->" + eol;
        }
        if (fbuMapping.equals("")){
            fbuMapping = "  <!-- NONE -->" + eol;
        }
        
        xml.write("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + eol);
//        xml.write("<!DOCTYPE characterMapping SYSTEM \"http://www.unicode.org/unicode/reports/tr22/CharacterMapping.dtd\">" + eol);
//        xml.write("<!DOCTYPE characterMapping SYSTEM \"http://www.unicode.org/unicode/reports/tr22/CharacterMapping-3.dtd\">" + eol);
      xml.write("<!DOCTYPE characterMapping SYSTEM \"CharacterMapping-3.dtd\">" + eol);
        xml.write("<characterMapping id=\"" + fileBaseName + "\" version=\"1\">" + eol);
        xml.write(" <history>" + eol);
        xml.write("  <modified version=\"1\" date=\"" + date+ "\">");
        xml.write("This file was generated with Ucm2Xml.java.");
        xml.write("</modified>" + eol);
        xml.write(" </history>" + eol);
        xml.write(validity);
        xml.write(" <assignments" + sub + sub1 + ">" + eol);
		xml.write("  <!-- One to one mappings -->" + eol);
        xml.write(normalMapping);
        xml.write("  <!-- Fallback mappings from Unicode to bytes -->" + eol);
        xml.write(fubMapping);
        xml.write("  <!-- Fallback mappings from bytes to Unicode -->" + eol);
        xml.write(fbuMapping);
        
        // TODO: turn on/off these lines for test.
        if (!sub1Mapping.equals("")){
            xml.write("  <!-- Unicode code point maps to the 'narrow' sub1 -->" + eol);
            xml.write(sub1Mapping);
        }
        
        xml.write(" </assignments>" + eol);
        xml.write("</characterMapping>" + eol);
		xml.close();
	}

	private static boolean parseArgs(String[] args) {
		if (args.length != 2) {
			printUsage();
			return false;
		}
		ucmDir = new File(args[0]);
		xmlDir = new File(args[1]);
		if (!ucmDir.exists()) {
			System.out.println("<UCM files' dir> does not exist");
			return false;
		}

		if (!xmlDir.exists()) {
			xmlDir.mkdirs();
		}

		ucmFiles = ucmDir.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.toLowerCase(Locale.US).endsWith(".ucm");
				// May raise some i18n troubles.
			}
		});

		return true;
	}

	private static void printUsage() {
		String help =
			"Ucm2Xml version "
				+ versionNumber
				+ "\nUsage: "
				+ "Ucm2Xml <UCM files' dir> <XML files' dir>";
		System.out.println(help);
	}
}
