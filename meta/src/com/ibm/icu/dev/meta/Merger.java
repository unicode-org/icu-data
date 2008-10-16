// Copyright (C) 2008, International Business Machines Corporation and others.  All Rights Reserved.

package com.ibm.icu.dev.meta;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import java.io.*;
import org.unicode.cldr.util.*;
import org.unicode.cldr.icu.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.HashMap;


// DOM imports
import org.apache.xpath.XPathAPI;
import org.apache.xalan.serialize.DOMSerializer;
import org.apache.xalan.serialize.Serializer;
import org.apache.xalan.serialize.SerializerFactory;
import org.apache.xalan.templates.OutputProperties;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.unicode.cldr.icu.LDMLConstants;

// Needed JAXP classes
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

// SAX2 imports
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


public class Merger {
    public static void main(String args[]) {
        Document full = null;
        String sources = "";
        for(String s : args) {
            sources = sources + " " + s;
            System.err.println("# Read: "+s);
            Document next = LDMLUtilities.parse(s, false);
            if(full == null) {
                System.err.println("# Initial: "+s);
                full = next;
            } else {
                System.err.println("# Merge: "+s);
                StringBuffer xpath = new StringBuffer();
                mergeXMLDocuments(full, next, xpath, "Something", "DotDotDot", false, false);
            }
        }
        System.err.println("# Write");
        try {
             java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                     System.out);
             LDMLUtilities.printDOMTree(full, new PrintWriter(writer),"\n<!-- This file was generated from: "+sources+" -->\n<!DOCTYPE icuInfo SYSTEM \"http://icu-project.org/dtd/icumeta.dtd\">\n",null); //
             writer.flush();
        } catch (IOException e) {
             //throw the exceptionaway .. this is for debugging
        }
    }
    
    
    
        // from LDMLUtilities
    /**
     *   Resolved Data File
     *   <p>To produce fully resolved locale data file from CLDR for a locale ID L, you start with root, and 
     *   replace/add items from the child locales until you get down to L. More formally, this can be 
     *   expressed as the following procedure.</p>
     *   <ol>
     *     <li>Let Result be an empty LDML file.</li>
     *   
     *     <li>For each Li in the locale chain for L<ol>
     *       <li>For each element pair P in the LDML file for Li:<ol>
     *         <li>If Result has an element pair Q with an equivalent element chain, remove Q.</li>
     *         <li>Add P to Result.</li>
     *       </ol>
     *       </li>
     *     </ol>
     *   
     *     </li>
     *   </ol>
     *   <p>Note: when adding an element pair to a result, it has to go in the right order for it to be valid 
     *  according to the DTD.</p>
     *
     * @param source
     * @param override
     * @return the merged document
     */
    public static Node mergeXMLDocuments(Document source, Node override, StringBuffer xpath, 
                                          String thisName, String sourceDir, boolean ignoreDraft,
                                          boolean ignoreVersion){
        if(source==null){
            return override;
        }
        if(xpath.length()==0){
            xpath.append("/");
        }
        
//        boolean gotcha = false;
//        String oldx = new String(xpath);
//        if(override.getNodeName().equals("week")) {
//            gotcha = true;
//            System.out.println("SRC: " + getNode(source, xpath.toString()).toString());
//            System.out.println("OVR: " + override.toString());
//        }
        
        // we know that every child xml file either adds or 
        // overrides the elements in parent
        // so we traverse the child, at every node check if 
        // if the node is present in the source,  
        // if (present)
        //    recurse to replace any nodes that need to be overridded
        // else
        //    import the node into source
        Node child = override.getFirstChild();
        while( child!=null){
            // we are only concerned with element nodes
            if(child.getNodeType()!=Node.ELEMENT_NODE){
                child = child.getNextSibling();
                continue;
            }   
            String childName = child.getNodeName();

            int savedLength=xpath.length();
            xpath.append("/");
            xpath.append(childName);
            appendXPathAttribute(child,xpath, false, false);
            Node nodeInSource = null;
            
            if(childName.indexOf(":")>-1){ 
                nodeInSource = getNode(source, xpath.toString(), child);
            }else{
                nodeInSource =  getNode(source, xpath.toString());
            }
            
            Node parentNodeInSource = null;
            if(nodeInSource==null){
                // the child xml has a new node
                // that should be added to parent
                String parentXpath = xpath.substring(0, savedLength);

                if(childName.indexOf(":")>-1){ 
                    parentNodeInSource = getNode(source, parentXpath, child);
                }else{
                    parentNodeInSource =  getNode(source,parentXpath);
                }
                if(parentNodeInSource==null){
                    throw new RuntimeException("Internal Error");
                }
                
                Node childToImport = source.importNode(child,true);
                parentNodeInSource.appendChild(childToImport);
            }else if( childName.equals(LDMLConstants.IDENTITY)){
                if(!ignoreVersion){
                    // replace the source doc
                    // none of the elements under collations are inherited
                    // only the node as a whole!!
                    parentNodeInSource = nodeInSource.getParentNode();
                    Node childToImport = source.importNode(child,true);
                    parentNodeInSource.replaceChild(childToImport, nodeInSource);
                }
            }else if( childName.equals(LDMLConstants.COLLATION)){
                // replace the source doc
                // none of the elements under collations are inherited
                // only the node as a whole!!
                parentNodeInSource = nodeInSource.getParentNode();
                Node childToImport = source.importNode(child,true);
                parentNodeInSource.replaceChild(childToImport, nodeInSource);
                //override the validSubLocales attribute
                String val = LDMLUtilities.getAttributeValue(child.getParentNode(), LDMLConstants.VALID_SUBLOCALE);
                NamedNodeMap map = parentNodeInSource.getAttributes();
                Node vs = map.getNamedItem(LDMLConstants.VALID_SUBLOCALE);
                vs.setNodeValue(val);
            }else{
                boolean childElementNodes = areChildrenElementNodes(child);
                boolean sourceElementNodes = areChildrenElementNodes(nodeInSource);
//                System.err.println(childName + ":" + childElementNodes + "/" + sourceElementNodes);
                if(childElementNodes &&  sourceElementNodes){
                    //recurse to pickup any children!
                    mergeXMLDocuments(source, child, xpath, thisName, sourceDir, ignoreDraft, ignoreVersion);
                }else{
                    // we have reached a leaf node now get the 
                    // replace to the source doc 
                    parentNodeInSource = nodeInSource.getParentNode();
                    Node childToImport = source.importNode(child,true);
                    parentNodeInSource.replaceChild(childToImport, nodeInSource);
                }
            }
            xpath.delete(savedLength,xpath.length());
            child= child.getNextSibling();
        }
//        if(gotcha==true) {
//            System.out.println("Final: " + getNode(source, oldx).toString());
//        }
        return source;
    }    

    /**
     * Appends the attribute values that make differentiate 2 siblings
     * in LDML
     * @param node
     * @param xpath
     */
    public static final void appendXPathAttribute(Node node, StringBuffer xpath){
        appendXPathAttribute(node,xpath,false,false);
    }
    public static void appendXPathAttribute(Node node, StringBuffer xpath, boolean ignoreAlt, boolean ignoreDraft){
        boolean terminate = false;
        String val = getAttributeValue(node, LDMLConstants.TYPE);
        String and =  "][";//" and ";
        boolean isStart = true;
        String name = node.getNodeName();
        if(val!=null && !name.equals(LDMLConstants.DEFAULT)&& !name.equals(LDMLConstants.MS)){
            if(!(val.equals("standard")&& name.equals(LDMLConstants.PATTERN))){
               
                if(isStart){
                    xpath.append("[");
                    isStart=false;
                }
                xpath.append("@type='");
                xpath.append(val);
                xpath.append("'");
                terminate = true;
            }
        }
//        if(!ignoreAlt) {
//            val = getAttributeValue(node, LDMLConstants.ALT);
//            if(val!=null){
//                if(isStart){
//                    xpath.append("[");
//                    isStart=false;
//                }else{
//                    xpath.append(and);
//                }
//                xpath.append("@alt='");
//                xpath.append(val);
//                xpath.append("'");
//                terminate = true;
//            }
//            
//        }
//        
//        if(!ignoreDraft) {
//            val = getAttributeValue(node, LDMLConstants.DRAFT);
//            if(val!=null && !name.equals(LDMLConstants.LDML)){
//                if(isStart){
//                    xpath.append("[");
//                    isStart=false;
//                }else{
//                    xpath.append(and);
//                }
//                xpath.append("@draft='");
//                xpath.append(val);
//                xpath.append("'");
//                terminate = true;
//            }
//            
//        }
//       
        String what=null;
        
        
        what = "version";
        val = getAttributeValue(node, what);
        if(val!=null){
            if(isStart){
                xpath.append("[");
                isStart=false;
            }else{
                xpath.append(and);
            }
            xpath.append("@"+what+"='");
            xpath.append(val);
            xpath.append("'");
            terminate = true;
        }

        
        what = "owner";
        val = getAttributeValue(node, what);
        if(val!=null){
            if(isStart){
                xpath.append("[");
                isStart=false;
            }else{
                xpath.append(and);
            }
            xpath.append("@"+what+"='");
            xpath.append(val);
            xpath.append("'");
            terminate = true;
        }
        
        what = "name";
        val = getAttributeValue(node, what);
        if(val!=null){
            if(isStart){
                xpath.append("[");
                isStart=false;
            }else{
                xpath.append(and);
            }
            xpath.append("@"+what+"='");
            xpath.append(val);
            xpath.append("'");
            terminate = true;
        }
//        val = getAttributeValue(node, LDMLConstants.REGISTRY);
//        if(val!=null){
//            if(isStart){
//                xpath.append("[");
//                isStart=false;
//            }else{
//                xpath.append(and);
//            }
//            xpath.append("@registry='");
//            xpath.append(val);
//            xpath.append("'");
//            terminate = true;
//        }
//        val = getAttributeValue(node, LDMLConstants.ID);
//        if(val!=null){
//            if(isStart){
//                xpath.append("[");
//                isStart=false;
//            }else{
//                xpath.append(and);
//            }
//            xpath.append("@id='");
//            xpath.append(val);
//            xpath.append("'");
//            terminate = true;
//        }
        if(terminate){
            xpath.append("]");
        }
    }

    /**
     * Ascertains if the children of the given node are element
     * nodes.
     * @param node
     * @return
     */
    public static boolean areChildrenElementNodes(Node node){
        NodeList list = node.getChildNodes();
        for(int i=0;i<list.getLength();i++){
            if(list.item(i).getNodeType()==Node.ELEMENT_NODE){
                return true;
            }
        }
        return false;  
    }
    public static Node[] getNodeListAsArray( Node doc, String xpath){
        try{
            NodeList list = XPathAPI.selectNodeList(doc, xpath);
            int length = list.getLength();
            if(length>0){
                Node[] array = new Node[length];
                for(int i=0; i<length; i++){
                    array[i] = list.item(i);
                }
                return array;
            }
            return null;
        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        } 
    }

    private static Object[] getChildNodeListAsArray( Node parent, boolean exceptAlias){

        NodeList list = parent.getChildNodes();
        int length = list.getLength();
        
        ArrayList al = new ArrayList();
        for(int i=0; i<length; i++){
            Node item  = list.item(i);
            if(item.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            if(exceptAlias && item.getNodeName().equals(LDMLConstants.ALIAS)){
               continue; 
            }
            al.add(item);
        }
        return al.toArray();
        
    }
    public static Node[] toNodeArray( NodeList list){
        int length = list.getLength();
        if(length>0){
            Node[] array = new Node[length];
            for(int i=0; i<length; i++){
                array[i] = list.item(i);
            }
            return array;
        }
        return null;
    }
    public static Node[] getElementsByTagName(Document doc, String tagName){
        try{
            NodeList list = doc.getElementsByTagName(tagName);
            int length = list.getLength();
            if(length>0){
                Node[] array = new Node[length];
                for(int i=0; i<length; i++){
                    array[i] = list.item(i);
                }
                return array;
            }
            return null;
        }catch(Exception ex){
            throw new RuntimeException(ex.getMessage());
        } 
    }
    
    /**
     * Fetches the list of nodes that match the given xpath
     * @param doc
     * @param xpath
     * @return
     */
    public static NodeList getNodeList( Document doc, String xpath){
        try{
            return XPathAPI.selectNodeList(doc, xpath);

        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        }   
    }
    
    public static final boolean isAlternate(Node node){
        NamedNodeMap attributes = node.getAttributes();
        Node attr = attributes.getNamedItem(LDMLConstants.ALT);
        if(attr!=null){
            return true;
        }
        return false;
    }

    private static final Node getNonAltNode(NodeList list /*, StringBuffer xpath*/){
        // A nonalt node is one which .. does not have alternate
        // attribute set
        Node node =null;
        for(int i =0; i<list.getLength(); i++){
            node = list.item(i);
            if(/*!isDraft(node, xpath)&& */!isAlternate(node)){
                return node;
            }
        }
        return null;
    }
    
    private static final Node getNonAltNodeIfPossible(NodeList list)
    {
        // A nonalt node is one which .. does not have alternate
        // attribute set
        Node node =null;
        for(int i =0; i<list.getLength(); i++)
        {
            node = list.item(i);
            if(/*!isDraft(node, xpath)&& */!isAlternate(node))
            {
                return node;
            }
        }
        if (list.getLength()>0)
            return list.item(0);   //if all have alt=.... then return the first one
        return null;
    }
        
    public static Node getNonAltNodeLike(Node parent, Node child){
        StringBuffer childXpath = new StringBuffer(child.getNodeName());
        appendXPathAttribute(child,childXpath,true/*ignore alt*/,true/*ignore draft*/);
        String childXPathString = childXpath.toString();
        for(Node other=parent.getFirstChild(); other!=null; other=other.getNextSibling() ){
            if((other.getNodeType()!=Node.ELEMENT_NODE)  || (other==child)) {
                continue;
            }
            StringBuffer otherXpath = new StringBuffer(other.getNodeName());
            appendXPathAttribute(other,otherXpath);
          //  System.out.println("Compare: " + childXpath + " to " + otherXpath);
            if(childXPathString.equals(otherXpath.toString())) {
              //  System.out.println("Match!");
                return other;
            }
        }
        return null;
    }


    /**
     * Fetches the node from the document that matches the given xpath.
     * The context namespace node is required if the xpath contains 
     * namespace elments
     * @param doc
     * @param xpath
     * @param namespaceNode
     * @return
     */
    public static Node getNode(Document doc, String xpath, Node namespaceNode){
        try{
            NodeList nl = XPathAPI.selectNodeList(doc, xpath, namespaceNode);
            int len = nl.getLength();
            //TODO watch for attribute "alt"
            if(len>1){
              throw new IllegalArgumentException("The XPATH returned more than 1 node!. Check XPATH: "+xpath);   
            }
            if(len==0){
                return null;
            }
            return nl.item(0);

        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        }
    }
    public static Node getNode(Node context, String resToFetch, Node namespaceNode){
        try{
            NodeList nl = XPathAPI.selectNodeList(context, "./"+resToFetch, namespaceNode);
            int len = nl.getLength();
            //TODO watch for attribute "alt"
            if(len>1){
              throw new IllegalArgumentException("The XPATH returned more than 1 node!. Check XPATH: "+resToFetch);   
            }
            if(len==0){
                return null;
            }
            return nl.item(0);

        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        }
    }
    /**
     * Fetches the node from the document which matches the xpath
     * @param node
     * @param xpath
     * @return
     */
    public static Node getNode(Node node, String xpath){
        try{
            NodeList nl = XPathAPI.selectNodeList(node, xpath);
            int len = nl.getLength();
            //TODO watch for attribute "alt"
            if(len>1){
                //PN Node best = getNonAltNode(nl);
                Node best = getNonAltNodeIfPossible(nl); //PN
                if(best != null) {
                    //System.err.println("Chose best node from " + xpath);
                    return best;
                }
                /* else complain */
                String all = ""; 
                int i;
                for(i=0;i<len;i++) {
                    all = all + ", " + nl.item(i);
                }
                throw new IllegalArgumentException("The XPATH returned more than 1 node!. Check XPATH: "+xpath + " = " + all);   
            }
            if(len==0){
                return null;
            }
            return nl.item(0);

        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        }
    }
    public static Node getNode(Node node, String xpath, boolean preferDraft, boolean preferAlt){
        try{
            NodeList nl = XPathAPI.selectNodeList(node, xpath);
            return getNode(nl, xpath, preferDraft, preferAlt);

        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        }
    }
    private static Node getVettedNode(NodeList list, StringBuffer xpath, boolean ignoreDraft){
        // A vetted node is one which is not draft and does not have alternate
        // attribute set
        Node node =null;
        for(int i =0; i<list.getLength(); i++){
            node = list.item(i);
            if(isDraft(node, xpath) && !ignoreDraft){
                continue;
            }
            if(isAlternate(node)){
                continue;
            }
            return node;
        }
        return null;
    }
    public static Node getVettedNode(Document fullyResolvedDoc, Node parent, String childName, StringBuffer xpath, boolean ignoreDraft){
        NodeList list = getNodeList(parent, childName, fullyResolvedDoc, xpath.toString());
        int oldLength=xpath.length();
        Node ret = null;

        if(list != null && list.getLength()>0){
            xpath.append("/");
            xpath.append(childName);
            ret = getVettedNode(list,xpath, ignoreDraft);
        }
        xpath.setLength(oldLength);
        return ret;
    }
    public static Node getNode(NodeList nl, String xpath, boolean preferDraft, boolean preferAlt){
        int len = nl.getLength();
        //TODO watch for attribute "alt"
        if(len>1){
            Node best = null;
            for(int i=0; i<len;i++){
                Node current = nl.item(i);
                if(!preferDraft && ! preferAlt){
                    if(!isNodeDraft(current) && ! isAlternate(current)){
                        best = current;
                        break;
                    }
                    continue;
                }else if(preferDraft && !preferAlt){
                    if(isNodeDraft(current) && ! isAlternate(current)){
                        best = current;
                        break;
                    }
                    continue;
                }else if(!preferDraft && preferAlt){
                    if(!isNodeDraft(current) && isAlternate(current)){
                        best = current;
                        break;
                    }
                    continue;
                }else{
                    if(isNodeDraft(current) || isAlternate(current)){
                        best = current;
                        break;
                    }
                    continue;
                }
            }
            if(best==null && preferDraft==true){
                best = getVettedNode(nl, new StringBuffer(xpath), false);
            }
            if(best != null){
                return best;
            }
            /* else complain */
            String all = ""; 
            int i;
            for(i=0;i<len;i++) {
                all = all + ", " + nl.item(i);
            }
            throw new IllegalArgumentException("The XPATH returned more than 1 node!. Check XPATH: "+xpath + " = " + all);   
        }
        if(len==0){
            return null;
        }
        return nl.item(0);

    }
    /**
     * 
     * @param context
     * @param resToFetch
     * @param fullyResolved
     * @param xpath
     * @return
     */
    public static Node getNode(Node context, String resToFetch, Document fullyResolved, String xpath){
        String ctx = "./"+ resToFetch;
        Node node = getNode(context, ctx);
        if(node == null && fullyResolved!=null){
            // try from fully resolved
            String path = xpath+"/"+resToFetch;
            node = getNode(fullyResolved, path);
        }
        return node;
    }
    /**
     * 
     * @param context
     * @param resToFetch
     * @return
     */
    public static NodeList getChildNodes(Node context, String resToFetch){
        String ctx = "./"+ resToFetch;
        NodeList list = getNodeList(context, ctx);
        return list;
    }
    /**
     * Fetches the node from the document that matches the given xpath.
     * The context namespace node is required if the xpath contains 
     * namespace elments
     * @param doc
     * @param xpath
     * @param namespaceNode
     * @return
     */
    public static NodeList getNodeList(Document doc, String xpath, Node namespaceNode){
        try{
            NodeList nl = XPathAPI.selectNodeList(doc, xpath, namespaceNode);
            if(nl.getLength()==0){
                return null;
            }
            return nl;

        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        }
    }
    /**
     * Fetches the node from the document which matches the xpath
     * @param node
     * @param xpath
     * @return
     */
    public static NodeList getNodeList(Node node, String xpath){
        try{
            NodeList nl = XPathAPI.selectNodeList(node, xpath);
            int len = nl.getLength();
            if(len==0){
                return null;
            }
            return nl;
        }catch(TransformerException ex){
            throw new RuntimeException(ex.getMessage());
        }
    }

    /**
     * Fetches node list from the children of the context node.
     * @param context
     * @param resToFetch
     * @param fullyResolved
     * @param xpath
     * @return
     */
    public static NodeList getNodeList(Node context, String resToFetch, Document fullyResolved, String xpath){
        String ctx = "./"+ resToFetch;
        NodeList list = getNodeList(context, ctx);
        if((list == null || list.getLength()>0) && fullyResolved!=null){
            // try from fully resolved
            String path = xpath+"/"+resToFetch;
            list = getNodeList(fullyResolved, path);
        }
        return list;
    }

    /**
     * Decide if the node is text, and so must be handled specially 
     * @param n
     * @return
     */
    private static boolean isTextNode(Node n) {
      if (n == null)
        return false;
      short nodeType = n.getNodeType();
      return nodeType == Node.CDATA_SECTION_NODE || nodeType == Node.TEXT_NODE;
    }   
    public static Node getAttributeNode(Node sNode, String attribName){
        NamedNodeMap attrs = sNode.getAttributes();
        if(attrs!=null){
           return attrs.getNamedItem(attribName);
        }
        return null;
    }
    /**
     * Utility method to fetch the attribute value from the given 
     * element node
     * @param sNode
     * @param attribName
     * @return
     */
    public static String getAttributeValue(Node sNode, String attribName){
        String value=null;
        NamedNodeMap attrs = sNode.getAttributes();
        if(attrs!=null){
            Node attr = attrs.getNamedItem(attribName);
            if(attr!=null){
                value = attr.getNodeValue();
            }
        }
        return value;
    }
    /**
     * Utility method to set the attribute value on the given 
     * element node
     * @param sNode
     * @param attribName
     * @param val
     */
    public static void setAttributeValue(Node sNode, String attribName, String val){

        Node attr = sNode.getAttributes().getNamedItem(attribName);
        if(attr!=null){
            attr.setNodeValue(val);
        } else {
            attr = sNode.getOwnerDocument().createAttribute(attribName);
            attr.setNodeValue(val);
            sNode.getAttributes().setNamedItem(attr);
        }
    }
    /**
     * Utility method to fetch the value of the element node
     * @param node
     * @return
     */
    public static String getNodeValue(Node node){
        for(Node child=node.getFirstChild(); child!=null; child=child.getNextSibling() ){
            if(child.getNodeType()==Node.TEXT_NODE){
                return child.getNodeValue();
            }
        }
        return null;
    }

    public static boolean isNodeDraft(Object o) { return false; }
    public static boolean isDraft(Object o, Object p) { return false; }


}
