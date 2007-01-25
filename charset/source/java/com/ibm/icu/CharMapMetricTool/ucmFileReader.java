/*
 *****************************************************************************
 * Copyright (C) 2000-2007, International Business Machines Corporation and  *
 * others. All Rights Reserved.                                              *
 *****************************************************************************
*/

import java.util.*;
import java.io.*;


// preconditions:  The file read by inFile is in correct ucm format 
// This is not thread safe because of the caches.
public final class ucmFileReader  
{   
    private static final Map GARBAGE_MAP = new HashMap(251);
    
    private static final Map MAP_CACHE[] = new Map[]{null, null, GARBAGE_MAP, null};
    private static final int INTERSECT_CACHE[] = new int[]{0, 0, 0, 0};
    private static final int TYPE_COUNT_CACHE[] = new int[]{0, 0, 0, 0};
    
    public static final void populateMap(Map rtMap,               // roundtrip map
                                   Map fbMap,               // fallback map
                                   Map rfbMap,              // reverse fallback map
                                   boolean compareISOControls,
                                   BufferedReader inFile) 
    throws
    IOException
    {
        String aLine, key, value;
        int fbi;                   // fallback indicator {0, 1, 2, 3}
        int indexOfPipe;
        
        MAP_CACHE[MappingTypes.ROUNDTRIP] = rtMap;
        MAP_CACHE[MappingTypes.FALLBACK] = fbMap;
        MAP_CACHE[MappingTypes.SUBSTITUTION].clear();   // ignore any substitution chars
        MAP_CACHE[MappingTypes.REVERSE_FALLBACK] = rfbMap;
        
        aLine = inFile.readLine();
        while (!aLine.startsWith("CHARMAP")) { 
            aLine = inFile.readLine();    
        }
        
        aLine = inFile.readLine();
        while (aLine != null && !aLine.startsWith("END CHARMAP")) {
            if (aLine.length() > 0 && aLine.charAt(0) != '#') {
                indexOfPipe = aLine.lastIndexOf('|');
                fbi = aLine.charAt(indexOfPipe + 1) - '0';
                key = aLine.substring(2, aLine.lastIndexOf('>'));
                if (fbi < MAP_CACHE.length) {
                    boolean isComparableChar = true;
                    try 
                    {
                        isComparableChar = (compareISOControls || !Character.isISOControl((char)Integer.parseInt(key, 16)));
                    }
                    catch (NumberFormatException nfe) {
                        // If it's a multi codepoint character, which doesn't parse, always compare it.
                    }
                    if (isComparableChar) {
                        value = aLine.substring(aLine.indexOf('\\'), indexOfPipe).trim();
                        MAP_CACHE[fbi].put(key, value);
                    }
                }
                else {
//                    aLine = inFile.readLine();
                    System.out.println("Unexpected fallback indicator code encountered (skipped)");
                    System.out.println("File: " + inFile);
                    System.out.println("Line: " + aLine);
//                    continue;
                }
            }
            aLine = inFile.readLine();
        }
        inFile.close();         
    }

/*    public static void populateIntegerMap(Map rtMap,               // roundtrip map
                                   Map fbMap,               // fallback map
                                   Map rfbMap,              // reverse fallback map
                                   boolean compareISOControls,
                                   BufferedReader inFile) 
    throws
    IOException
    {
        String aLine, value;
        int key;
        int fbi;                   // fallback indicator {0, 1, 2, 3}
        int indexOfPipe;
        MAP_CACHE[MappingTypes.ROUNDTRIP] = rtMap;
        MAP_CACHE[MappingTypes.FALLBACK] = fbMap;
        // ignore any substitution chars
        MAP_CACHE[MappingTypes.REVERSE_FALLBACK] = rfbMap;
        
        aLine = inFile.readLine();
        while (!aLine.startsWith("CHARMAP")) { 
            aLine = inFile.readLine();    
        }
        
        aLine = inFile.readLine();
        while (aLine != null && !aLine.startsWith("END CHARMAP")) {
            if (aLine.length() > 0 && aLine.charAt(0) != '#') {
                indexOfPipe = aLine.lastIndexOf('|');
                fbi = aLine.charAt(indexOfPipe + 1) - '0';
                key = Integer.parseInt(aLine.substring(2, aLine.indexOf('>')), 16);
                if (fbi < MAP_CACHE.length) {
                    if (compareISOControls || !Character.isISOControl((char)key)) {
                        value = aLine.substring(aLine.indexOf('\\'), indexOfPipe).trim();
                        MAP_CACHE[fbi].put(new Integer(key), value);
                    }
                }
                else {
                    System.out.println("Unexpected fallback indicator code encountered (skipped)");
                    System.out.println("File: " + inFile);
                    System.out.println("Line: " + aLine);
                }
            }
            aLine = inFile.readLine();
        }
        inFile.close();         
    }
*/
    public static final void compareMap(Map rtMap,               // roundtrip map
                                  Map fbMap,               // fallback map
                                  Map rfbMap,              // reverse fallback map
                                  MappingTableAnalyzer mta,
                                  boolean compareISOControls,
                                  BufferedReader inFile) 
    throws
    IOException
    {
        Object fromValueObj;
        String aLine, key, fromValue, toValue;
        int fbi;                   // fallback indicator {0, 1, 2, 3}
        int indexOfPipe;

        MAP_CACHE[MappingTypes.ROUNDTRIP] = rtMap;
        MAP_CACHE[MappingTypes.FALLBACK] = fbMap;
        //MAP_CACHE[MappingTypes.SUBSTITUTION].clear();   // ignore any substitution chars
        MAP_CACHE[MappingTypes.REVERSE_FALLBACK] = rfbMap;

        TYPE_COUNT_CACHE[MappingTypes.ROUNDTRIP] = 0;
        TYPE_COUNT_CACHE[MappingTypes.FALLBACK] = 0;
        TYPE_COUNT_CACHE[MappingTypes.REVERSE_FALLBACK] = 0;
        
        INTERSECT_CACHE[MappingTypes.ROUNDTRIP] = 0;
        INTERSECT_CACHE[MappingTypes.FALLBACK] = 0;
        INTERSECT_CACHE[MappingTypes.REVERSE_FALLBACK] = 0;
        
        
        aLine = inFile.readLine();
        while (!aLine.startsWith("CHARMAP")) { 
            aLine = inFile.readLine();    
        }
        
        aLine = inFile.readLine();
        while (aLine != null && !aLine.startsWith("END CHARMAP")) {
            if (aLine.length() > 0 && aLine.charAt(0) != '#') {
                boolean isComparableChar = true;
                key = aLine.substring(2, aLine.lastIndexOf('>'));
                try {
                    isComparableChar = (compareISOControls || !Character.isISOControl((char)Integer.parseInt(key, 16)));
                }
                catch (NumberFormatException nfe) {
                    // If it's a multi codepoint character, which doesn't parse, always compare it.
                }
                if (isComparableChar) {
                    indexOfPipe = aLine.lastIndexOf('|');
                    fbi = aLine.charAt(indexOfPipe + 1) - '0';
                    TYPE_COUNT_CACHE[fbi]++;
                    fromValueObj = MAP_CACHE[fbi].get(key);
                    if (fromValueObj != null) {
                        fromValue = (String)fromValueObj;
                        toValue = aLine.substring(aLine.indexOf('\\'), indexOfPipe).trim();
                        if ((fromValue).equals(toValue)) {
                            INTERSECT_CACHE[fbi]++;
                        }
                    }
                }
            }
            aLine = inFile.readLine();
        }
        inFile.close();
        
        mta.setValues(MAP_CACHE, INTERSECT_CACHE, TYPE_COUNT_CACHE);
    }
    
    /**
     * @return the intersetion of the two sets
     */
/*    private static int compareOneIntegerMapSet(Map fromMap,
									             Map toMap)
    {
        int intersections = 0;
        Iterator fromItr = fromMap.keySet().iterator();
        
        while (fromItr.hasNext()) {
            Object key = fromItr.next();
            Object toVal = toMap.get(key);
            if (toVal != null && ((String)toVal).compareTo(fromMap.get(key)) == 0) {
                intersections++;
            }
        }
        return intersections;
    }
    
    
    public static void compareIntegerMaps(Map rtMapFrom,               // roundtrip map
                                  Map fbMapFrom,               // fallback map
                                  Map rfbMapFrom,              // reverse fallback map
                                  Map rtMapTo,               // roundtrip map
                                  Map fbMapTo,               // fallback map
                                  Map rfbMapTo,              // reverse fallback map                                  MappingTableAnalyzer mta,
                                  MappingTableAnalyzer mta) 
    {
        MAP_CACHE[MappingTypes.ROUNDTRIP] = rtMapFrom;
        MAP_CACHE[MappingTypes.FALLBACK] = fbMapFrom;
        MAP_CACHE[MappingTypes.REVERSE_FALLBACK] = rfbMapFrom;

        TYPE_COUNT_CACHE[MappingTypes.ROUNDTRIP] = rtMapTo.size();
        TYPE_COUNT_CACHE[MappingTypes.FALLBACK] = fbMapTo.size();
        TYPE_COUNT_CACHE[MappingTypes.REVERSE_FALLBACK] = rfbMapTo.size();
        
        INTERSECT_CACHE[MappingTypes.ROUNDTRIP] = compareOneIntegerMapSet(rtMapFrom, rtMapTo);
        INTERSECT_CACHE[MappingTypes.FALLBACK] = compareOneIntegerMapSet(fbMapFrom, fbMapTo);
        INTERSECT_CACHE[MappingTypes.REVERSE_FALLBACK] = compareOneIntegerMapSet(rfbMapFrom, rfbMapTo);
        
        mta.setValues(MAP_CACHE, INTERSECT_CACHE, TYPE_COUNT_CACHE);
    }*/
}