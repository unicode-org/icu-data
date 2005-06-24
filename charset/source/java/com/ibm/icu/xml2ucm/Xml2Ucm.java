/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.xml2ucm;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

//import MbcsStateMachine.StateMachineHasLoop;

/**
 * @author YANG RongWei
 * 
 * Convert "UTR22 .XML file" to "ICU .UCM file"
 * 
 * Usage:
 *          Xml2Ucm <XML files' dir>  <UCM files' dir>    
 *
 */
//  Known limitation (ignored fields):
//      /characterMapping/assignments/range/@*
//
//      /characterMapping/iso2022
//
//      /characterMapping/attribute::description
//      /characterMapping/attribute::contact
//      /characterMapping/attribute::registrationAuthority
//      /characterMapping/attribute::registrationName
//      /characterMapping/attribute::copyright
//      /characterMapping/attribute::bidiOrder
//      /characterMapping/attribute::combiningOrder
//      /characterMapping/attribute::normalization
//      /characterMapping/assignments/*/@v
//      /characterMapping/assignments/*/@c
//      /characterMapping/assignments/*/@rc

public class Xml2Ucm {
    private static class XmlFileFormatIsNotUnderstand extends Exception{}
    private static class XmlFile_CharacterMapping_id_IsNull extends Exception{}

    private static final String versionNumber = "0.95";
	private static File xmlDir;
    private static File ucmDir;
    private static String[] xmlFilesName;
    private static String   xmlFileName;
    private static String   ucmFileName;
    private static DocumentBuilder xmlParser; 

    public static void main(String[] args) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            xmlParser = dbf.newDocumentBuilder();
        } catch (ParserConfigurationException e1) {
            System.out.println("Cannot initialize XML parser, Xml2Ucm is aborted.");
            e1.printStackTrace();
            return;
        }
    
        if (!parseArgs(args)) {
            return;
        }
        
        StringBuffer errorFiles = new StringBuffer();
    
        for (int i = 0; i < xmlFilesName.length; i++) {
            String filename = xmlFilesName[i];
            File xmlFile = new File(xmlDir, filename);
            xmlFileName = xmlFile.toString();
            System.out.println("Parsing " + xmlFileName);
            try {
				parseOneFile();
			} catch (Exception e) {
                errorFiles.append(xmlFileName + "\n");
                e.printStackTrace();
			}
            System.out.println("        Parsed.");
        }
        
        System.out.println("====================");
        System.out.println("Done.");
        System.out.println();
        
        if (errorFiles.length() > 0 ){
            System.err.println("These files' CHARMAP has error:");
            System.err.println("----------------------------------------");
            System.err.println(errorFiles.toString());
        }
    } // main(String[] args)
    
    private static void parseOneFile() throws Exception{ 
        Element charMap;
//        try {
            Document xmlDoc = xmlParser.parse(xmlFileName);
            charMap = (Element) xmlDoc.getElementsByTagName("characterMapping").item(0);
            if (!canBeProcessed(charMap)){
                throw new XmlFileFormatIsNotUnderstand();
            }
            ucmFileName = charMap.getAttribute("id");
            if (ucmFileName.compareTo("") == 0){
                throw new XmlFile_CharacterMapping_id_IsNull(); 
            }
           
//        } catch (Exception  e) {
//            e.printStackTrace();
//            return;
//        }
        
        GenUcmComments  gc = new GenUcmComments();
        GenUcmHeader    gh = new GenUcmHeader(ucmFileName);
        GenUcmCharmap   gm = new GenUcmCharmap();
//        try {
            gc.parse(charMap);
            gh.parse1(charMap);
            gm.parse(charMap);
            gh.parse2(gm.charsetFamily);
//        } catch (Exception  e) {
//			e.printStackTrace();
//            return;
//		}
        
        
        PrintWriter out;
//        try {
            File f;
            FileOutputStream s_byte;
            OutputStreamWriter s_char;
            BufferedWriter buf;

            f = new File(ucmDir, ucmFileName + ".ucm");
            f.createNewFile();
			s_byte = new FileOutputStream(f);
            s_char = new OutputStreamWriter(s_byte, "UTF8");
            buf = new BufferedWriter(s_char);
            out = new PrintWriter(buf);
//		} catch (Exception  e) {
//            e.printStackTrace();
//            return;
//        }
        
        gc.dump(out);
        gh.dump(out);
        gm.dump(out);
        
        out.close();
    } // parseOneFile()
    
    private static boolean canBeProcessed(Element charMap) {
        // TODO: something to do
//        if (charMap.getElementsByTagName("stateful_siso").getLength() > 0){
//            return false;
//        }
        if (charMap.getElementsByTagName("iso2022").getLength() > 0){
            return false;
        }
        return true;
    }
    
    private static boolean parseArgs(String[] args) {
        if (args.length != 2) {
            printUsage();
            return false;
        }
        xmlDir = new File(args[0]);
        ucmDir = new File(args[1]);
        if (!xmlDir.exists()){
            System.out.println("<XML files' dir> does not exist");
            return false;
        }
        
        if (!ucmDir.exists()){
            ucmDir.mkdirs();
        }

        xmlFilesName = xmlDir.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase(Locale.US).endsWith(".xml");   // May raise some i18n troubles. 
            }
        });
        return true;
    }
    
    private static void printUsage() {
        String help = 
        "Xml2Ucm version " + 
        versionNumber +
        "\nUsage:" +
        "Xml2Ucm <XML files' dir>  <UCM files' dir>";
        System.out.println(help);
    }

    static String getXmlFileName() {
        return xmlFileName;
    }

    static String getUcmFileName() {
        return ucmFileName;
    }
}
