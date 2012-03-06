/*
 *******************************************************************************
 * Copyright (C) 2012, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package com.ibm.icu.dev.tztools;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.ibm.icu.impl.ICUResourceBundle;
import com.ibm.icu.util.UResourceBundle;
import com.ibm.icu.util.UResourceBundleIterator;

public class MetazoneMappings {
    private SortedMap<String, List<MetaZoneData>> tz2mzs;

    public MetazoneMappings() {
        load();
    }

    private void load() {
        UResourceBundle bundle = UResourceBundle.getBundleInstance(
                ICUResourceBundle.ICU_BASE_NAME, "metaZones");
        UResourceBundle mapTimezones = bundle.get("metazoneInfo");
        UResourceBundleIterator tzitr = mapTimezones.getIterator();

        tz2mzs = new TreeMap<String, List<MetaZoneData>>();
        while (tzitr.hasNext()) {
            UResourceBundle tzmap = tzitr.next();
            UResourceBundleIterator tzmapitr = tzmap.getIterator();

            List<MetaZoneData> mzs = new LinkedList<MetaZoneData>();
            while (tzmapitr.hasNext()) {
                UResourceBundle mzarray = tzmapitr.next();
                String[] mzdata = null;
                if (mzarray.getSize() == 1) {
                    mzdata = new String[3];
                    mzdata[0] = mzarray.getString(0);
                    mzdata[1] = "1970-01-01 00:00";
                    mzdata[2] = "9999-12-31 23:59";
                } else {
                    mzdata = mzarray.getStringArray();
                }
                mzs.add(new MetaZoneData(mzdata[0], mzdata[1], mzdata[2]));
            }

            tz2mzs.put(tzmap.getKey(), mzs);
        }
    }

    private static class MetaZoneData {
        String mz;
        String start;
        String until;

        MetaZoneData(String mz, String start, String until) {
            this.mz = mz;
            this.start = start;
            this.until = until;
        }
    }

    public void write(PrintWriter pw, String baseIndent) throws IOException {
        SupplementalDataUpdater.println(pw, baseIndent, 0, "metazoneMappings{");
        for (Entry<String, List<MetaZoneData>> map : tz2mzs.entrySet()) {
            String key = map.getKey();
            SupplementalDataUpdater.println(pw, baseIndent, 1, "\"" + key + "\"{");
            List<MetaZoneData> mzlist = map.getValue();
            for (int idx = 0; idx < mzlist.size(); idx++) {
                MetaZoneData mzdata = mzlist.get(idx);
                SupplementalDataUpdater.println(pw, baseIndent, 2, "mz" + idx + "{");
                SupplementalDataUpdater.println(pw, baseIndent, 3, "\"" + mzdata.mz + "\",");
                SupplementalDataUpdater.println(pw, baseIndent, 3, "\"" + mzdata.start
                        + "\",");
                SupplementalDataUpdater.println(pw, baseIndent, 3, "\"" + mzdata.until
                        + "\",");
                SupplementalDataUpdater.println(pw, baseIndent, 2, "}");
            }
            SupplementalDataUpdater.println(pw, baseIndent, 1, "}");
        }
        SupplementalDataUpdater.println(pw, baseIndent, 0, "}");
    }

    public static void main(String... args) {
        MetazoneMappings mzinfo = new MetazoneMappings();
        PrintWriter out = new PrintWriter(System.out, true);
        try {
            mzinfo.write(out, "    ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
