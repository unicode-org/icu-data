/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.ucm2xml;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.ibm.icu.ucm2xml.StateTable;
import com.ibm.icu.ucm2xml.TooManyInitialState;
import com.ibm.icu.utility.ByteSequence;
import com.ibm.icu.utility.ByteSequence.Illformed_EscSeq;
import com.ibm.icu.utility.ByteSequence.Illformed_HexSeq;

public final class UcmHeader {
	public static class Unknown_UCM_Header extends Exception {
		Unknown_UCM_Header(String msg) {
			super(msg);
		}
		Unknown_UCM_Header(String msg, Throwable cause) {
			super(msg, cause);
		}
		Unknown_UCM_Header(Throwable cause) {
			super(cause);
		}
	}

    public String code_set_name;
	public ByteSequence subchar;
	public ByteSequence subchar1;
    public int mb_cur_max;
    public int mb_cur_min;

    // For determining the state table 
    public String uconv_class;            //
    public List icu_states;

	// constructor
	UcmHeader() {
        code_set_name = "";
		subchar = ByteSequence.newEmpty();
		subchar1 = ByteSequence.newEmpty();
        mb_cur_max = 0;
        mb_cur_min = 0;

        uconv_class = "";
        icu_states = new ArrayList();
	}

	void parse(BufferedReader ucm) throws IOException, Unknown_UCM_Header {

		// continuing read file till reach "CHARMAP"
		for (String line = ucm.readLine();
			line != null && !line.equals("CHARMAP");
			line = ucm.readLine()) {
			line = line.trim();
			// skip empty line, and comments
			if (line.length() == 0 || line.startsWith("#")) {
				continue;
			}
			parseOneLine(line);
		}

        if (subchar.length() <= 0) {
            subchar = ByteSequence.fromRawSeq(new byte[]{0x1A});
        }

        if (icu_states.size() == 0 && uconv_class.equals("")){
            throw new Unknown_UCM_Header("Doesn't find either <uconv_class> or <icu:state>");
        }
        
        if (uconv_class.equals("SBCS")){
            if (mb_cur_min > 1 || mb_cur_max > 1){
                throw new Unknown_UCM_Header("SBCS byte number > 1");
            }
            mb_cur_min = 1;
            mb_cur_max = 1;
        }
	}

	private void parseOneLine(String line) throws Unknown_UCM_Header {
		// assert !line.equals("");
		// empty line, will not be put in.

		int i = line.indexOf('>');

		if (i == -1) {
			throw new Unknown_UCM_Header(line);
		}

		String key;
		key = line.substring(0, i + 1); // 0 .. i
		key = key.toLowerCase(Locale.US);

		// error detection here
		if (key.length() == 0) {
			throw new Unknown_UCM_Header(line);
		}

		String value;
		value = line.substring(i + 1); // i+1 .. end
		value = value.trim();

		if (key.equals("<code_set_name>")) {
			value = value.replaceAll("\"", "");
			code_set_name = value;
			return;
		}

		if (key.equals("<subchar>")) {
			try {
				subchar = ByteSequence.fromEscSeq(value);
			} catch (Illformed_EscSeq e) {
				throw new Unknown_UCM_Header(line, e);
			}
			return;
		}

		if (key.equals("<subchar1>")) {
			try {
				subchar1 = ByteSequence.fromEscSeq(value);
			} catch (Illformed_EscSeq e) {
				throw new Unknown_UCM_Header(line, e);
			}
			return;
		}

		if (key.equals("<icu:state>")) {
			icu_states.add(value);
			uconv_class = "MBCS"; // generic ICU converter type
			return;
		}

		if (key.equals("<uconv_class>")) {
			if (icu_states.size() != 0) { // state table exist
				uconv_class = "MBCS";     // generic ICU converter type
				return;
			}
            
			value = value.replaceAll("\"", "");
			value = value.toUpperCase(Locale.US);
            
            if (value.equals("SBCS")
             || value.equals("DBCS")
             || value.equals("MBCS")
             || value.equals("EBCDIC_STATEFUL")){
                 // right, do nothing.
            } else {
                throw new Unknown_UCM_Header("Unknown <uconv_class>");
            }
			uconv_class = value;
            return;
		}
        
        if (key.equals("<mb_cur_max>")){
            if (value.length() == 1){
                char v = value.charAt(0);
                if ('1' <= v && v <= '4'){
                    mb_cur_max = v - '0';
                    return;
                }
            }
            throw new Unknown_UCM_Header("Unknown <mb_cur_max>");
        }
        
        if (key.equals("<mb_cur_min>")){
            if (value.length() == 1){
                char v = value.charAt(0);
                if ('1' <= v && v <= '4'){
                    mb_cur_min = v - '0';
                    return;
                }
            }
            throw new Unknown_UCM_Header("Unknown <mb_cur_min>");
        }
        
	} // parseOneLine
}
