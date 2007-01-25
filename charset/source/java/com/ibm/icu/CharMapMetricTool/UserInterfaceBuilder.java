/*
 *****************************************************************************
 * Copyright (C) 2000-2007, International Business Machines Corporation and  *
 * others. All Rights Reserved.                                              *
 *****************************************************************************
*/

import java.io.*;
import java.util.*;



public class UserInterfaceBuilder {

    private HashArray2D dataBase;
    // private static final String INDEX_DIR = "C:/cpCompTables/";
    // private static final String INDEX_DIR = "/home/jmarchan/cpCompTables/";
    //private static final String INDEX_DIR = "C:/cpCompTables/";
    private static final String INDEX_DIR = "./";
//    private static final String FROM_SUB_DIR = "HTML-fromDataPages/";
//    private static final String TO_SUB_DIR = "HTML-toDataPages/";
    private boolean showMinNum = false;
    private boolean showFallBack = true;
    private boolean showReverseFallBack = false;
    
    public static void main(String[] args) {
        try {
            HashArray2D db = HashArray2D.deserializeHashArray2D(
                // "C:\\CMapping_Posix\\code\\CharMapMetricTool\\HashArray2D.ser"); 
                System.getProperty("user.dir") + "/HashArray2D.ser");
            UserInterfaceBuilder uib = new UserInterfaceBuilder(db, true);
            uib.buildTable2D(90.0, 10.0);
//            uib.buildUI();
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(System.err));
            System.exit(1);
        }
    }
    
    public UserInterfaceBuilder(HashArray2D h2d, boolean showMinNum) throws IOException {

        dataBase = h2d;
        this.showMinNum = showMinNum;
        
        // create directory structure to hold data pages
        File dirI = new File(INDEX_DIR);
/*        File dirF = new File(INDEX_DIR + FROM_SUB_DIR);
        File dirT = new File(INDEX_DIR + TO_SUB_DIR);*/
        
        if ((!dirI.exists() && !dirI.mkdir())/* ||
            (!dirF.exists() && !dirF.mkdir()) ||
            (!dirT.exists() && !dirT.mkdir())*/) {
            throw new IOException("Couldn't create necessary directory structure!!!");
        }
    }
        
/*    public void buildUI() throws IOException {
        buildLinkList("from");
        buildLinkList("to");
        Iterator cpNames = dataBase.getRowKeyIterator();
        String cpName;
        while (cpNames.hasNext()) {
            cpName = (String)cpNames.next();
            buildDataPage("from", cpName);
            buildDataPage("to", cpName);    
        }
    }*/

    /**
     * Create a 2D matrix with all of the codepages round trips
     * @param rtThreshold roundtrip numbers below this value are not displayed
     * @param subsetThreshold A difference by more than this ammount are not displayed as matches.
     */
    public void buildTable2D(double rtThreshold, double subsetThreshold) throws IOException {
        String rt, fb, rfb, rtColor, fbColor, rfbColor;
//        int colspan = 1;
        ShortOrderedTriple curVal, symetricVal, best;
        ShortOrderedTriple max = new ShortOrderedTriple(), min = new ShortOrderedTriple();
        Iterator fromNames = dataBase.getRowKeyIterator();
        Iterator tocNames = dataBase.getRowKeyIterator();
        String filename = INDEX_DIR + "roundtripIndex.html";
        PrintWriter thisFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(filename), "UTF-8")));
        String fromName;
            
        
/*        if (showFallBack) {
	        colspan++;
        }
        if (showReverseFallBack) {
	        colspan++;
        }
*/
        thisFile.println("<html><head>");
        thisFile.println("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">");
        thisFile.println("<title>Detailed Character Set Comparison</title>");
        thisFile.println("<style type=\"text/css\">");
        thisFile.println("<!--");
        thisFile.println("table   { page-break-before: always; }");
        thisFile.println("ul      { list-style-type: none}");
		thisFile.println("caption { margin-bottom: 2pt; font-weight: bold; text-align: left; white-space: nowrap; }");
        thisFile.println("th      { font-weight: normal; text-align: left; padding: 2pt; }");
        thisFile.println("td      { padding: 2pt; }");
        thisFile.println("td.p    { text-align: right; } /* Normal Percent */");
        thisFile.println("td.e    { color: #000000; background-color: #00FF00; text-align: Right; } /* Identical */");
        thisFile.println("td.s    { color: #000000; background-color: #FFFF00; text-align: Right; } /* Subset */");
        thisFile.println("td.b    { color: #000000; background-color: #00FFFF; text-align: Right; } /* Best match */");
        thisFile.println("-->");
        thisFile.println("</style>");
        thisFile.println("</head><body>");
        thisFile.println("<h1>Detailed Character Set Comparison</h1>");
        thisFile.println("<p>This document describes how similar any two Unicode conversion tables are to each other.");
        thisFile.println("Please see <a href=\"http://www.unicode.org/unicode/reports/tr22/\">http://www.unicode.org/unicode/reports/tr22/</a> for the character mapping details.");
        thisFile.println("If you would like more information on this topic, like alias tables or location of these mappings, please go to the <a href=\"index.html\">main page</a>.");
        thisFile.println("Style sheets must be enabled in your web browser to properly view these results.");
        thisFile.println("<b>These results are preliminary.</b></p>");
        thisFile.println("<p><em>Options used to generate this document</em></p>");
        thisFile.println("<p>Document generated on " + new Date() + "<br>");
        thisFile.println("Roundtrip threshold: " + rtThreshold + "%<br>");
        thisFile.println("Subset threshold: " + subsetThreshold + "%<br>");
        thisFile.println("Compare ISO control characters: " + dataBase.isCompareISOControls() + "<br>");
        thisFile.println("Show minimum number: " + showMinNum + "<br>");
        thisFile.println("Show fallback results: " + showFallBack + "<br>");
        thisFile.println("Show reverse fallback results: " + showReverseFallBack + "</p>");
        thisFile.println("<p><em>Definition of terms</em></p>");
        thisFile.println("<p>The \"RT(%)\" column means \"roundtrip percent\"<br>");
        if (showFallBack) {
            thisFile.println("The \"FB(%)\" column means \"fallback percent\"<br>");
        }
        thisFile.println("<span style=\"background-color: #00FF00;\">Green</span> means identical.<br>");
        thisFile.println("<span style=\"background-color: #FFFF00;\">Yellow</span> means different set sizes.<br>");
        thisFile.println("<span style=\"background-color: #00FFFF;\">Cyan</span> means best match.</p>");
        if (showMinNum) {
            thisFile.println("<p>The equation used to calculate an entry in a table is \"minimum( sizeof(A intersects B)/ sizeof(A), sizeof(A intersects B) / sizeof(B))\". ");
            thisFile.println("This equation can also look like this \"min( | A \u2229 B | / | A |, | A \u2229 B | / | B |)\"");
        }
        else {
            thisFile.println("<p>The equation used to calculate an entry in a table is \"sizeof(A intersects B)/ sizeof(A)\". ");
            thisFile.println("This equation can also look like this \"| A \u2229 B | / | A |\"");
        }
        thisFile.println("where A is a set of mappings for a codepage, and B is a different set of mappings for a codepage that is being compared to A.</p>");

        
        thisFile.println("<br>");
        thisFile.println("<h2>Table of Contents</h2>");
        thisFile.println("<p>Here is the Table of Contents for all comparisons. You can use it to quickly go to your desired comparison summary table.</p>");
        thisFile.println("<ul>");
        while (tocNames.hasNext()) {
            fromName = (String)tocNames.next();
	        thisFile.println("<li><a href=\"#" + fromName + "\">" + fromName + "</a></li>");
        }
        thisFile.println("</ul>");
        thisFile.println("<br>");
        thisFile.println("<h2>Comparison Summary</h2>");
        while (fromNames.hasNext()) {
            fromName = (String)fromNames.next();
            best = dataBase.bestMetricWhenMappingFrom(fromName); 
            Iterator toNames = dataBase.getRowKeyIterator();
            boolean showHeader = true;

            thisFile.println("<br>");
            thisFile.println("<table border=1 cellspacing=0>");

//            if (showMinNum) {
                thisFile.println("<caption><a name=\"" + fromName + "\" href=\"http://source.icu-project.org/repos/icu/data/trunk/charset/data/ucm/" + fromName + ".ucm\">" + fromName + "</a></caption>");
//            }
//            else {
//                thisFile.println("<tr><th>FROM</th><th colspan=" + colspan + "><a name=\"" + fromName + "\" href=\"http://source.icu-project.org/repos/icu/data/trunk/charset/data/ucm/" + fromName + ".ucm\">" + fromName + "</a></th></tr>");
//            }

            System.out.println(fromName);
            while (toNames.hasNext()) {                                                  
                String toName = (String)toNames.next();
                // System.out.println(fromName + "->" + toName);
                if (fromName.equals(toName)) {
                    // System.out.println("Skipping " +fromName+ " " +toName);
                    continue;
                }
                curVal = (ShortOrderedTriple)dataBase.get(fromName, toName);
                symetricVal = (ShortOrderedTriple)dataBase.get(toName, fromName);
                if (showMinNum && curVal.rt > symetricVal.rt) {
                    min.rt = symetricVal.rt;
                    max.rt = curVal.rt;
                }
                else {
                    min.rt = curVal.rt;
                    max.rt = symetricVal.rt;
                }
                
                if ((Math.abs(ShortOrderedTriple.asPercent((int)(max.rt - min.rt), 0.01)) <= subsetThreshold
                    && ShortOrderedTriple.asPercent(min.rt, 0.01) >= rtThreshold))
                {
                    rt = ShortOrderedTriple.asStringPercent2(min.rt);
                    rtColor = getCellClass(min.rt, max.rt, best.rt);

					if (showHeader) {
                        if (showMinNum) {
                            thisFile.print("<tr><td>\u00A0</td><th>RT(%)</th>");
                        }
                        else {
                            thisFile.print("<tr><th>TO</th><th>RT(%)</th>");
                        }
                        if (showFallBack) {
                            thisFile.print("<th>FB(%)</th>");
                        }
                        if (showReverseFallBack) {
                            thisFile.print("<th>RFB(%)</th>");
                        }
                        thisFile.println("</tr>");
                        showHeader = false;
					}

                    thisFile.print("<tr><th>" + toName
                        + "</th><td class=" + rtColor + ">" + rt + "</td>");

                    if (showFallBack) {
                        if (showMinNum && curVal.fb > symetricVal.fb) {
                            min.fb = symetricVal.fb;
                            max.fb = curVal.fb;
                        }
                        else {
                            min.fb = curVal.fb;
                            max.fb = symetricVal.fb;
                        }
                        fb = ShortOrderedTriple.asStringPercent2(min.fb);
                    
                        fbColor = getCellClass(min.fb, max.fb, best.fb);
                    
                        thisFile.print("<td class=" + fbColor + ">" + fb + "</td>");
                    }
                    if (showReverseFallBack) {
                        if (showMinNum && curVal.rfb > symetricVal.rfb) {
                            min.rfb = symetricVal.rfb;
                            max.rfb = curVal.rfb;
                        }
                        else {
                            min.rfb = curVal.rfb;
                            max.rfb = symetricVal.rfb;
                        }
                        rfb = ShortOrderedTriple.asStringPercent2(min.rfb);
                    
                        rfbColor = getCellClass(min.rfb, max.rfb, best.rfb);
                    
                        thisFile.print("<td class=" + rfbColor + ">" + rfb + "</td>");
                    }
                    thisFile.println("</tr>");
                }
            }
            if (showHeader) {
                /* Nothing matched */
                thisFile.println("<tr><td>No mappings are similar to this mapping.</td></tr>");
            }
            thisFile.println("</table>");
        }
        thisFile.println("</html>");
        thisFile.flush();
        thisFile.close();
        System.out.println("Table written to: " + filename);
    }
    
    // Pre:  type == "from" or type == "to"
/*    private void buildLinkList(String type) throws IOException {   
        Iterator cpNames = dataBase.getRowKeyIterator();
        PrintWriter thisFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(INDEX_DIR + type + "CPLinkList.html"), "UTF-8")));         
        thisFile.println("<HTML><HEAD><TITLE>" + type + " code page links</TITLE></HEAD><BODY>"); 
        while (cpNames.hasNext()) {
            String cpName = (String)cpNames.next();
            thisFile.println("<A HREF=\"HTML-" + type + "DataPages/" + type + cpName + 
            ".html\" TARGET=noneSelected>" + cpName + "</A><BR>");
        }
        thisFile.println("</BODY></HTML>");
        thisFile.flush();
        thisFile.close();      
    }
    
    private void buildDataPage(String type,
                               String cpName) throws IOException {
        String cp, rt, fb, rfb, rtColor, fbColor, rfbColor;
        ShortOrderedTriple curVal, symetricVal, best;
        Iterator cpNames = dataBase.getRowKeyIterator();
        String subDir = type.equals("from") ? FROM_SUB_DIR : TO_SUB_DIR;
        PrintWriter thisFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(INDEX_DIR + subDir + type + cpName + ".html"), "UTF-8")));

        thisFile.println("<HTML><HEAD><TITLE>" + type + " " + cpName + "</TITLE></HEAD><BODY>"); 
        thisFile.println("<TABLE CELLSPACING=0 BORDER=1 CELLPADDING=2><TR><TD><B>" + 
            type.toUpperCase() + "</B></TD>" + "<TD COLSPAN=3><B>" + cpName + 
            "</B></TD></TR>");
        thisFile.println("<TR><TD>" + (type.equals("from") ? "TO" : "FROM") + 
            "</TD><TD>RT(%)</TD><TD>FB(%)</TD><TD>RFB(%)</TD></TR>");
        if (type.equals("from")) {
            best = dataBase.bestMetricWhenMappingFrom(cpName); 
        } else {
            best = dataBase.bestMetricWhenMappingTo(cpName);
        }
        // System.out.println(type + " " + cpName);
        while (cpNames.hasNext()) {                                                  
            cp = (String)cpNames.next();
            // System.out.println(cp);
            if (cp.equals(cpName)) continue;   
            if (type.equals("from")) {
                curVal = (ShortOrderedTriple)dataBase.get(cpName, cp);
                if (curVal == null) { // quick fix for row/col ibm-1363_0B-2000 
                    System.out.println("null, skipping (" + cpName + ", " + cp + ")");
                    continue;
                }
                symetricVal = (ShortOrderedTriple)dataBase.get(cp, cpName);
            } else {
                curVal = (ShortOrderedTriple)dataBase.get(cp, cpName);
                if (curVal == null) { // quick fix for row/col ibm-1363_0B-2000 
                    System.out.println("null, skipping (" + cp + ", " + cpName + ")");
                    continue;
                }
                symetricVal = (ShortOrderedTriple)dataBase.get(cpName, cp);
            }
                        
            rt = ShortOrderedTriple.asStringPercent2(curVal.rt);
            fb = ShortOrderedTriple.asStringPercent2(curVal.fb);
            rfb = ShortOrderedTriple.asStringPercent2(curVal.rfb);
            
            rtColor = getCellColor(curVal.rt, symetricVal.rt, best.rt);
            fbColor = getCellColor(curVal.fb, symetricVal.fb, best.fb);
            rfbColor = getCellColor(curVal.rfb, symetricVal.rfb, best.rfb); 
            
            thisFile.println("<TR><TD>" + cp
                + "</TD><TD ALIGN=\"RIGHT\" BGCOLOR=\"" + rtColor + "\">" + rt
                + "</TD><TD ALIGN=\"RIGHT\" BGCOLOR=\"" + fbColor + "\">" + fb
                + "</TD><TD ALIGN=\"RIGHT\" BGCOLOR=\"" + rfbColor + "\">" + rfb
                + "</TD></TR>");
        }
        thisFile.println("</TABLE></HTML>");
        thisFile.flush();
        thisFile.close();
    } 
*/
    
    private String getCellClass(int aVal,
                                int symVal,
                                int bestVal) {
        if (aVal == Short.MAX_VALUE) {
            if (symVal == Short.MAX_VALUE) {
                return "e";           // green    100% round trip <->
            } else {
                return "s";          // yellow   aVal is a subset of symVal
            }
        } else if (aVal == bestVal) {
            return "b";             // blue     closest match
        } else if (aVal != symVal) {
            return "s";              // yellow   different set size
        }
        else {
            return "p";              // white    normal percent
        }
    }  

    private String getCellColor(int aVal,
                                int symVal,
                                int bestVal) {
        if (aVal == Integer.MAX_VALUE) {
            if (symVal == Integer.MAX_VALUE) {
                return "#00FF00";           // green    100% round trip <->
            } else {
                return "#FFFF00";           // yellow   aVal is a subset of symVal
            }
        } else if (aVal == bestVal) {
            return "#00FFFF";               // blue     closest match
        } else {
            return "#FFFFFF";               // white
        }
    }  
}