/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.xml2ucm;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author YANG RongWei
 *
 * This clas just in purpose to wrap a group of functions.
 */
final class GenUcmComments {
    private static final String eol = System.getProperty("line.separator", "\r\n");
    StringBuffer comments;
    
    GenUcmComments(){
        comments = new StringBuffer();
    }
    
    void parse(Element charMap) {
        comments.append("# File created by Xml2Ucm.java from " + Xml2Ucm.getXmlFileName() + eol);
        comments.append("# characterMapping version:  " + charMap.getAttribute("version") + eol);
        xmlHistory2Comment(charMap);
    }
    
    void dump(PrintWriter out){
        out.print(comments.toString());
    }
   
    
    private void xmlHistory2Comment(Element charMap) {
        NodeList nodeList = charMap.getElementsByTagName("history");
        if (nodeList.getLength() == 0) {
            return;
        }
        
        Element history = (Element) nodeList.item(0);
 
        comments.append("# <history>" + eol);
        
        getXmlTextNode(history, "#  ");
        
        nodeList = history.getElementsByTagName("modified");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element modified = (Element) nodeList.item(i);
            comments.append("#  <modified version=\"");
            comments.append(modified.getAttribute("version"));
            comments.append("\" date=\"");
            comments.append(modified.getAttribute("date"));
            comments.append("\">" + eol);
            
            getXmlTextNode(modified, "#   ");
            
            comments.append("#  </modified>" + eol);
        }
        
        comments.append("# </history>" + eol);
    }
    
    private void getXmlTextNode(Element node, String prefix) {
        Node textNode = node.getFirstChild();
        for (; textNode != null && textNode.getNodeName().compareTo("#text") == 0; textNode = textNode.getNextSibling()) {
            String value = textNode.getNodeValue().trim();
            StringTokenizer valueTokens = new StringTokenizer(value, "\n\r\f");
            while (valueTokens.hasMoreTokens()) {
                comments.append(prefix + valueTokens.nextToken().trim() + eol);
            }
        }
    }
}
