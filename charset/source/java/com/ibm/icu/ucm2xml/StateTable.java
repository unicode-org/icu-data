/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.ucm2xml;

import java.util.ArrayList;
import java.util.List;

import com.ibm.icu.ucm2xml.UcmCharmap;
import com.ibm.icu.ucm2xml.UcmHeader;

public final class StateTable {
	private Row[] stateTable;
    private List initialStates;     // List <Row>, because we cannot use List<int>, great Java!
    
    public StateTable(){
        initialStates = new ArrayList();
    }
    
    public void parse(UcmHeader header, UcmCharmap mapping)throws TooManyInitialState, UnknownRow {
        List states;
        String uconv_class;
        states = header.icu_states;
        uconv_class = header.uconv_class;

        if (states.size() == 0 && uconv_class.equals("")){
            throw new Error("This should not happen.");
        }
        
        if (states.size() == 0 && !uconv_class.equals("")){
            if (uconv_class.equals("SBCS") && mapping.hasMaxSbValue() && mapping.maxSbValue() <= 0x7F){
                states.add("0-7f");
            } else if (uconv_class.equals("MBCS")){
                throw new UnknownRow();
            }else {
                getDefaultUcmStateTable(states, uconv_class);
            }
        }

        // assert states.size() != 0
         
		int length = states.size();
		stateTable = new Row[length];

		for (int i = 0; i < length; i++) {
			String rawState = (String) states.get(i);
            Row row = new Row(i, rawState);
			stateTable[i] = row;
			if (stateTable[i].isInitial()) {
				initialStates.add(row);
			}
		}

		if (initialStates.size() > 2) {
			System.err.println(
				"The state table has more than two initial states.");
			System.err.println("Unable to process this file.");
			throw new TooManyInitialState();
		} else if(initialStates.size() == 0){
            throw new Error("This should happen.");
		} else if(initialStates.size() == 2){
            // TODO: fix fullmap here?
        }
        
	}

	public String getXmlExpression() {
        StringBuffer buf = new StringBuffer();
		switch (initialStates.size()) {
			case 1 :
				dumpSingleSateMachine(buf);
				break;
			case 2 :
				dumpSiSoSateMachine(buf);
				break;
			default :
				// assert false
                throw new Error("We should not touch here.");
		}
        return buf.toString();
	}

	private void dumpSingleSateMachine(StringBuffer buf) {
        boolean doNotOutputInvalid = true; // TODO: Hardcoded behavior.
        
		buf.append(" <validity>\r\n");

		for (int i = 0; i < stateTable.length; i++) {
			Row state = stateTable[i];
			Entry[] entries = state.getEntries();
			for (int j = 0; j < entries.length; j++) {
				Entry entry = entries[j];
				
				if (entry.action == 'i' && entries.length > 1 && doNotOutputInvalid){
                    // If the row has more than one entry,
                    // doesn't output its INVALID entries 
                    continue;
				}

				String currentName = STATE_NAMES[i];
				String nextName = STATE_NAMES[entry.next_state];
				buf.append(
					"  "
						+ entry.getXmlExpression(currentName, nextName)
						+ "\r\n");
			}
		}
		buf.append(" </validity>\r\n");
	}

	private void dumpSiSoSateMachine(StringBuffer buf) {
        boolean doNotOutputInvalid = true; // TODO: Hardcoded behavior.

        if (initialStates.size() != 2){
            throw new Error("This should not happen.");
        }
        
        int otherInit = ((Row)initialStates.get(0)).rowNumber;
        if (otherInit == 0) {
            otherInit = ((Row)initialStates.get(1)).rowNumber;
        }

		StringBuffer buf0 = new StringBuffer();
		StringBuffer buf1 = new StringBuffer();
        
		buf.append(" <stateful_siso>\r\n");
		buf0.append("  <validity>\r\n");
		buf1.append("  <validity>\r\n");

		// go through all 'row' in the state table
		for (int i = 0; i < stateTable.length; i++) {
			Row row = stateTable[i];

			// go through all 'entry' in the row
			Entry[] entries = row.getEntries();
			for (int j = 0; j < entries.length; j++) {

				// parse the entry
				Entry entry = entries[j];
				if (entry.action == 's') {
					continue; // do not ouput state change entry.
				}

                if (entry.action == 'i' && entries.length > 1 && doNotOutputInvalid){
                    // If the row has more than one entry,
                    // doesn't output its INVALID entries 
                    continue;
                }

				String currentName;
                if (row.rowNumber == otherInit){
                    currentName = "FIRST";
                } else {
                   currentName = STATE_NAMES[row.rowNumber];
                }
                
                String nextName;
                if (entry.next_state == otherInit){
                    nextName = "VALID";
                } else {
                    nextName = STATE_NAMES[entry.next_state];
                }
                
				String xmlEntry = entry.getXmlExpression(currentName, nextName);

				if (entry.next_state == 0) {
					// Row 0 is always an initial state
					buf0.append("   " + xmlEntry + "\r\n");
				} else {
					buf1.append("   " + xmlEntry + "\r\n");
				}
			} // go through all 'entry' in the row
		} // go through all 'row' in the state table

		buf0.append("  </validity>\r\n");
		buf1.append("  </validity>\r\n");
		buf.append(buf0);
		buf.append(buf1);
		buf.append(" </stateful_siso>\r\n");
	}

	private static final String[] STATE_NAMES =
		{
			"FIRST",
			"SECOND",
			"THIRD",
			"FOURTH",
			"FIFTH",
			"SIXTH",
			"SEVENTH",
			"EIGHTH",
			"NINTH",
			"TENTH",
			"ELEVENTH",
			"TWELFTH",
			"THIRTEENTH",
			"FOURTEENTH",
			"FIFTEENTH",
			"SIXTEENTH",
			"SEVENTEENTH",
			"EIGHTTEENTH",
			"NINETEENTH",
			"TWENTIETH" };

	public void getDefaultUcmStateTable(List states, String name) {
		//assert  icu_states.size() == 0;
		if (states.size() != 0) {
			throw new Error("This should not happen.");
		}

		if (name.equals("MBCS")) { // Haha, MBCS itself does not have one. :)
			throw new Error("MBCS does not has a default state table.");
		}

		if (name.equals("SBCS")) {
			states.add("00-FF");
			return;
		}

		if (name.equals("DBCS")) {
			states.add("0-3f:3, 40:2, 41-fe:1, ff:3");
			states.add("41-fe");
			states.add("40");
			states.add("0-ff.i");
			return;
		}

		if (name.equals("EBCDIC_STATEFUL")) {
			states.add("0-ff, e:1.s, f:0.s");
			states.add("initial, 0-3f:4, e:1.s, f:0.s, 40:3, 41-fe:2, ff:4");
			states.add("0-40:1.i, 41-fe:1, ff:1.i");
			states.add("0-ff:1.i, 40:1.");
			states.add("0-ff:1.i");
			return;
		}
        
		throw new Error("We should not touch here.");
	}
}
