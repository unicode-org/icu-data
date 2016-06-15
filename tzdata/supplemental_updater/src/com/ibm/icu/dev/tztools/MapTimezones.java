// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License

/*
 *******************************************************************************
 * Copyright (C) 2012, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package com.ibm.icu.dev.tztools;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.ibm.icu.impl.ICUData;
import com.ibm.icu.util.UResourceBundle;
import com.ibm.icu.util.UResourceBundleIterator;

public class MapTimezones {
    private String targetVersion;
    private SortedMap<String, SortedMap<String, String>> metaZones;
    private SortedMap<String, SortedMap<String, String>> windowsZones;

    public MapTimezones(String targetVersion) {
        this.targetVersion = targetVersion;
        load();
    }

    private void load() {
        metaZones = loadMapZones("metaZones");
        windowsZones = loadMapZones("windowsZones");
    }

    private SortedMap<String, SortedMap<String, String>> loadMapZones(String parent) {
        UResourceBundle bundle = UResourceBundle.getBundleInstance(
                ICUData.ICU_BASE_NAME, parent);
        UResourceBundle mapTimezones = bundle.get("mapTimezones");
        UResourceBundleIterator itr = mapTimezones.getIterator();

        SortedMap<String, SortedMap<String, String>> mapZones = new TreeMap<String, SortedMap<String, String>>();
        while (itr.hasNext()) {
            UResourceBundle mapdata = itr.next();
            UResourceBundleIterator tzitr = mapdata.getIterator();

            SortedMap<String, String> tzmap = new TreeMap<String, String>();
            while (tzitr.hasNext()) {
                UResourceBundle entry = tzitr.next();
                tzmap.put(entry.getKey(), entry.getString());
            }

            mapZones.put(mapdata.getKey(), tzmap);
        }
        return mapZones;
    }

    public void write(PrintWriter pw, String baseIndent) throws IOException {
        SupplementalDataUpdater.println(pw, baseIndent, 0, "mapTimezones{");
        SupplementalDataUpdater.println(pw, baseIndent, 1, "metazones{");

        SortedMap<String, String> oldMetaZonesData = new TreeMap<String, String>();
        for (Entry<String, SortedMap<String, String>> map : metaZones.entrySet()) {
            String mz = map.getKey();
            for (Entry<String, String> tzmap : map.getValue().entrySet()) {
                String region = tzmap.getKey();
                String tzid = tzmap.getValue();
                oldMetaZonesData.put("meta:" + mz + "_" + region, tzid);
            }
        }
        for (Entry<String, String> oldMap : oldMetaZonesData.entrySet()) {
            SupplementalDataUpdater.println(pw, baseIndent, 2, "\"" + oldMap.getKey() + "\"{\"" + oldMap.getValue() + "\"}");
        }
        SupplementalDataUpdater.println(pw, baseIndent, 1, "}");

        SupplementalDataUpdater.println(pw, baseIndent, 1, "windows{");
        if (targetVersion.equals("38")) {
            // 38 style data
            SortedMap<String, String> wmap38 = new TreeMap<String, String>();
            for (Entry<String, SortedMap<String, String>> map : windowsZones.entrySet()) {
                String wz = map.getKey();
                for (Entry<String, String> tzmap : map.getValue().entrySet()) {
                    String region = tzmap.getKey();
                    if (!region.equals("001")) {
                        continue;
                    }
                    String tzid = tzmap.getValue();
                    // 38 used tzid as key
                    String key38 = tzid.replace('/', ':');
                    // 38 used Windows zid without "Standard Time"
                    final String suffix = " Standard Time";
                    String val38;
                    if (wz.endsWith(" Standard Time")) {
                        val38 = wz.substring(0, wz.length() - suffix.length());
                    } else {
                        val38 = wz;
                    }
                    wmap38.put(key38, val38);
                }
            }
            for (Entry<String, String> entry38 : wmap38.entrySet()) {
                SupplementalDataUpdater.println(pw, baseIndent, 2, "\"" + entry38.getKey() + "\"{\"" + entry38.getValue() + "\"}");
            }
        } else {
            for (Entry<String, SortedMap<String, String>> map : windowsZones.entrySet()) {
                String wz = map.getKey();
                for (Entry<String, String> tzmap : map.getValue().entrySet()) {
                    String region = tzmap.getKey();
                    if (!region.equals("001")) {
                        continue;
                    }
                    String tzid = tzmap.getValue();
                    SupplementalDataUpdater.println(pw, baseIndent, 2, "\"" + wz + "\"{\"" + tzid + "\"}");
                }
            }
        }
        SupplementalDataUpdater.println(pw, baseIndent, 1, "}");
        SupplementalDataUpdater.println(pw, baseIndent, 0, "}");
    }

    public static void main(String... args) {
        MapTimezones mapzs = new MapTimezones(args[0]);
        PrintWriter out = new PrintWriter(System.out, true);
        try {
            mapzs.write(out, "    ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
