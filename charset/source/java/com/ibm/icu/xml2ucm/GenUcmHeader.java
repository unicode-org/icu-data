/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.xml2ucm;
import java.io.PrintWriter;
import java.util.Locale;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.ibm.icu.xml2ucm.MbcsStateMachine;
import com.ibm.icu.xml2ucm.MbcsStateMachine.StateMachineHasLoop;

/**
 * @author YANG RongWei
 *
 * This clas just in purpose to wrap a group of functions.
 */
final class GenUcmHeader {
	static class Unknown_validity extends Exception {
		Unknown_validity(String msg) {
			super(msg);
		}
	}
	////
	String code_set_name;
	String subchar = "";
	String subchar1 = "";
	int mb_cur_min ;
	int mb_cur_max ;
	String icu_state = "";
	String uconv_class = "";
	String icu_charsetFamily = "";

	GenUcmHeader(String ucmFileName) {
		code_set_name = ucmFileName;
	}

	void parse1(Element charMap)
		throws MbcsStateMachine.StateMachineHasLoop, Unknown_validity {
            
		Element assi = (Element) charMap.getElementsByTagName("assignments").item(0);

		String t;
		t = assi.getAttribute("sub");
		subchar = getByteSequence(t);

		t = assi.getAttribute("sub1");
		subchar1 = getByteSequence(t);

		NodeList siso = charMap.getElementsByTagName("stateful_siso");
        
		switch (siso.getLength()) {
			case 0 :
				parseSimplyStateMachine(charMap);
				break;
			case 1 :
				parseSiso(siso);
				break;
			default :
				throw new Unknown_validity("Expect at most one <stateful_siso>, but there are " + siso.getLength());
		}

	}

	void dump(PrintWriter out) {
		out.println();
		out.println("<code_set_name>      \"" + code_set_name + "\"");
		out.println("<char_name_mask>     \"AXXXX\"");
		if (!subchar.equals(""))	
            out.println("<subchar>            " + subchar);
		if (!subchar1.equals(""))	
            out.println("<subchar1>           " + subchar1);
        if (!icu_charsetFamily.equals(""))   
            out.println("<icu:charsetFamily>  \"" + icu_charsetFamily  + "\"");    
        out.println("<uconv_class>        \"" + uconv_class        + "\"");    
		out.println("<mb_cur_min>         "   + mb_cur_min);
		out.println("<mb_cur_max>         "   + mb_cur_max);
        out.println(icu_state);
	}

	private void parseSiso(NodeList siso)
		throws StateMachineHasLoop, Unknown_validity {
		NodeList nl = ((Element) siso.item(0)).getElementsByTagName("validity");
		if (nl.getLength() != 2) {
			throw new Unknown_validity(
				"Expect two validities, but there are : " + nl.getLength());
		}


		Element v1 = (Element) nl.item(0);
		Element v2 = (Element) nl.item(1);
        MbcsStateMachine s1 = new MbcsStateMachine(v1);
        MbcsStateMachine s2 = new MbcsStateMachine(v2);
        
		if (s1.getMaxBytes() > 1) {
            MbcsStateMachine s;
            s = s1;
            s1 = s2;
            s2 = s;
		}
        // assert s1.getMaxBytes() == 1

		mb_cur_min = s1.getMinBytes();
		mb_cur_max = s2.getMaxBytes();

		icu_state = s1.listAs_siso_1_byte();
        icu_state += s2.listAs_siso_2_byte();
	}

	private void parseSimplyStateMachine(Element charMap) throws StateMachineHasLoop, Unknown_validity {
        NodeList nl = charMap.getElementsByTagName("validity");
        if (nl.getLength() != 1) {
            throw new Unknown_validity( "Expect one validity, but there are : " + nl.getLength());
        }
        Element validiy = (Element) nl.item(0);
		MbcsStateMachine s = new MbcsStateMachine(validiy);
		mb_cur_min = s.getMinBytes();
		mb_cur_max = s.getMaxBytes();
		icu_state = s.listAs_simple_SateMachine();
	}

	void parse2(String charsetFamily){
		String t;
		if (mb_cur_max == 1 && mb_cur_min == 1) {
			t = "SBCS";
		} else if (mb_cur_max == 2 && mb_cur_min == 2) {
			t = "DBCS";
		} else if (mb_cur_max == 2 && mb_cur_min == 1) {
			if (charsetFamily.equals("EBCDIC")) {
				t = "EBCDIC_STATEFUL";
			} else {
				t = "MBCS";
			}
		} else {
			t = "MBCS";
		}

        icu_charsetFamily = charsetFamily;
		uconv_class       = t;
	}
    
    private String getByteSequence(String source) {
            String result = source.trim();
            result = result.toUpperCase(Locale.US);
            if (!result.equals("")) {
                result = result.replaceAll("\\s+", "\\\\x");
                result = "\\x" + result;
            }
            return result;
    }    
}
