/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.utility;

import java.util.Locale;

/**
 * @author YANG RongWei
 *
 * java.text.NumberFormat   is a superman.
 * I am unable to talk with him.
 * 
 */
public final class ByteTranslator {
    private ByteTranslator(){};
    public static String Byte2Hex(byte b){
        int i = b & 0xFF;  // The type of literal 0xFF is int 
        String hex = Integer.toHexString(i).toUpperCase(Locale.US);
        if (hex.length() < 2) {
            hex = '0' + hex;
        }
        return hex;
    }

    public static String Byte2Hex(int i){
        if (i < 0 || i > 0xFF){
            throw new NumberFormatException();
        }
        String hex = Integer.toHexString(i).toUpperCase(Locale.US);
        if (hex.length() < 2) {
            hex = '0' + hex;
        }
        return hex;
    }

    public static byte Hex2Byte(String hex){
        if (hex.length() > 2) {
            throw new NumberFormatException();
        }
        int i = Integer.parseInt(hex, 16);
        return (byte) i;
    }
}
