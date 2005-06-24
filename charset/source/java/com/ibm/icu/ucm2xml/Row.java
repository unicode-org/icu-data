/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.ucm2xml;

import java.util.Locale;

import com.ibm.icu.ucm2xml.Entry.Unknown_Entry;
import com.ibm.icu.utility.ByteTranslator;

class Row {
    // core member variables
    final int rowNumber;          // constant 
	private String indicator;
	private String[] jumpingMap;
    
    // to help other classes, express the state in different format
    private String[]    ucmEntries;
    private Entry[]     entries;
    
	Row(int stateNumber, String rawUcmRow) throws Unknown_Row{
        this.rowNumber = stateNumber;
        unwindRawUcmRow(rawUcmRow);
        
        setUcmJumpingEntries(); // Exception rise here
		setEntries();
	}
    
	private void unwindRawUcmRow(String rawUcmRow) throws Unknown_Row{
        initJumpingMap();
        
        if (rowNumber == 0) {
            indicator = "initial";
        } else {
            indicator = "";     // assume empty
        }
        
		rawUcmRow = rawUcmRow.trim();
		if (rawUcmRow.equals("")) {
            // default 00-ff.i, which has be prepared by initJumpingMap()
            return;
		}

		String[] raw_entries = rawUcmRow.split("\\s*,\\s*");
		int start_index = 0;
        
        // detect indicator, and shift start index when it is necessary
		if (raw_entries[0].toLowerCase(Locale.US).equals("initial")) {
			indicator = "initial";
			start_index = 1;
		} else if (
			raw_entries[0].toLowerCase(Locale.US).equals("surrogates")) {
			indicator = "surrogates";
			start_index = 1;
		}
        
        try{
    		for (int i = start_index; i < raw_entries.length; i++) {
                String raw_entry = raw_entries[i];
    			Entry entry = Entry.fromRawEntry(raw_entry);
    			modifyJumpingMap(entry);
    		}
        } catch(Entry.Unknown_Entry e){
            throw new Unknown_Row("Unkonw row: " + rawUcmRow, e);
        }
	}

	private void initJumpingMap() {
		jumpingMap = new String[256];     // 0x00 .. 0xFF  == 0x100 
		for (int i = 0; i < jumpingMap.length; i++) {
			// If a byte value is not specified in any column entry row, then it is illegal in the current state.
			// If an action is specified without a next state, then the next state number defaluts to 0.
			jumpingMap[i] = ":" + ByteTranslator.Byte2Hex(0) + ".i"; // assume illegal 
		}
	}

	private void modifyJumpingMap(Entry entry) {
		for (int j = entry.start; j <= entry.end; j++) {
			jumpingMap[j] = ":" + ByteTranslator.Byte2Hex(entry.next_state) + "." + entry.action;
		}
	}
    
	private void setUcmJumpingEntries() {
        // prepare header
		String result = ByteTranslator.Byte2Hex(0);
		String lastTag = jumpingMap[0];
        int lastStart = 0;

		for (int i = 1; i <= 0xFF; i++) {
			if (jumpingMap[i].equals(lastTag)) {
				continue;
			} else {
                if (i-1 > lastStart) {
                    result += "-" + ByteTranslator.Byte2Hex(i -1);
                }
                result += lastTag;
                result += ",";
                result += ByteTranslator.Byte2Hex(i);
				lastTag = jumpingMap[i];
                lastStart = i;
			}
		}
        
        if (lastStart == 0xFF) {  // 0xFF is the start number of the last range 
            result += lastTag;
        } else {     // xxx .. 0xFF  are same
            result += "-" + ByteTranslator.Byte2Hex(0xFF);
            result += lastTag;
		}
        
        ucmEntries =  result.split("\\s*,\\s*");
	}
    
    Entry[] getEntries(){
         return entries; 
    }

    private void setEntries(){
        int length = ucmEntries.length;
        entries = new Entry[length];

        try{
        for (int i = 0; i < length; i++) {
            String entry = ucmEntries[i];
            entries[i] = Entry.fromFormattedEntry(entry);
        }
        } catch(Unknown_Entry e){
            throw new Error("This should not happen.", e);
        }
    }

    boolean isInitial() {
        if (indicator.equals("initial")) {
            return true;
        } else {
            return false;
        }
    }
}
