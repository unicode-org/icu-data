/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.xml2ucm;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.ibm.icu.utility.ByteSequence;
import com.ibm.icu.utility.UcpSequence;
import com.ibm.icu.utility.ByteSequence.Illformed_HexSeq;
import com.ibm.icu.utility.UcpSequence.Illegal_Unicode_CodePoint;

/**
 * @author YANG RongWei
 *
 * This clas just in purpose to wrap a group of functions.
 */
final class GenUcmCharmap {
    static class Unknown_Assignment extends Exception{
        Unknown_Assignment(){
            super();
        }
        Unknown_Assignment(String msg){
            super(msg);
        }

        Unknown_Assignment(Exception cause){
            super(cause);
        }
    }
    ///////////
    private boolean detected;
    private String sub1;
    String charsetFamily = "";
    private StringWriter sw;
    private PrintWriter out;
    
    void parse(Element charMap) throws Unknown_Assignment{
        Element xml_assi = (Element) charMap.getElementsByTagName("assignments").item(0);
        detected = false;
        sw = new StringWriter();
        out = new PrintWriter(sw);
        
        out.println();
        out.println("CHARMAP");
        try {
			Assign2Charmap(xml_assi);
		} catch (Illformed_HexSeq e) {
            throw new Unknown_Assignment(e);
		}catch (Illegal_Unicode_CodePoint e) {
            throw new Unknown_Assignment(e);
		}
        out.println("END CHARMAP");
    }
    
    void dump(PrintWriter out){
        out.print(this.sw.toString());
    }


    private  void Assign2Charmap(Element xml_assi) throws Unknown_Assignment, Illformed_HexSeq, Illegal_Unicode_CodePoint{
        Node node = xml_assi.getFirstChild();
        for (;node != null; node = node.getNextSibling()) {
            String nodeName = node.getNodeName();
            nodeName = nodeName.toLowerCase(Locale.US);
            if (nodeName.equals("a")){
                out.print  (getUniSeq(node, "u"));
                out.print  (getByteSeq(node));
                out.println(" |0");
                if (!detected) {
                    detectCharsetFamily(node);
                }
            } else if (nodeName.equals("fbu")){
                out.print  (getUniSeq(node, "u"));
                out.print  (getByteSeq(node));
                out.println(" |3");
            } else if (nodeName.equals("sub1")){
                out.print  (getUniSeq(node, "u"));
                out.print  (getSub1(xml_assi));
                out.println(" |2");
            } else if (nodeName.equals("fub")){
                Element temp = (Element) node;
                if (temp.hasAttribute("u")){
                    out.print  (getUniSeq(node, "u"));
                    out.print  (getByteSeq(node));
                    out.println(" |1");
                } 
                if (temp.hasAttribute("ru")){
                    out.print(getUniSeq(node, "ru"));
                    out.print(getByteSeq(node));
                    out.println(" |3");
                }
            } else if (nodeName.equals("range")){
                throw new Unknown_Assignment("Unable to parse 'range' now.");
//            } else{
//                throw new Unknown_Assignment(nodeName);
            }
        }
    }

	private String getSub1(Element xml_assi) throws Unknown_Assignment {
        if (sub1 != null) {
            return sub1;
        }
		
		try{
            String t;
		    ByteSequence bs;
		    t = xml_assi.getAttribute("sub1");
		    bs = ByteSequence.fromHexSeq(t);
		    if ( bs.length() > 1) {
		        throw new Unknown_Assignment("'sub1' is longer than one, it is " + bs.length());
		    } else if (bs.length() == 1){
		        sub1 = bs.toEscSeq();
		    }  else { // (bs.length() == 0)
		        t = xml_assi.getAttribute("sub");
		        bs = ByteSequence.fromHexSeq(t);
		        if (bs.length() == 1){
		            sub1 = bs.toEscSeq();
		        } else if (bs.length() == 0 ) {
		            bs = ByteSequence.fromHexSeq("1A");    // default sub according UTR22
		            sub1 = bs.toEscSeq();
		        } else {
		            throw new Unknown_Assignment("Cannot find a useable 'sub1'");
		        }
		    }
		} catch ( Illformed_HexSeq e){
		    throw new Unknown_Assignment (e);
		}
		return sub1;
	}

    private String getUniSeq(Node node, String attributeName) throws Illegal_Unicode_CodePoint{
        String t = ((Element) node).getAttribute(attributeName);
        UcpSequence us = UcpSequence.fromHexSeq_xml(t);
        t = us.toHexSeq_ucm();
        return t;
    }

    private String getByteSeq(Node node) throws Illformed_HexSeq{
        String t = ((Element) node).getAttribute("b");
        ByteSequence bs = ByteSequence.fromHexSeq(t);
        t = bs.toEscSeq();
        return t;
    }
    
    private  void detectCharsetFamily(Node node){

        String u = ((Element) node).getAttribute("u").trim();
        if (u.matches("\\S+\\s+\\S+.*")) { // more than one Unicode code point
            return;
        }
        
        int uValue = Integer.parseInt(u,16);
        if (uValue == 0x41 || uValue == 0xa){
            // interested values, continue
        } else {
            return;
        }

        String b = ((Element) node).getAttribute("b").trim();
        if (b.matches("\\S+\\s+\\S+.*")) { // more than one byte
            return;
        }
        
        int bValue = Integer.parseInt(b,16);
        
        if ((uValue == 0x41 && bValue == 0x41) ||
            (uValue == 0xa  && bValue == 0xa)){
            charsetFamily = "ASCII";
            detected = true;
        } else if ((uValue == 0x41 && bValue == 0xc1) ||
                   (uValue == 0xa  && bValue == 0x25) ||
                   (uValue == 0xa  && bValue == 0x15)){
            charsetFamily = "EBCDIC";
            detected = true;
        }  
    }
}
