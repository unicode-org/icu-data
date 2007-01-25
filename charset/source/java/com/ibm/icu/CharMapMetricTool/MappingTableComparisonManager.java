/*
 *****************************************************************************
 * Copyright (C) 2000-2007, International Business Machines Corporation and  *
 * others. All Rights Reserved.                                              *
 *****************************************************************************
*/
import java.util.*;
import java.io.*;
import java.text.*;

public class MappingTableComparisonManager 
{
    /* No need to keep doing a rehash because the maps never gets smaller.
     * These prime numbers also make for better hashing speed.
     */
    public static final int AVG_NUM_OF_RT_MAPPINGS =  65521;
//    public static final int AVG_NUM_OF_RT_MAPPINGS =  55511;
//    public static final int AVG_NUM_OF_RT_MAPPINGS =  45523;
    /* glibc-euc_tw has 11991 fallbacks */
    public static final int AVG_NUM_OF_FB_MAPPINGS =  16381;
    public static final int AVG_NUM_OF_RFB_MAPPINGS = 509;

    private String szInDir;
    private String[] tableNames;
    private String[] ucmFileNames;
    private HashArray2D results;
    
    
    public static void main(String[] args) 
    {
        try {
            long startTime = System.currentTimeMillis();
            MappingTableComparisonManager mtcm = parseArgs(args);
    
            mtcm.compareAllMappingTables(); 
            mtcm.resultsToDisk();       
            UserInterfaceBuilder uib = new UserInterfaceBuilder(mtcm.getResults(), true);
//            UserInterfaceBuilder uib = new UserInterfaceBuilder(mtcm.getResults(), false);
//            uib.buildUI();
            uib.buildTable2D(91.0, 10.0);
    
            DateFormat dateFormat = new SimpleDateFormat("H:m:s.S");
            Calendar gregCal = new GregorianCalendar();
            SimpleTimeZone gmt = new SimpleTimeZone(0, "GMT");
            
            gregCal.setTimeZone(gmt);
            dateFormat.setCalendar(gregCal);
            long endTime = System.currentTimeMillis();
            System.out.println("This program took "
                + dateFormat.format(new Date(endTime - startTime)) + " to run");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
    
    private static MappingTableComparisonManager parseArgs(String[] args)
    throws IOException
    {
        if (args.length <= 0) {
            return new MappingTableComparisonManager(false);
        }
        else if (args.length <= 1) {
            if (args[0].startsWith("-i")) {
                return new MappingTableComparisonManager(args[0].equals("-i1"));
            }
            else {
                return new MappingTableComparisonManager(args[0], false);
            }
        }
        else if (args.length <= 2) {
            return new MappingTableComparisonManager(args[1], args[0].equals("-i1"));
        }
        throw new IOException("Usage: java MappingTableComparisonManager [-i[0 1]] [.ucm directory]");
    }
    
    /** constructor */
    public MappingTableComparisonManager(boolean compareISOControls) 
    throws 
    FileNotFoundException,
    IOException 
    {
        this("../../../../../../data/ucm/", compareISOControls);
    }

    /** constructor */
    public MappingTableComparisonManager(String szInDir, boolean compareISOControls) 
    throws
    FileNotFoundException,
    IOException
    {
        int i;
        File inDir = new File(szInDir);
        if (!inDir.exists())  {
            throw new FileNotFoundException(
                "Input directory " + szInDir + " does not exist!\n");
        }
        
        this.szInDir = szInDir;
        ucmFileNames = inDir.list(new UCMFileFilter());        
        if (ucmFileNames == null || ucmFileNames.length == 0) {
            throw new FileNotFoundException(
                "There are no .ucm files in the specified directory!\n");
        }

        /* We sort by file size to decrease the overall time to read each file. */
        System.out.println("Sorting files by size");
        TreeSet fileSet = new TreeSet(new FileSizeComparator(szInDir));
        for (i = 0; i < ucmFileNames.length; i++) {
//            System.out.println(i + " " + ucmFileNames[i]);
            fileSet.add(ucmFileNames[i]);
        }

        tableNames = new String[ucmFileNames.length];
        Iterator itr = fileSet.iterator();
        i = 0;
        while (itr.hasNext()) {
            String ucmFileName = (String)itr.next();
            tableNames[i] = ucmFileName.substring(0, ucmFileName.length() - 4);
            ucmFileNames[i] = szInDir + ucmFileName;
//            System.out.println(i + " " + (new File(ucmFileNames[i])).length() + " " + ucmFileName);
            i++;
        }
        if (i != ucmFileNames.length) {
            throw new IOException("Can't sort files sizes correctly");
        }
        System.out.println("Finished sorting");
        
        results = new HashArray2D(tableNames);
        results.setCompareISOControls(compareISOControls);
    }
    
    public HashArray2D getResults() {
        return results;
    }

/*    private void populateMaps(boolean compareISOControls) 
    throws IOException
    {
        System.out.println("Creating the cache");
        for (int idx = 0; idx < tableNames.length; idx++) {
            BufferedReader rowReader = new BufferedReader(new FileReader(ucmFileNames[idx]));
            Hashtable rtMap = new Hashtable(AVG_NUM_OF_RT_MAPPINGS);
            Hashtable fbMap = new Hashtable(AVG_NUM_OF_FB_MAPPINGS);
            Hashtable rfbMap = new Hashtable(AVG_NUM_OF_RFB_MAPPINGS);

            System.out.println(serFileNames[idx]);
            ucmFileReader.populateMap(rtMap, fbMap, rfbMap, 
                compareISOControls, rowReader);
            rowReader.close();

            ObjectOutputStream objOut = new ObjectOutputStream(
                new FileOutputStream(serFileNames[idx]));
            objOut.writeObject(rtMap);
            objOut.writeObject(fbMap);
            objOut.writeObject(rfbMap);
            objOut.flush();
            objOut.close();
        }
        System.out.println("Finished creating the cache");
    }
*/
    
    public void compareAllMappingTables() 
    	throws FileNotFoundException, Exception
    {
        String userDir = System.getProperty("user.dir");  
        PrintWriter identicalsFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(userDir + "/identicals.html"), "UTF-8")));
        PrintWriter similarFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(userDir + "/similar.html"), "UTF-8")));
        MappingTableAnalyzer mta = new MappingTableAnalyzer();
        HashMap rowHashRT = new HashMap(AVG_NUM_OF_RT_MAPPINGS);
        HashMap rowHashFB = new HashMap(AVG_NUM_OF_FB_MAPPINGS);
        HashMap rowHashRFB = new HashMap(AVG_NUM_OF_RFB_MAPPINGS);
        boolean compareISOControls = results.isCompareISOControls();
        
        identicalsFile.println("<html><head><title>Identical Unicode Conversion Tables</title></head><body>");
        identicalsFile.println("<h3>Identical Unicode conversion tables</h3>");
        identicalsFile.println("<p>More information about the data on this page can be found <a href=\"http://icu.sourceforge.net/charts/charset/\">here</a>.</p><hr>");
        
        similarFile.println("<html><head><title>Similar Unicode Conversion Tables</title></head><body>");
        similarFile.println("<h3>Similar Unicode conversion tables that are identical in roundtrip mappings only.</h3>");
        similarFile.println("<p>More information about the data on this page can be found <a href=\"http://icu.sourceforge.net/charts/charset/\">here</a>.</p><hr>");

//        populateMaps(compareISOControls);
                
        for (int row = 0; row < tableNames.length; row++) {
            String rowName = tableNames[row];
            boolean isIdentFirstLine = true;
            boolean isSimilarFirstLine = true;
            BufferedReader rowReader = new BufferedReader(
                new InputStreamReader(new FileInputStream(ucmFileNames[row]), "US-ASCII"));

            rowHashRT.clear();
            rowHashFB.clear();
            rowHashRFB.clear();
            ucmFileReader.populateMap(rowHashRT, rowHashFB, rowHashRFB, 
                compareISOControls, rowReader);
            rowReader.close();
            
            System.out.println(rowName);

            for (int col = row + 1; col < tableNames.length; col++) {
                String colName = tableNames[col];
                BufferedReader colReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(ucmFileNames[col]), "US-ASCII"));
                
                ucmFileReader.compareMap(rowHashRT, rowHashFB, rowHashRFB,
                    mta, compareISOControls, colReader);
                colReader.close();
/*                ucmFileReader.compareIntegerMaps(rowHashRT, rowHashFB, rowHashRFB,
                    colHashRT, colHashFB, colHashRFB,
                    mta);*/
                if (mta.areSimilar()) {
                    if (isSimilarFirstLine) {
                        isSimilarFirstLine = false;
                        similarFile.println(rowName + "<br>");
                    }
                    similarFile.println("&nbsp;&nbsp;&nbsp;&nbsp;" + colName + "<br>");
                    if (mta.areIdentical()) {
                        System.out.println("\tIDENTICAL:  " + colName);
                        if (isIdentFirstLine) {
                            isIdentFirstLine = false;
                            identicalsFile.println(rowName + "<br>");
                        }
                        identicalsFile.println("&nbsp;&nbsp;&nbsp;&nbsp;" + colName + "<br>");
                    } 
                    else {
                        System.out.println("\tSIMILAR:  " + colName);
                    }
                }
                results.put(rowName, colName, mta.getMetric());
                results.put(colName, rowName, mta.getReverseMetric());
            }
        }
        identicalsFile.println("</body></html>");
        identicalsFile.flush();
        identicalsFile.close();

        similarFile.println("</body></html>");
        similarFile.flush();
        similarFile.close();
        System.out.println("List of identical tables at: " + userDir + "/identicals.html"); 
        System.out.println("List of similar tables at: " + userDir + "/similar.html"); 
    }
    
    public void resultsToDisk()
    throws
    FileNotFoundException,
    IOException
    {
        String userDir = System.getProperty("user.dir");
        ObjectOutputStream objOut = new ObjectOutputStream(
            // new FileOutputStream(userDir + "\\HashArray2D.ser"));  
            new FileOutputStream(userDir + "/HashArray2D.ser"));       
        objOut.writeObject(results);
        objOut.flush();
        objOut.close();
        // System.out.println("Serialized data at: " + userDir + "\\HashArray2D.ser");
        System.out.println("Serialized data at: " + userDir + "/HashArray2D.ser");
    }
    
/*    private final class IdenticalsAssistant 
    {
        int[] identicalTables = new int[30];
        int addIndex = 0;
        int readIndex = 0;
        
        public void addIdenticalTable(int indexOfIdenticalTable) {
            identicalTables[addIndex++] = indexOfIdenticalTable;
        }
       
        public boolean hasNextIdenticalTable() {
            return readIndex < addIndex;
        }
        
        public int nextIdenticalTable() {
            return identicalTables[readIndex++];
        }
        
        public String getBestCanNameForCurrentRow(String rowName) throws Exception {
            if (addIndex == 0) return rowName;
            String[] platform_version = new String[addIndex + 1];
            platform_version[0] = rowName.substring(0, rowName.indexOf('-')) +
                rowName.substring(rowName.lastIndexOf('-') + 1);
            for (int i = 1; i <= addIndex; i++) {
                String cur = (String)tables2Compare.get(identicalTables[i - 1]);
                platform_version[i] = cur.substring(0, cur.indexOf('-')) +
                    cur.substring(cur.lastIndexOf('-') + 1);
            }
            for (int j = 0; j < PRIORITY.length; j++) {
                for (int k = 0; k < platform_version.length; k++) {
                    if (PRIORITY[j].equalsIgnoreCase(platform_version[k])) {
                        return (k == 0) ? rowName : 
                            (String)tables2Compare.get(identicalTables[k - 1]);
                    }
                }
            }
            throw new Exception("Identicals assistant failed!");
        }
    }
*/
    
    
    private class UCMFileFilter implements FilenameFilter {
        UCMFileFilter() {}

        public boolean accept(File dir, String name) {
            return name.toUpperCase().endsWith(".UCM");
        }
    }

    private class FileSizeComparator implements Comparator {
        String inDir;
        
        FileSizeComparator(String dir) {
            inDir = dir;
        }
        
        public int compare(Object o1, Object o2) {
            String str1 = (String)o1;
            String str2 = (String)o2;
            int diff = (int)((new File(inDir + str2)).length() - (new File(inDir + str1)).length());
            if (diff != 0) {
                return diff;
            }
            return str1.compareTo(str2);
        }

        public boolean equals(Object o1) {
            return o1 instanceof FileSizeComparator;
        }
    }
}


