
/**
 * Copyright (c) 2008-2010 IBM Corporation. All Rights Reserved.
 */
package com.ibm.icu.dev.meta;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import com.ibm.icu.impl.ICUResourceBundle;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.LocaleData;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.UResourceBundle;
import com.ibm.icu.util.VersionInfo;

/**
 * Calculate the Unicode 'age' of a locale, based on unicode support.
 * @author srl
 *
 */
public class UCharacterSupport {
    private static UCharacterSupport singleton = null;

    /**
     * Return the current CLDR version.
     * @return
     */
    public static VersionInfo getCLDRVersion() {
        UResourceBundle urb = UResourceBundle.getBundleInstance(ICUResourceBundle.ICU_BASE_NAME, "res_index", ICUResourceBundle.ICU_DATA_CLASS_LOADER);
        return VersionInfo.getInstance(urb.getString("CLDRVersion"));
    }
    
    public synchronized static UCharacterSupport getInstance() {
        if(singleton == null) {
            singleton = new UCharacterSupport(); // does calculation
        }
        return singleton;
    }

    /**
     * Given a version map, find out the 'newest' (highest number) version info contained therein 
     * @param versionToSet
     * @return
     */
    public static VersionInfo getNewest(Map<VersionInfo, UnicodeSet> versionToSet) {
        return getNewest(versionToSet.keySet());
    }

    /**
     * Given a set of VersionInfos, find the newest.
     * @param set
     * @return
     */
    public static VersionInfo getNewest(Set<VersionInfo> set) {
        VersionInfo newest = null;
        for(VersionInfo vi : set) {
            if(newest == null || vi.compareTo(newest)>0) {
                newest = vi;
            }
        }
        return newest;
    }
    
    /**
     * Default prog: just print out the XML file
     * @param args
     */
    public static void main(String[] args) {
        UCharacterSupport.getInstance().print(System.out);
    }
    
    public static UnicodeSet setForLocale(ULocale loc) {
        LocaleData l = LocaleData.getInstance(loc);
        UnicodeSet set = l.getExemplarSet(0, LocaleData.ES_STANDARD);
        return set;
    }
    
    public Set<ULocale> localesSupportedBy(VersionInfo unicodeVersion) {
        Set<ULocale> locs = new TreeSet<ULocale>(getLocaleComparator());
        for(Entry<ULocale, VersionInfo> s : newestNeeded.entrySet()) {
            if(unicodeVersion.compareTo(s.getValue())>=0) {
                locs.add(s.getKey());
            }
        }
        return locs;
    }
    
    private Comparator<ULocale> getLocaleComparator() {
        final Comparator<ULocale> comp = new Comparator<ULocale>() {
                public int compare(ULocale lhs, ULocale rhs) {
                    return lhs.toString().compareTo(rhs.toString());
                }
            };
        return comp;
    }


    /**
     * Storage of info for all locs
     */
    private Map<ULocale,Map<VersionInfo,UnicodeSet>> allLocs = new TreeMap<ULocale,Map<VersionInfo,UnicodeSet>>(getLocaleComparator());
    private Map<ULocale,VersionInfo> newestNeeded = new TreeMap<ULocale, VersionInfo>(getLocaleComparator());
    /**
     * Private constructor. Just calculate and be done
     */
    private UCharacterSupport() {
        calc();
    }
    
    private void calc() {
        for(ULocale loc : ULocale.getAvailableLocales()) {
            calc(loc);
        }
    }

    private void calc(ULocale loc) {
        UnicodeSet set = setForLocale(loc);
        Map<VersionInfo,UnicodeSet> versionToSet = setToVersionedMap(set);
        allLocs.put(loc, versionToSet);
        VersionInfo newest = getNewest(versionToSet);
        newestNeeded.put(loc, newest);
    }

    /**
     *  Print XML Document all locales
     */
    public void print(PrintStream printStream) {
        System.out.println("<localeAges icuVersion=\""+IcuInfo.versionInfoToShortString(VersionInfo.ICU_VERSION)+"\" "+
                "unicodeVersion=\""+IcuInfo.versionInfoToShortString(UCharacter.getUnicodeVersion())+"\" cldrVersion=\""+IcuInfo.versionInfoToShortString(getCLDRVersion())+"\" "+
                "generatedOn=\""+new Date().toString()+"\">");
       for(ULocale loc : allLocs.keySet()) {
           print(printStream, loc);
       }
       printStream.println("</localeAges>");
    }

    /**
     * Calculate age (newest version required) for this locale.
     * 
     * @param loc
     * @return
     */
    public void print(PrintStream printStream, ULocale loc) {
        Map<VersionInfo,UnicodeSet> versionToSet = allLocs.get(loc);
        printStream.println("\t<locale id=\""+loc.getBaseName()+"\" newestChar=\""+IcuInfo.versionInfoToShortString(newestNeeded.get(loc))+"\">");
        for(VersionInfo vi : versionToSet.keySet()) {
            printStream.println("\t\t<exemplars version=\""+IcuInfo.versionInfoToShortString(vi)+"\">"+versionToSet.get(vi).toPattern(true)+"</exemplars>");
        }
        printStream.println("\t</locale>");
        
    }

    /**
     * Convert a UnicodeSet into a map of VersionInfo to UnicodeSet, where the key is the age of the character.
     * @param set
     * @return
     */
    public  Map<VersionInfo, UnicodeSet> setToVersionedMap(UnicodeSet set) {
        Map<VersionInfo,UnicodeSet> versionToSet = new TreeMap<VersionInfo,UnicodeSet>();
//        VersionInfo newest = null;
        for(int i : new UnicodeSetCharIterator(set)) {
            //System.err.println(" n: "+ i);
//            try {
                VersionInfo age = UCharacter.getAge(i);
//                if(newest == null) {
//                    newest = age;
//                } else if (age.compareTo(newest)>0) {
//                    newest = age;
//                }
                if(age == null) {
                  throw new InternalError(" -- for codepoint " + Integer.toHexString(i) + " : null age");
                } else {
                    UnicodeSet aSet = versionToSet.get(age);
                    if(aSet==null) {
                        aSet = new UnicodeSet();
                        versionToSet.put(age, aSet);
                    }
                    aSet.add(i);
                }
//            } catch(Throwable t) {
//                System.err.println(loc + " -- for codepoint " + Integer.toHexString(i) + " : " + t.toString());
//                return null;
//            }
        }
        return versionToSet;
    }

}
