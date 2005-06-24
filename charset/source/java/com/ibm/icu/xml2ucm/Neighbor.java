/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.xml2ucm;

import java.util.Locale;

import org.w3c.dom.Element;



class Neighbor implements Comparable {
	private final Vertex vertex;
    String selfStartValue;
    String selfEndValue;
    String tag;
    
    // Neighbor's information in various format;
    String neighborXmlName;
    String neighborUcmNumber;
    Vertex neighbor;

    // enable Neighbor to be sortable
    public int compareTo(Object o) {
        Neighbor s = (Neighbor)o;
        return this.selfStartValue.compareTo(s.selfStartValue);
    }
    
    Neighbor(Vertex vertex, Element e) {
        selfStartValue = e.getAttribute("s");
		this.vertex = vertex;
        selfEndValue = e.getAttribute("e");
        neighborXmlName = e.getAttribute("next");
        tag = e.getAttribute("max");
    }

    String listInUcmFormat(int initStateNum){
        String entry = selfStartValue;
        if (!selfEndValue.equals("") && !selfEndValue.equals(selfStartValue)){
            entry += '-' + selfEndValue;
        }
        
        // TODO: kick of the use of MbcsStateMachine.startNumber 
        if (neighborXmlName.equals("VALID")){
            if (initStateNum != 0){
                entry += ":" + Integer.toHexString(initStateNum);
            }
            if (tag.trim().toUpperCase(Locale.US).equals("10FFFF")){
                entry += ".p";
            }
            // do nothing 
        } else if (neighborXmlName.equals("INVALID")){
            if (initStateNum != 0){
                entry += ":" + Integer.toHexString(initStateNum);
            }
            entry += ".i";
        } else if (neighborXmlName.equals("UNASSIGNED")){
            if (initStateNum != 0){
                entry += ":" + Integer.toHexString(initStateNum);
            }
            entry += ".u";
        } else {
            if (initStateNum != 0){
                int t = Integer.parseInt(neighborUcmNumber, 16);
                t += initStateNum;  // notice point
                entry += ":" + Integer.toHexString(t);
            } else {
                entry += ':' + neighborUcmNumber;
            }
        }
        return entry;
    } // dumpInUcmFormat()
} // class Neighbor