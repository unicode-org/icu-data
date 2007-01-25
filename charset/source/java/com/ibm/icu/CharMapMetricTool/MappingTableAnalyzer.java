/*
 *****************************************************************************
 * Copyright (C) 2000-2007, International Business Machines Corporation and  *
 * others. All Rights Reserved.                                              *
 *****************************************************************************
*/

import java.util.*;


public class MappingTableAnalyzer implements MappingTypes
{    
    private double fromSizeRT, fromSizeFB, fromSizeRFB;
    private double toSizeRT, toSizeFB, toSizeRFB;
    private double intersectionsRT, intersectionsFB, intersectionsRFB;
    
    /** constructor */
    public MappingTableAnalyzer() {}

    public MappingTableAnalyzer(Hashtable fromRT, Hashtable fromFB, Hashtable fromRFB,
                                Hashtable toRT, Hashtable toFB, Hashtable toRFB)
    {
        setTables(fromRT, fromFB, fromRFB, toRT, toFB, toRFB);
    }
    
    public void setTables(Hashtable fromRT, Hashtable fromFB, Hashtable fromRFB,
                          Hashtable toRT, Hashtable toFB, Hashtable toRFB)
    {
        fromSizeRT = (double)fromRT.size();
        fromSizeFB = (double)fromFB.size();
        fromSizeRFB = (double)fromRFB.size();
        toSizeRT = (double)toRT.size();
        toSizeFB = (double)toFB.size();
        toSizeRFB = (double)toRFB.size();
        intersectionsRT = getIntersections(fromRT, toRT);
        intersectionsFB = getIntersections(fromFB, toFB);
        intersectionsRFB = getIntersections(fromRFB, toRFB);
    }

    public void setValues(Map fromMaps[],
                          int intersections[], int toSize[])
    {
        fromSizeRT = (double)fromMaps[ROUNDTRIP].size();
        fromSizeFB = (double)fromMaps[FALLBACK].size();
        fromSizeRFB = (double)fromMaps[REVERSE_FALLBACK].size();
        toSizeRT = (double)toSize[ROUNDTRIP];
        toSizeFB = (double)toSize[FALLBACK];
        toSizeRFB = (double)toSize[REVERSE_FALLBACK];
        intersectionsRT = intersections[ROUNDTRIP];
        intersectionsFB = intersections[FALLBACK];
        intersectionsRFB = intersections[REVERSE_FALLBACK];
    }

    public ShortOrderedTriple getMetric() {
        short rt, fb, rfb;
        rt = fromSizeRT == 0 ? Short.MAX_VALUE :
            (short)Math.round(intersectionsRT / fromSizeRT * Short.MAX_VALUE);
        fb = fromSizeFB == 0 ? Short.MAX_VALUE :
            (short)Math.round(intersectionsFB / fromSizeFB * Short.MAX_VALUE);
        rfb = fromSizeRFB == 0 ? Short.MAX_VALUE :
            (short)Math.round(intersectionsRFB / fromSizeRFB * Short.MAX_VALUE);
        return new ShortOrderedTriple(rt, fb, rfb);
    }
    
    public ShortOrderedTriple getReverseMetric() {
        short rt, fb, rfb;
        rt = toSizeRT == 0 ? Short.MAX_VALUE :
            (short)Math.round(intersectionsRT / toSizeRT * Short.MAX_VALUE);
        fb = toSizeFB == 0 ? Short.MAX_VALUE :
            (short)Math.round(intersectionsFB / toSizeFB * Short.MAX_VALUE);
        rfb = toSizeRFB == 0 ? Short.MAX_VALUE :
            (short)Math.round(intersectionsRFB / toSizeRFB * Short.MAX_VALUE);
        return new ShortOrderedTriple(rt, fb, rfb);
    }
    
    public boolean areIdentical() {
        return ((fromSizeRT == toSizeRT) && (fromSizeRT == intersectionsRT)) &&
               ((fromSizeFB == toSizeFB) && (fromSizeFB == intersectionsFB)) &&
               ((fromSizeRFB == toSizeRFB) && (fromSizeRFB == intersectionsRFB));
    }

    public boolean areSimilar() {
        return ((fromSizeRT == toSizeRT) && (fromSizeRT == intersectionsRT));
    }
       

    // preconditions:  Hashtables in correct format and contains only one type of mappings
    //                 i.e.  all roundtrips, all fallbacks, or all reverse fallbacks
    private double getIntersections(Hashtable from,
                                    Hashtable to)  {
        int intersections = 0;
        Hashtable small, big;
        
        if (from.size() <= to.size()) {
            small = from;
            big = to;
        } else {
            small = to;
            big = from;
        }

        Enumeration smallHash = small.keys();        
        while (smallHash.hasMoreElements()) {
            String u = (String)smallHash.nextElement();
            if (big.containsKey(u)) {
                String u2bc = (String)small.get(u);
                if (((String)big.get(u)).equals(u2bc)) {
                    intersections++;
                }
            } 
        }
        
        return (double)intersections;
    }   
}