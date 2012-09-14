// Copyright (C) 2008-2012, International Business Machines Corporation and others.  All Rights Reserved.

package com.ibm.icu.dev.meta;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Calendar;

import javax.xml.transform.TransformerException;

import org.apache.xpath.XPathAPI;
import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.util.LDMLUtilities;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ibm.icu.dev.util.ElapsedTimer;


public class Merger {
    private static Document full = null;
    private static String sources = "";
    private static boolean verbose = false;
    private static boolean copyright= true;
    private static String outfile = null;
    
    private static void addSource(String s) {
        sources = sources + " " + s;
        ElapsedTimer r = new ElapsedTimer();
        //if(verbose) { System.err.println("" ... "); System.err.flush(); }
        Document next = LDMLUtilities.parse(s, false);
        if(full == null) {
            if(verbose) System.err.println("# Read: "+s+" .. " + r + "(1st)");
            full = next;
        } else {
            //if(verbose) System.err.println("# .. "+ r);
            ElapsedTimer r2 = new ElapsedTimer();
            StringBuffer xpath = new StringBuffer();
            if(verbose) { System.err.println("# Read: "+s+" .. " + r + ", Merge .. ");  System.err.flush(); }
            mergeXMLDocuments(full, next, xpath, "Something", "DotDotDot", false, false);
            if(verbose) System.err.println("# .. merged - "+ r2);
        }
    }
    
    public static void main(String args[]) throws IOException {
        for(String s : args) {
            if(s.equals("-v")) {
                verbose = true;
                continue;
            } else if(s.equals("-c")) {
                copyright = false;
                continue;
            } else if(s.startsWith("-o")) {
                if(!s.startsWith("-o:")) {
                    System.err.println("# Err: usage:   -o:outfile.xml ");
                    return;
                }
                outfile = s.substring(3);
                if(verbose) System.err.println("# Outfile: "+outfile);
                continue;
            } else if(s.endsWith("/")) {
                if(verbose) System.err.println("# / adding contents of " +s);
                File subdir = new File(s);
                if(new File(subdir,"versions.xml").exists()) {
                    addSource(s+"versions.xml");
                }
                for (String ss : subdir.list(
                            new FilenameFilter() {
                                public boolean accept(File dir, String name) {
                                    return(name.endsWith(".xml")&&!name.equals("versions.xml"));
                                } })) {
                   addSource(s+ss);
                }
                if(verbose) System.err.println("# \\ end contents of " +s);
            } else {
                addSource(s);
            }
        }
        if(sources.length()==0) {
            System.err.println("Merger - merge multiple XML documents.\nOptions: [-c] [-o:outfile.xml] [-v]  source1.xml source2.xml ...");
            return;
        }
        
        OutputStream out = null;
        java.io.FileOutputStream fos = null;
        if(outfile!=null) {
            fos = new FileOutputStream(outfile);
            out = fos;
            if(verbose) System.err.println("# Write <"+outfile+">");
        } else {
            out = System.out;
            if(verbose) System.err.println("# Write <stdout>");
        }
//        try {
             java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                     out);
             String copy = "";
             if(copyright) copy = ("<!-- Copyright (c) "+Calendar.getInstance().get(Calendar.YEAR)+" IBM Corporation and Others, All Rights Reserved. -->\n");
             LDMLUtilities.printDOMTree(full, new PrintWriter(writer),copy+"\n<!-- This file was generated from: "+sources+" -->\n<!DOCTYPE icuInfo SYSTEM \"http://icu-project.org/dtd/icumeta.dtd\">\n",null); //
             writer.flush();
//        } catch (IOException e) {
//            Syste
//        }
          if(fos!=null) fos.close();
    }
    
    
    
        // from LDMLUtilities
    static final boolean DEBUG_MERGE = false;
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
    private static Node mergeXMLDocuments(Document source, Node override, StringBuffer xpath, 
                                          String thisName, String sourceDir, boolean ignoreDraft,
                                          boolean ignoreVersion){
        if(source==null){
            if(DEBUG_MERGE) System.err.println("MM: 0: xp: " + xpath);
            return override;
        }
        if(xpath.length()==0){
            xpath.append("/");
        }
        if(DEBUG_MERGE) System.err.println("MM: 1: xp: " + xpath);
        
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
        int savedLength_p=xpath.length();
        String xpath_p = xpath.substring(0,savedLength_p);
        Node nis_p = null;

        
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
            
            if(DEBUG_MERGE) System.err.println("MM: xp: " + xpath  + ", nis:" + nodeInSource);

            Node parentNodeInSource = null;
            if(nodeInSource==null){
                // the child xml has a new node
                // that should be added to parent
                String parentXpath = xpath.substring(0, savedLength);

                if(childName.indexOf(":")>-1){ 
                    parentNodeInSource = getNode(source, parentXpath, child);
                }else{
                    if(true && parentXpath.equals(xpath_p)) {
                        //System.err.println("NIS: " + xpath_p);
                        if(nis_p != null ) {
                            parentNodeInSource = nis_p;
                            //System.err.println("@@@ GOOL " + xpath_p);
                        } else {
                            parentNodeInSource =  getNode(source,parentXpath);
                            nis_p = parentNodeInSource;
                            //System.err.println("@@@ ----");
                        }
                    } else {
                        parentNodeInSource =  getNode(source,parentXpath);
                    }
                }
                if(parentNodeInSource==null){
                    throw new RuntimeException("Internal Error");
                }
                
                Node childToImport = source.importNode(child,true);
                parentNodeInSource.appendChild(childToImport);
//            }else if( childName.equals(LDMLConstants.IDENTITY)){
//                if(!ignoreVersion){
//                    // replace the source doc
//                    // none of the elements under collations are inherited
//                    // only the node as a whole!!
//                    parentNodeInSource = nodeInSource.getParentNode();
//                    Node childToImport = source.importNode(child,true);
//                    parentNodeInSource.replaceChild(childToImport, nodeInSource);
//                }
//            }else if( childName.equals(LDMLConstants.COLLATION)){
//                // replace the source doc
//                // none of the elements under collations are inherited
//                // only the node as a whole!!
//                parentNodeInSource = nodeInSource.getParentNode();
//                Node childToImport = source.importNode(child,true);
//                parentNodeInSource.replaceChild(childToImport, nodeInSource);
//                //override the validSubLocales attribute
//                String val = LDMLUtilities.getAttributeValue(child.getParentNode(), LDMLConstants.VALID_SUBLOCALE);
//                NamedNodeMap map = parentNodeInSource.getAttributes();
//                Node vs = map.getNamedItem(LDMLConstants.VALID_SUBLOCALE);
//                vs.setNodeValue(val);
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
                    if(false) System.err.println(" MM replace: " + childToImport.getNodeName() + " with " + nodeInSource.getNodeName());
                    parentNodeInSource.replaceChild(childToImport, nodeInSource);
                }
            }
            xpath.delete(savedLength,xpath.length());
            child= child.getNextSibling();
        }
//        if(DEBUG_MERGE==true) {
//            System.out.println("Final: " + getNode(source, oldx).toString());
//        }
//        if(DEBUG_MERGE) {
//            System.err.println("F: " + source.getNodeName()+" / " + xpath);
//        }
        return source;
    }    

    private static void appendXPathAttribute(Node node, StringBuffer xpath, boolean ignoreAlt, boolean ignoreDraft){
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
    private static boolean areChildrenElementNodes(Node node){
        NodeList list = node.getChildNodes();
        for(int i=0;i<list.getLength();i++){
            if(list.item(i).getNodeType()==Node.ELEMENT_NODE){
                return true;
            }
        }
        return false;  
    }
    private static final boolean isAlternate(Node node){
        NamedNodeMap attributes = node.getAttributes();
        Node attr = attributes.getNamedItem(LDMLConstants.ALT);
        if(attr!=null){
            return true;
        }
        return false;
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
        
    /**
     * Fetches the node from the document that matches the given xpath.
     * The context namespace node is required if the xpath contains 
     * namespace elments
     * @param doc
     * @param xpath
     * @param namespaceNode
     * @return
     */
    private static Node getNode(Document doc, String xpath, Node namespaceNode){
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
    /**
     * Fetches the node from the document which matches the xpath
     * @param node
     * @param xpath
     * @return
     */
    private static Node getNode(Node node, String xpath){
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
    /**
     * Utility method to fetch the attribute value from the given 
     * element node
     * @param sNode
     * @param attribName
     * @return
     */
    private static String getAttributeValue(Node sNode, String attribName){
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


}
