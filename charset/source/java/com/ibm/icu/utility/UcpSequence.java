/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.utility;

import java.util.Locale;
import java.util.StringTokenizer;

/**
 * @author YANG RongWei
 *
 * Unicode Code Point Sequnce
 */
public class UcpSequence {
    // exceptions
    public static class Illegal_Unicode_CodePoint extends Exception{}
    
    // code point: any value in the unicode codespace
    // codespace:  a range of integers from 0 to 0x10FFFF
    private String ucpHexSeq;  // Hex sequence of code points
    private UcpSequence(){}
    
    public static UcpSequence fromHexSeq_ucm(String value) throws Illegal_Unicode_CodePoint{
        value = value.trim();

        if (value.equals("")){
            UcpSequence t = new UcpSequence(); 
            t.ucpHexSeq = "";   // give us blank, return them blank
            return t;
        }

        value = value.toUpperCase(Locale.US);
        value = value.replaceAll("<U", " "); // NOTICE: leading <U will be space
        value = value.trim();                // NOTICE: trim leading space
        value = value.replaceAll(">","");
        value = value.replaceAll("\\s+"," ");

        UcpSequence t = new UcpSequence(); 
        t.ucpHexSeq = value;
        t.toJavaString();   // Exception rise here
        return t;
    }
    
    public static UcpSequence fromHexSeq_xml(String value) throws Illegal_Unicode_CodePoint{
        value = value.trim();
        value = value.toUpperCase(Locale.US);
        value = value.replaceAll("\\s+"," ");

        UcpSequence t = new UcpSequence();
        t.ucpHexSeq = value;
        t.toJavaString();   // Exception rise here
        return t;
    }
    
    private String toJavaString() throws Illegal_Unicode_CodePoint{
        // Simply construct an raw UTF-16 code point sequence
        // no ill-formed surrogate pair checking.
        
        // String.split()       has bug.

        StringTokenizer hexSeq = new StringTokenizer(ucpHexSeq);
        String result = "";
        while(hexSeq.hasMoreTokens()){
            String hex = hexSeq.nextToken();
            int codeUnit32 = Integer.parseInt(hex, 16);
            // If JDK 1.5 is released, we can use
            // Character.toChars(int codePoint)
            // But now:
            if (0xFFFF < codeUnit32 && codeUnit32 <= 0x10FFFF){
                codeUnit32 -= 0x10000;
                char highS = (char) (codeUnit32 / (0xDC00 - 0xD800) + 0xD800);
                char lowS  = (char) (codeUnit32 % (0xDC00 - 0xD800) + 0xDC00);
                result += String.valueOf(highS) + String.valueOf(lowS);
            } else if (0 <= codeUnit32 && codeUnit32 <= 0xFFFF){
                char codeUnit16 = (char) codeUnit32;
                result += codeUnit16;
            } else {
                throw new Illegal_Unicode_CodePoint();
            }
        }
        return result;
    }
     
    public String toHexSeq_xml(){
        return ucpHexSeq;
    }

    public String toHexSeq_ucm() {
        String value = ucpHexSeq;
		if (!value.equals("")) {
			value = ucpHexSeq.replaceAll(" ", "><U");
			value = "<U" + value + "> ";
		}
		return value;
	}

    public String toHtmlLiteral(){
         char[] chars;
         try {
             chars = toJavaString().toCharArray();
         } catch (Illegal_Unicode_CodePoint e) {
             // assert false;
             throw new Error("This should not happen.", e);
         }

         String result= "";
         for (int i = 0; i < chars.length; i++) {
             char c = chars[i];
             if ( Character.isISOControl(c) || c >= 0xFFFE){
                 //result += '\uFFFD';
                 // do nothing.
             }  else if (c == '\'') {
                 result +=  "&apos;";
             } else if (c == '\"') {
                 result +=  "&quot;";
             } else if (c == '<') {
                 result +=  "&lt;";
             } else if (c == '&') {
                 result +=  "&amp;";
             } else if (c == '>') {
                 result +=  "&gt;";
             } else {
                 // Simply contact an raw UTF-16 code point sequence
                 // no ill-formed surrogate pair checking.
                 result += Character.toString(c);
             }
         }
        return result;
     }

    public int length(){
        // String.split()       has bug.
        StringTokenizer hexSeq = new StringTokenizer(ucpHexSeq);
        return hexSeq.countTokens();
    }
     
}
