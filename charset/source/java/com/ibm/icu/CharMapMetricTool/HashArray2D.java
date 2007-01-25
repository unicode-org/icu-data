/*
 *****************************************************************************
 * Copyright (C) 2000-2007, International Business Machines Corporation and  *
 * others. All Rights Reserved.                                              *
 *****************************************************************************
*/

import java.util.*;
import java.io.*;


public class HashArray2D implements Serializable, MappingTypes
{    
    private TreeMap keyToIndex;
    private Object value[][];
    private Comparator comp;
    private boolean compareISOControls = true;
    
    /** constructor */
    public HashArray2D(Object keys[], Comparator c) {
        comp = c;
        keyToIndex = new TreeMap(comp);
        value = new Object[keys.length][keys.length];
        
        for (int idx = 0; idx < keys.length; idx++) {
            keyToIndex.put(keys[idx], new Integer(idx));
        }
    }
    
    /** constructor */
    public HashArray2D(Object keys[]) {
        this(keys, null);
    }
    
    public void setCompareISOControls(boolean val)
    {
        compareISOControls = val;
    }

    public boolean isCompareISOControls()
    {
        return compareISOControls;
    }
    
    public Object get(String rowKey,
                      String colKey) 
    {
        Integer rowInt = (Integer)keyToIndex.get(rowKey);
        Integer colInt = (Integer)keyToIndex.get(colKey);
        if (rowInt == null || colInt == null) {
            return null;
        }
        return value[rowInt.intValue()][colInt.intValue()];
    }
    
    public Object put(String rowKey,
                      String colKey,
                      Object newValue)
    {
        Integer rowInt = (Integer)keyToIndex.get(rowKey);
        Integer colInt = (Integer)keyToIndex.get(colKey);
        if (rowInt == null || colInt == null) {
            return null;
        }
        Object oldVal = value[rowInt.intValue()][colInt.intValue()];
        value[rowInt.intValue()][colInt.intValue()] = newValue;
        return oldVal;
    }
    
/*    public Iterator getValueIterator() {
        return null;
    }*/
    
    public Iterator getKeyIterator() {
        return keyToIndex.keySet().iterator();
    }
    
    public Iterator getRowKeyIterator() {
        return keyToIndex.keySet().iterator();    
    }
        
/*    public void remove(String canName) {
        row.remove(canName);
        Iterator colMaps = row.values().iterator();
        while (colMaps.hasNext()) {
            TreeMap aColMap = (TreeMap)colMaps.next();
            if (aColMap != null) {  
                aColMap.remove(canName);
            }
        }
    }
    
    public void move(String fromRC, 
                     String toRC) {
        if (fromRC.equals(toRC)) return;
        
        // handle row
        Object fromValMap = row.get(fromRC);
        if (fromValMap == null) {
            row.remove(toRC);
        } else {
            row.put(toRC, fromValMap);
        }
        row.remove(fromRC);
        
        // handle col
        Iterator colMaps = row.values().iterator();
        while (colMaps.hasNext()) {      
            TreeMap aColMap = (TreeMap)colMaps.next();
            Object fromVal = aColMap.get(fromRC);
            if (fromVal == null) {
                aColMap.remove(toRC);
            } else {
                aColMap.put(toRC, fromVal);
            }
            aColMap.remove(fromRC);
        } 
    }
    
    public void shallowCopy(String fromRC,
                            String toRC) {
        if (fromRC.equals(toRC)) return;
        
        // handle row
        Object fromValMap = row.get(fromRC);
        if (fromValMap == null) {
            row.remove(toRC);
        } else {
            row.put(toRC, fromValMap);
        }
        
        // handle col
        Iterator colMaps = row.values().iterator();
        while (colMaps.hasNext()) {      
            TreeMap aColMap = (TreeMap)colMaps.next();
            Object fromVal = aColMap.get(fromRC);
            if (fromVal == null) {
                aColMap.remove(toRC);
            } else {
                aColMap.put(toRC, fromVal);
            }
        } 
    } 
*/  
    // Post:  returns TRUE if fromCanName is a subset of toCanName for the specified mappingType
    public boolean isSubset(String fromCanName,
                            String toCanName,
                            int mappingType) {
        ShortOrderedTriple sot = (ShortOrderedTriple)get(fromCanName, toCanName);
        switch (mappingType) {
            case ROUNDTRIP:
                return sot.rt == Integer.MAX_VALUE;
            case FALLBACK:
                return sot.fb == Integer.MAX_VALUE;
            case REVERSE_FALLBACK:
                return sot.rfb == Integer.MAX_VALUE;
            default:
                return false;
        }  
    }
    
    // Post:  returns TRUE if fromCanName is a superset of toCanName for the specified mappingType
    public boolean isSuperSet(String fromCanName,
                              String toCanName,
                              int mappingType) {
        ShortOrderedTriple sot = (ShortOrderedTriple)get(toCanName, fromCanName);
        switch (mappingType) {
            case ROUNDTRIP:
                return sot.rt == Integer.MAX_VALUE;
            case FALLBACK:
                return sot.fb == Integer.MAX_VALUE;
            case REVERSE_FALLBACK:
                return sot.rfb == Integer.MAX_VALUE;
            default:
                return false;
        }  
    }
    
    public ShortOrderedTriple bestMetricWhenMappingFrom(String fromCanName) {
        Integer rowInt = (Integer)keyToIndex.get(fromCanName);
        if (rowInt == null)
            return null;
        short bestRT = 0, bestFB = 0, bestRFB = 0;
        int keySize = keyToIndex.size();
        Object rowValues[] = value[rowInt.intValue()];
        
        for (int idx = 0; idx < keySize; idx++) {
            ShortOrderedTriple sot = (ShortOrderedTriple)rowValues[idx];
            if (sot == null) {
                continue;
            } else {
                if (sot.rt > bestRT) bestRT = sot.rt;
                if (sot.fb > bestFB) bestFB = sot.fb;
                if (sot.rfb > bestRFB) bestRFB = sot.rfb;
            }
        }
        return new ShortOrderedTriple(bestRT, bestFB, bestRFB);
    }
    
    public ShortOrderedTriple bestMetricWhenMappingTo(String toCanName) {
        Integer colInt = (Integer)keyToIndex.get(toCanName);
        if (colInt == null)
            return null;
        short bestRT = 0, bestFB = 0, bestRFB = 0;
        int keySize = keyToIndex.size();
        int col = colInt.intValue();
        for (int idx = 0; idx < keySize; idx++) {
            ShortOrderedTriple sot = (ShortOrderedTriple)value[idx][col];
            if (sot == null) {
                continue;
            } else {
                if (sot.rt > bestRT) bestRT = sot.rt;
                if (sot.fb > bestFB) bestFB = sot.fb;
                if (sot.rfb > bestRFB) bestRFB = sot.rfb;           
            }
        } 
        return new ShortOrderedTriple(bestRT, bestFB, bestRFB);
    }
    
/*    public static void main(String[] args) // test code
    {
        // load
        String keyArr[] = {"a", "b", "c", "d"};
        HashArray2D h2D = new HashArray2D(keyArr);
        h2D.put("a", "b", new Integer(1));
        h2D.put("a", "c", new Integer(2));
        h2D.put("a", "d", new Integer(3));        

        h2D.put("b", "a", new Integer(0));
        h2D.put("b", "c", new Integer(2));
        h2D.put("b", "d", new Integer(3));
        h2D.put("b", "e", new Integer(4));

        h2D.put("c", "a", new Integer(0));        
        h2D.put("c", "b", new Integer(1));
        h2D.put("c", "d", new Integer(3));
        
        h2D.put("d", "a", new Integer(0));
        h2D.put("d", "b", new Integer(1));
        h2D.put("d", "c", new Integer(2));
        h2D.put("d", "e", new Integer(4));

        // view data
        Iterator myIt = h2D.getKeyIterator();
        while (myIt.hasNext()) {
            StringPair sp = (StringPair)myIt.next();
            System.out.println(sp + " = " + (Integer)h2D.get(sp.r, sp.c));
        }
        
        // exercise shallowCopy method
        h2D.shallowCopy("b", "d");
        
        // view new data
        Iterator newMyIt = h2D.getKeyIterator();
        System.out.println();
        while (newMyIt.hasNext()) {
            StringPair sp = (StringPair)newMyIt.next();
            System.out.println(sp + " = " + (Integer)h2D.get(sp.r, sp.c));
        }  
        
        // change one value and see if it changes in both locations 
        h2D.put("b", "c", new Integer(20));
        
        // view changed data
        Iterator newMyIt2 = h2D.getKeyIterator();
        System.out.println();
        while (newMyIt2.hasNext()) {
            StringPair sp = (StringPair)newMyIt2.next();
            System.out.println(sp + " = " + (Integer)h2D.get(sp.r, sp.c));
        }
        
    } 
*/    
       
    public static HashArray2D deserializeHashArray2D(String pathToSerializedForm) 
    throws
    FileNotFoundException,
    IOException,
    ClassNotFoundException
    {
        ObjectInputStream in = new ObjectInputStream(
                                   new FileInputStream(pathToSerializedForm));
        return (HashArray2D)in.readObject();
    }   
}

