/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.ucm2xml;

import java.util.Locale;

import com.ibm.icu.utility.ByteTranslator;


class Entry {
	class UnknownEntry extends Exception {
        UnknownEntry(String msg){
            super(msg);
        }
    }
    // cannot found data type "unsinged byte", exciting!
    // use "int" rather than "byte" to avoid the implicit sign-extending conversion
    // For example:  (byte) 0x80 > (byte) 0x7F == false !!
	final int start;
	final int end;
	final int next_state;
	final char action;

    static Entry fromFormattedEntry(String entry) throws UnknownEntry{
        return new Entry(entry, true);
    }
    
    static Entry fromRawEntry(String entry) throws UnknownEntry{
        return new Entry(entry, false);
    }
    
	private Entry(String entry, boolean isFormatted) throws UnknownEntry {
		// Defined two customed actions to help processing
		// '-'      non-terminated
		// 'v'      terminated and valid
        
        char temp_action;
		int i = entry.indexOf('.');
		if (i == -1) {
            temp_action = '-'; // assume non-terminated
        } else if (i == entry.length() - 1) { // '.' only
            temp_action = '-';
            entry = entry.substring(0, i);
		} else if (i == entry.length() - 2) { // '.'   and   one character
			temp_action = entry.substring(i + 1).toLowerCase(Locale.US).charAt(0);
            boolean extraTag;
            extraTag = isFormatted ? temp_action == '-'|| temp_action == 'v' : false;
			if (temp_action == 'u'
				|| temp_action == 'p'
				|| temp_action == 'i'
				|| temp_action == 's'
                || extraTag
                ) {
                entry = entry.substring(0, i);
			} else {
				throw new UnknownEntry("Unkonwn action.");
			}
		} else {
			throw new UnknownEntry("'.' followed by more than one char.");
		}

		i = entry.indexOf(':');
		if (i == -1) {
			if (temp_action == '-') {
                // action != '-' 
                // no next state, no action  ==  state 0, valid action
				temp_action = 'v';
			}
			// action == ...    , terminated and ...
			// action == 'v'    , terminated and valid
			next_state = 0;
		} else {
			// action == ...    , terminated and ...
			// action == '-'    , non-terminated
            //
			// action != 'v'      next state, no action ==  state x, non-terminated
            //                    But this assumption is NOT true for siso tables. 
            byte b = ByteTranslator.Hex2Byte(entry.substring(i + 1)); 
            if (b < 0){ // nextstate = 0..7F
                throw new UnknownEntry("Next state larger than 0x7F.");
            }
			next_state = 0xFF & b;    // 0xFF is to help read 
			entry = entry.substring(0, i);
		}
        
        action = temp_action;
        
		i = entry.indexOf('-');
		if (i == -1) {
            //  cannot found data type "unsinged byte", exciting
			start =  0xFF & ByteTranslator.Hex2Byte(entry);
			end = start;
		} else {
			start = 0xFF & ByteTranslator.Hex2Byte(entry.substring(0, i));
			end =  0xFF & ByteTranslator.Hex2Byte(entry.substring(i + 1));
		}
		if (start > end){
			throw new UnknownEntry("Start larger than End.");
		}
	}
    
    String getXmlExpression(String currentStateName, String nextStateName){
        
        String type = " type=\"" + currentStateName + '"'; 
        
        String s = " s=\"" + ByteTranslator.Byte2Hex(start) + '"';
                
        String e = "";
        if (end != start) {
            e = " e=\"" + ByteTranslator.Byte2Hex(end) + '"';
        }
        
        String next;
        String max = "";
//        max = "FFFF";   // TODO: turn on/off this line for test.            
        if (action == '-' && nextStateName.equals("VALID")) { // for Si/So TODO: kick off this sentance
            max = "FFFF";            
        }
        
        // determine our next state base on entry's 'action'        
        if (action == '-') {
            next = nextStateName;
        } else if (action == 'v') {
            next = "VALID";
            max = "FFFF";            
        } else if (action == 'u') {
            next = "UNASSIGNED";
        } else if (action == 'p') {
            next = "VALID";
            max = "10FFFF";
        } else if (action == 'i') {
            next = "INVALID";
        } else if (action == 's') {
            // assert false;            
            throw new Error("The SI/SO entry does not have an XML equivalent.");
        } else {
            // assert false;
            throw new Error("We should not touch here.");
        }
        
        next = " next=\"" + next + '"';
        
        if (!max.equals("")) {
            max = " max=\"" + max + '"';
        }
        
        String result;
        result = "<state" 
                + type
                + next
                + s
                + e
                + max
                + "/>";
        return result;
    }

}