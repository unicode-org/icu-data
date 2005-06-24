/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.utility;

import java.util.Locale;
import java.util.StringTokenizer;

public class ByteSequence{
	public static class Illformed_EscSeq extends Exception{}
	public static class Illformed_HexSeq extends Exception{}

    private String byteSequence;
    private ByteSequence(){} // for allocate memory space only
    
    public static ByteSequence newEmpty(){
        ByteSequence t = new ByteSequence();
        t.byteSequence = "";
        return t;
    }

    public static ByteSequence fromHexSeq(String hexSeq) throws Illformed_HexSeq{
        hexSeq = hexSeq.trim();
        hexSeq = hexSeq.toUpperCase(Locale.US);
        hexSeq = hexSeq.replaceAll("\\s+", " ");

        // Seems strange. Am I right?
        ByteSequence s = new ByteSequence();
        s.byteSequence = hexSeq;
        byte[] b;
        try {
            b = s.toRawSeq();
        } catch (NumberFormatException e) {
            throw new Illformed_HexSeq();
        }
        s = fromRawSeq(b);
        return s;
    }

    public static ByteSequence fromEscSeq(String escSeq) throws Illformed_EscSeq{
        escSeq = escSeq.trim();
        escSeq = escSeq.toUpperCase(Locale.US);
        escSeq = escSeq.replaceAll("\\s*\\\\X\\s*", " ");   // regular expression:  \s*\\x\*

        // Seems strange. Am I right?
        ByteSequence s = new ByteSequence();
        s.byteSequence = escSeq;
        byte[] b;
        try {
            b = s.toRawSeq();
        } catch (NumberFormatException e) {
            throw new Illformed_EscSeq();
        }
        s = fromRawSeq(b);
        return s;
    }
    

    public static ByteSequence fromRawSeq(byte[] rawSeq){
        ByteSequence t = new ByteSequence();
        t.byteSequence = "";
        for (int i = 0; i < rawSeq.length; i++) {
            t.byteSequence += ByteTranslator.Byte2Hex(rawSeq[i]) + " ";
        }
        t.byteSequence = t.byteSequence.trim();
        return t;
    }

    
    public byte[] toRawSeq(){
        // String.split()       has bug.
        StringTokenizer hexSeq = new StringTokenizer(byteSequence);
        
        byte[] rawSeq = new byte[hexSeq.countTokens()];
        int i = 0;
        while(hexSeq.hasMoreTokens()){
            String hex = hexSeq.nextToken();
            rawSeq[i] = ByteTranslator.Hex2Byte(hex);
            i++;
        }
        return rawSeq;
    }
    
    public String toEscSeq() {
	    String value = byteSequence;
	    if (!value.equals("")) {
	        value = value.replaceAll(" ", "\\\\x");    // great Java regexp!
	        value = "\\x" + value;
	    }
	    return value;
	}
    
    public String toHexSeq(){
        return byteSequence;
    }
    
    
    public int length(){
        // String.split()       has bug.
        StringTokenizer hexSeq = new StringTokenizer(byteSequence);
        return hexSeq.countTokens();
    }
    
    public boolean equals(Object obj){
        ByteSequence right = (ByteSequence) obj;
		return this.byteSequence.equals(right.byteSequence);
    }
}