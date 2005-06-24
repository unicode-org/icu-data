/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.ucm2xml;

import java.io.BufferedReader;
import java.io.IOException;

import com.ibm.icu.utility.ByteSequence;
import com.ibm.icu.utility.UcpSequence;
import com.ibm.icu.utility.ByteSequence.Illformed_EscSeq;
import com.ibm.icu.utility.UcpSequence.Illegal_Unicode_CodePoint;
 
public final class UcmCharmap {
    static class Illformed_CHARMAP_Line extends Exception {
        Illformed_CHARMAP_Line(String msg){
            super(msg);
        }

        Illformed_CHARMAP_Line(String msg, Throwable cause){
            super(msg,cause);
        }
    }
    private static final String eol = System.getProperty("line.separator", "\r\n");
    // core member variables for parsing one mapping
    private UcpSequence ucpSeq;
    private ByteSequence bSeq;
    private String indicator;
    
    // global information for all mapping lines
    private UcmHeader header; 
    private String isIndicatorExist;
	private boolean needVisualExpression;
    

    //
    private static final int maxUnset = -1;
    private static final int maxNa = -2;
    private int maxSingalByteValue;
    
	private StringBuffer normalEntries;
    private StringBuffer fubEntries;
    private StringBuffer fbuEntries;
    private StringBuffer sub1Entries;


	UcmCharmap(boolean showChar) {
		needVisualExpression = showChar;
		normalEntries = new StringBuffer();
		fubEntries = new StringBuffer();
		fbuEntries = new StringBuffer();
        sub1Entries = new StringBuffer();
        isIndicatorExist = "UNKNOWN";
        maxSingalByteValue = -1;
	}

	void parse(BufferedReader ucm,UcmHeader header) throws IOException, Illformed_CHARMAP_Line{
        this.header = header;
        
		for (String line = ucm.readLine(); line != null; line = ucm.readLine()) {
			line = line.trim();
			if (line.length() == 0 || line.startsWith("#")) {
				// skip empty line, comments
				continue;
			} else if (line.equals("END CHARMAP")) {
                // reach end, stop
				break;
			}

			parseOneMapping(line);
            findMaxSbValue();
            dumpOneMapping(line);

		} // for
	}

	private void parseOneMapping(String line) throws Illformed_CHARMAP_Line{
        int i = line.indexOf('|');
        
        // optional precision indicator.
        // set the tag for the first time
        if (isIndicatorExist.equals("UNKNOWN")){
            if (i == -1) {
                isIndicatorExist = "NO";
            } else {
                isIndicatorExist = "YES";
            }
        }
        // assert !isIndicatorExist.equals("UNKNOWN");

        // check whether the tag is consistent in the mapping table
        if (isIndicatorExist.equals("YES") && i != -1){
            indicator = line.substring(i + 1);  // exclude '|'
            if (indicator.length() == 0){   // '|' only
                throw new Illformed_CHARMAP_Line(line + eol + " The line has '|' only ");
            }
            line = line.substring(0, i);
        } else if (isIndicatorExist.equals("NO")  && i == -1){
            indicator = "1";
        } else {
            // (isIndicatorExist.equals("YES") && i == -1)
            // (isIndicatorExist.equals("NO")  && i != -1)
            String err = line + eol +
            "The precision indicator either must be present in all mappings or in none of them.";
            throw new Illformed_CHARMAP_Line(err);
        }
        

        String bValue;
        String uValue;
        i = line.indexOf('\\');
        if (i == -1) {
            throw new Illformed_CHARMAP_Line(line + eol + "Cannot found unicode value.");
        } else {
            bValue = line.substring(i);
            bValue = bValue.trim();
            
            uValue = line.substring(0, i);
            uValue = uValue.trim();
        }
        
		try {
			ucpSeq = UcpSequence.fromHexSeq_ucm(uValue);
            bSeq = ByteSequence.fromEscSeq(bValue);
		} catch (Illegal_Unicode_CodePoint e) {
            throw new Illformed_CHARMAP_Line(line, e);
		} catch (Illformed_EscSeq e) {
            throw new Illformed_CHARMAP_Line(line, e);
		}

        if (bSeq.length() == 0 || ucpSeq.length() == 0){
            throw new Illformed_CHARMAP_Line(line);
        }
        if (header.uconv_class.equals("SBCS") && bSeq.length() > 1){
            throw new Illformed_CHARMAP_Line(line + eol + "The mapping is declared as SBCS in header.");
        }
	} // parseOneLine()

	private void dumpOneMapping(String line) throws Illformed_CHARMAP_Line {
		// dump xml entry
		String u = " u=\"" + ucpSeq.toHexSeq_xml() + '"';
		String b = " b=\"" + bSeq.toHexSeq() + '"';
		String c = "";
		String temp = ucpSeq.toHtmlLiteral();;
		if (needVisualExpression && !temp.equals("") ){
		    c = " c=\"" + temp + '"';
		}
		
        // compose the xmlEntry
        String xmlEntry;
		if (indicator.equals("0")) {
			xmlEntry = "  <a" + u + b + c + "/>";
		} else if (indicator.equals("1")) {
			xmlEntry = "  <fub" + u + b + c + "/>";
		} else if (indicator.equals("2")) {
		    boolean isValid = bSeq.equals(header.subchar1);
		    isValid = isValid || (bSeq.length() == 1 && bSeq.equals(header.subchar));    
			if (isValid) {
		        xmlEntry = "  <sub1" + u + c + "/>";
			} else {
		        String err = "Illformed_CHARMAP_Line : " + line + "\n" +
		        "The line does not match the requirement of |2.";
		        throw new Illformed_CHARMAP_Line(err);
			}
		} else if (indicator.equals("3")) {
			xmlEntry = "  <fbu" + u + b + "/>";
		} else { 
		    throw new Illformed_CHARMAP_Line("Illformed_CHARMAP_Line : " + line);
		}
        
        // put the xmlEntry
        if (indicator.equals("0")) {
            normalEntries.append(xmlEntry + eol);
        } else if (indicator.equals("1")) {
            fubEntries.append(xmlEntry + eol);
        } else if (indicator.equals("2")) {
            sub1Entries.append(xmlEntry + eol);
        } else if (indicator.equals("3")) {
            fbuEntries.append(xmlEntry + eol);
        } else {
            // assert false
            throw new Error("We should not touch here.");
        }
	} 
    
    String getNormalEntries(){
        return normalEntries.toString();
    }
    String getFubEntries(){
        return fubEntries.toString();
    }
    String getFbuEntries(){
        return fbuEntries.toString();
    }
    String getSub1Entries(){
        return sub1Entries.toString();
    }
    
    
    private void findMaxSbValue(){
        if (bSeq.length() > 1){
            maxSingalByteValue = maxNa;
            return;
        }
        
        if (bSeq.length() == 0){
            throw new Error("This should not happen.");
        }
        
        int i = 0xFF & bSeq.toRawSeq()[0];

        if (maxSingalByteValue == maxUnset) {
            maxSingalByteValue = i;
        } else if (i > maxSingalByteValue){
            maxSingalByteValue = i;
        }
    }
    
    public boolean hasMaxSbValue(){
        if (maxSingalByteValue == maxNa){
            return false;
        } else if (maxSingalByteValue == maxUnset){
            throw new Error("This should not happen.");
        } else{
            return true;
        }
    }

    public int maxSbValue(){
        return maxSingalByteValue;
    }

}
