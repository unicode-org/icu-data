/*
 *******************************************************************************
 * Copyright (C) 2012, International Business Machines Corporation and         *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 */
package com.ibm.icu.dev.tztools;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import com.ibm.icu.impl.ICUResourceBundle;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.UResourceBundle;
import com.ibm.icu.util.UResourceBundleIterator;

public class ZoneFormatting {
    private String targetVersion;
    private SortedMap<String, ZoneData> zoneDataMap;
    private SortedSet<String> multizones;

    public ZoneFormatting(String targetVersion) {
        this.targetVersion = targetVersion;
        load();
    }

    private static boolean READ_ALIASES_FROM_TYPEALIASES = false;

    private void load() {
        UResourceBundle bundle = UResourceBundle.getBundleInstance(
                ICUResourceBundle.ICU_BASE_NAME, "timezoneTypes");

        // First, collect alias data
        Map<String, SortedSet<String>> aliasMap = new HashMap<String, SortedSet<String>>();
        if (READ_ALIASES_FROM_TYPEALIASES) {
            UResourceBundle typeAlias = bundle.get("typeAlias");
            UResourceBundle timezoneAliases = typeAlias.get("timezone");
            UResourceBundleIterator aliasitr = timezoneAliases.getIterator();
    
            while (aliasitr.hasNext()) {
                UResourceBundle aliasEntry = aliasitr.next();
                String alias = aliasEntry.getKey().replace(':', '/');
                String canonical = aliasEntry.getString();
                SortedSet<String> aliasSet = aliasMap.get(canonical);
                if (aliasSet == null) {
                    aliasSet = new TreeSet<String>();
                }
                aliasSet.add(alias);
                aliasMap.put(canonical, aliasSet);
            }
        } else {
            // Includes ICU specific alias, not available in CLDR
            String[] allIDs = TimeZone.getAvailableIDs();
            for (String id : allIDs) {
                String canonical = TimeZone.getCanonicalID(id);
                if (!canonical.equals(id)) {
                    SortedSet<String> aliasSet = aliasMap.get(canonical);
                    if (aliasSet == null) {
                        aliasSet = new TreeSet<String>();
                    }
                    aliasSet.add(id);
                    aliasMap.put(canonical, aliasSet);
                }
            }
        }

        // Now, create canonical zone data and multi-regions zones
        UResourceBundle typeMap = bundle.get("typeMap");
        UResourceBundle tzTypes = typeMap.get("timezone");
        UResourceBundleIterator tzTypesItr = tzTypes.getIterator();

        zoneDataMap = new TreeMap<String, ZoneData>();
        Set<String> regions = new HashSet<String>();
        multizones = new TreeSet<String>();

        while (tzTypesItr.hasNext()) {
            String canonicalID = tzTypesItr.next().getKey().replace(':', '/');
            String region;
            if (canonicalID.equals("Etc/Unknown")) {
                region = "001"; // special case
            } else {
                region = TimeZone.getRegion(canonicalID);    // we may use zoneinfo64.res directly
            }
            if (regions.contains(region)) {
                multizones.add(region);
            } else {
                regions.add(region);
            }
            SortedSet<String> aliases = aliasMap.get(canonicalID);
            zoneDataMap.put(canonicalID, new ZoneData(region, aliases));
        }

    }

    private static class ZoneData {
        String territory;
        SortedSet<String> aliases;

        ZoneData(String territory, SortedSet<String> aliases) {
            this.territory = territory;
            this.aliases = aliases;
        }
    }

    public void write(PrintWriter pw, String baseIndent) throws IOException {
        SupplementalDataUpdater.println(pw, baseIndent, 0, "zoneFormatting{");
        for (Entry<String, ZoneData> entry : zoneDataMap.entrySet()) {
            String canonical = entry.getKey();
            String zonekey = canonical.replace('/', ':');
            ZoneData zdata = entry.getValue();

            SupplementalDataUpdater.println(pw, baseIndent, 1, "\"" + zonekey + "\"{");
            if (targetVersion.equals("42")) {
                SupplementalDataUpdater.println(pw, baseIndent, 2, "canonical{\"" + canonical + "\"}");
            }
            SupplementalDataUpdater.println(pw, baseIndent, 2, "territory{\"" + zdata.territory + "\"}");
            if (zdata.aliases != null) {
                SupplementalDataUpdater.println(pw, baseIndent, 2, "aliases{");
                for (String alias : zdata.aliases) {
                    SupplementalDataUpdater.println(pw, baseIndent, 3, "\"" + alias + "\",");
                }
                SupplementalDataUpdater.println(pw, baseIndent, 2, "}");
            }
            SupplementalDataUpdater.println(pw, baseIndent, 1, "}");
        }
        // multizone
        SupplementalDataUpdater.println(pw, baseIndent, 1, "multizone{");
        for (String region : multizones) {
            SupplementalDataUpdater.println(pw, baseIndent, 2, "\"" + region + "\",");
        }
        SupplementalDataUpdater.println(pw, baseIndent, 1, "}");
        SupplementalDataUpdater.println(pw, baseIndent, 0, "}");
    }

    public static void main(String... args) {
        ZoneFormatting zf = new ZoneFormatting(args[0]);
        PrintWriter out = new PrintWriter(System.out, true);
        try {
            zf.write(out, "    ");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
