import java.util.*;
import java.io.*;
import java.text.*;

public class UCMValidityParser {
    static final String[] STATE_NAMES = {
        "FIRST",
        "SECOND",
        "THIRD",
        "FOURTH",
        "FIFTH",
        "SIXTH",
        "SEVENTH",
        "EIGHTH",
        "NINTH",
        "TENTH",
        "ELEVENTH",
        "TWELFTH",
        "THIRTEENTH",
        "FOURTEENTH",
        "FIFTEENTH",
        "SIXTEENTH",
        "SEVENTEENTH",
        "EIGHTTEENTH",
        "NINETEENTH",
        "TWENTIETH",
        "",
        "UNASSIGNED",
        "INVALID",
        "VALID"
    };
    
    static final int VALID_INDEX      = STATE_NAMES.length - 1;
    static final int INVALID_INDEX    = STATE_NAMES.length - 2;
    static final int UNASSIGNED_INDEX = STATE_NAMES.length - 3;
    static final int MAX_STATES       = STATE_NAMES.length - 4;
    
    private static final Map ALIAS_MAP  = createAliasMap();
    
    static final int ALIAS_REAL         = 0;
    static final int ALIAS_ORIGINAL     = 1;
    static final int ALIAS_PREFERRED    = 2;
    static final int ALIAS_INFO_MAX     = 3;

    /**
     * UCM files has a 'feature' that the last overlapping state
     * transition overrides the previous state transitions.  This
     * is on a per line basis.  The XML format does not allow that, and this
     * simplifies the ranges.
     * TODO: Eleminate the unused states that can be created from this simplification process.
     */
    static String fixRanges(String validity) {
        StringTokenizer lineTok = new StringTokenizer(validity,
                                                      "\n\r\f");
        StringWriter xmlValidity = new StringWriter();
        PrintWriter xmlValidityBuf = new PrintWriter(new BufferedWriter(xmlValidity));
        int currState = 0;

        // Quick and dirty way to do range checking
        while (lineTok.hasMoreTokens()) {
            String line = lineTok.nextToken();
            StringTokenizer strTok = new StringTokenizer(line, ", \t");
            byte stateArr[] = new byte[256];
            char actionArr[] = new char[256];
            
            while (strTok.hasMoreTokens()) {
                String state = strTok.nextToken();
                int dashPos = state.indexOf('-');
                int colonPos = state.indexOf(':');
                int dotPos = state.indexOf('.');
                int startRange;
                int endRange;
                byte nextState;
                char action;

                if (dotPos < 0) {
                    action = 1; //VALID
                    dotPos = state.length();
                }
                else {
                    if (dotPos + 1 >= state.length()) {
//                        System.out.println("Warning: Don't know how to handle VALID final state");
                        action = 2;     // VALID FINAL
                    }
                    else {
                        action = state.charAt(dotPos + 1);
                    }
                }
                if (colonPos < 0) {
                    colonPos = dotPos;
                    nextState = -1;     // VALID
                }
                else {
                    // add one so that -> 0 is not ignored
                    nextState = Byte.parseByte(state.substring(colonPos + 1, dotPos), 16);
                    nextState++;
                }
                if (dashPos < 0 || dashPos > colonPos) {
                    try {
                        startRange = Integer.parseInt(state.substring(0, colonPos), 16);
                    }
                    catch (NumberFormatException e) {
                        xmlValidityBuf.print(state.substring(0, colonPos));
                        continue;
                    }
                    endRange = startRange;
                    dashPos = colonPos;
                }
                else {
                    startRange = Integer.parseInt(state.substring(0, dashPos), 16);
                    endRange = Integer.parseInt(state.substring(dashPos + 1, colonPos), 16);
                }
                if (startRange < 0 || 0xFF < startRange) {
                    System.out.println("Warning: validity out of range! startRange=" + Integer.toHexString(startRange));
                }
                if (endRange < 0 || 0xFF < endRange) {
                    System.out.println("Warning: validity out of range! endRange=" + Integer.toHexString(endRange));
                }
//                System.out.println("startRange=" + startRange + " endRange=" + endRange);
                for (int idx = startRange; idx <= endRange; idx++) {
                    if (stateArr[idx] != 0) {
                        System.out.println("Warning: Overlapping validity range at "
                            + Integer.toHexString(idx) + " in state " + currState);
                    }
                    stateArr[idx] = nextState;
                    actionArr[idx] = action;
                }
            }
            int idx = 0;
            while (idx < 0xFF && stateArr[idx] == 0) {
                idx++;
//                System.out.println("Skipping " + idx);
            }
            while (idx <= 0xFF) {
                // skip empty values
                int startIdx = idx;
                while (idx < 0xFF
                 && stateArr[idx] == stateArr[idx + 1]
                 && actionArr[idx] == actionArr[idx + 1])
                {
                    idx++;
                }
                if (actionArr[idx] > 0) {
                    if (startIdx <= 0xF) {
                        xmlValidityBuf.print('0');
                    }
                    xmlValidityBuf.print(Integer.toHexString(startIdx));
                    if (startIdx != idx) {
                        xmlValidityBuf.print('-');
                        if (idx <= 0xF) {
                            xmlValidityBuf.print('0');
                        }
                        xmlValidityBuf.print(Integer.toHexString(idx));
                    }
                    if (stateArr[idx] != -1) {
                        xmlValidityBuf.print(':' + Integer.toHexString(stateArr[startIdx] - 1));
                    }
                    if (actionArr[idx] > 1) {
                        xmlValidityBuf.print('.');
                        if (actionArr[idx] > 2) {
                            xmlValidityBuf.print(actionArr[startIdx]);
                        }
                    }
                }
                idx++;
                while (idx <= 0xFF && stateArr[idx] == 0) {
                    idx++;
//                    System.out.println("Skipping " + idx);
                }
                if (idx <= 0xFF) {
                    xmlValidityBuf.print(',');
                }
//                xmlValidityBuf.flush();
//                System.out.println(xmlValidity.toString());
            }
            if (lineTok.hasMoreTokens()) {
                xmlValidityBuf.println();
            }
            currState++;
        }
        xmlValidityBuf.flush();

        return xmlValidity.toString();
    }

    /** Do not give this function an empty string. Empty strings will be ignored. */    
    static String getXMLValidity(String rawValidity) {
        String validity = fixRanges(rawValidity);

        StringTokenizer lineTok = new StringTokenizer(validity,
                                                      "\n\r\f");
        StringWriter xmlValidity = new StringWriter();
        PrintWriter xmlValidityBuf = new PrintWriter(new BufferedWriter(xmlValidity));
        int currState = 0;
        int nextState = 0;
        String startRange;
        String endRangeElement;

//        System.out.println("before");
//        System.out.println(rawValidity);
//        System.out.println("after");
//        System.out.println(validity);
        
        xmlValidityBuf.println(" <validity>");
        while (lineTok.hasMoreTokens() && currState < MAX_STATES) {
            String line = lineTok.nextToken();
            StringTokenizer strTok = new StringTokenizer(line, ", \t");
            boolean surrogates = false;

            if (line.startsWith("initial")) {
                System.out.println("Warning: Don't know how to handle initial keyword");
            }
            else if (line.startsWith("surrogates")) {
                surrogates = true;
            }
            while (strTok.hasMoreTokens()) {
                String state = strTok.nextToken();
                int dashPos = state.indexOf('-');
                int colonPos = state.indexOf(':');
                int dotPos = state.indexOf('.');
                char action = 0;

                String maxElement = surrogates ? " max=\"10FFFF\"" : " max=\"FFFF\"";
                
                if (dotPos < 0) {
                    dotPos = state.length();
                }
                else {
                    if (dotPos + 1 >= state.length()) {
                        System.out.println("Warning: Don't know how to handle VALID final state");
                    }
                    else {
                        action = state.charAt(dotPos + 1);
                        
                        if (action == 'p') {
                            maxElement = " max=\"10FFFF\"";
                        }
                        else if (action == 's') {
                            System.out.println("Warning: Don't know how to handle state change");
                        }
                        else if (action != 'i' && action != 'u') {
                            System.out.println("Warning: unknown action: " + action);
                        }
                    }
                }
                if (colonPos < 0) {
                    colonPos = dotPos;
                    if (action == 'i') {
                        nextState = INVALID_INDEX;
                    }
                    else if (action == 'u') {
                        nextState = UNASSIGNED_INDEX;
                    }
                    else {
                        nextState = STATE_NAMES.length - 1;     // VALID
                    }
                }
                else {
                    nextState = Integer.parseInt(state.substring(colonPos + 1, dotPos), 16);
                }
                if (dashPos < 0 || dashPos > colonPos) {
                    dashPos = colonPos;
                    endRangeElement = "";
                }
                else {
                    endRangeElement = " e=\"" + state.substring(dashPos + 1,
                                      colonPos).toUpperCase() + "\"";
                }

                if (dashPos > colonPos || colonPos > dotPos) {
                    System.out.println("Warning: encountered unusable validity format: " + state);
                    break;
                }
                
                startRange = state.substring(0, dashPos);
                xmlValidityBuf.println("  <state type=\"" + STATE_NAMES[currState]
                                     + "\" next=\"" + STATE_NAMES[nextState]
                                     + "\" s=\"" + startRange.toUpperCase()
                                     + "\"" + endRangeElement
                                     + maxElement + "/>");
            }
            currState++;
        }
        xmlValidityBuf.println(" </validity>");
        xmlValidityBuf.flush();

        if (lineTok.hasMoreTokens()) {
            System.out.println("Warning: encountered too many states!");
        }
        return xmlValidity.toString();
    }

    /**
     * Tables come straight from the UCM files
     */    
    static String getCodepageValidity(String codepage) {
        if (codepage.equals("ibm-1363_P110-2000")
         || codepage.equals("ibm-1363_P11B-2000")
         || codepage.equals("ibm-1386")
         || codepage.equals("ibm-950")) {
            return "00-7f, 81-fe:1\n40-7e, 80-fe";
        }
        else if (codepage.equals("ibm-1370")) {
            return "00-80, 81-fe:1\n40-7e, 81-fe";
        }
        else if (codepage.equals("ibm-1383")) {
            return "00-9f, a1-fe:1\na1-fe";
        }
        else if (codepage.equals("ibm-33722")) {
//            return "0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\na1-fe\na1-e4\na1-fe:1, a1:4, a3-af:4, b6:4, d6:4, da-db:4, ed-f2:4\na1-fe.u";
            // simplified validity table
            return "00-8d,8e:2,8f:3,90-9f,a1-fe:1\n"
                 + "a1-fe\n"
                 + "a1-e4\n"
                 + "a1:4,a2:1,a3-af:4,b0-b5:1,b6:4,b7-d5:1,d6:4,d7-d9:1,da-db:4,dc-ec:1,ed-f2:4,f3-fe:1\n"
                 + "a1-fe.u";
        }
//        else if (codepage.equals("ibm-942")) {
        else if (codepage.equals("ibm-942_P12A-2000")
              || codepage.equals("ibm-942_P120-2000")) {
            return "00-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n40-7e, 80-fc";
        }
        else if (codepage.equals("ibm-943_P14A-2000")
              || codepage.equals("ibm-943_P130-2000")) {
            return "00-7f, 81-9f:1, a0-df, e0-fc:1\n40-7e, 80-fc";
        }
        else if (codepage.equals("ibm-944")) {
            return "00-80, 81-bf:1, c0-ff\n40-7e, 80-fe";
        }
        else if (codepage.equals("ibm-949_P110-2000")
              || codepage.equals("ibm-949_P11A-2000")) {
            return "00-84, 8f-fe:1\n40-7e, 80-fe";
        }
        else if (codepage.equals("ibm-964")) {
//            return "0-8d, 8e:2, 90-9f, a1-fe:1, aa-c1:5, c3:5, fe:5\na1-fe\na1-b0:3, a1:4, a2:8, a3-ab:4, ac:7, ad:6, ae-b0:4\na1-fe:1\na1-fe:5\na1-fe.u\na1-a4:1, a5-fe:5\na1-e2:1, e3-fe:5\na1-f2:1, f3-fe:5";
            // simplified validity table
            return "00-8d, 8e:2, 90-9f, a1-a9:1, aa-c1:5, c2:1, c3:5, c4-fd:1, fe:5\n"
                 + "a1-fe\n"
                 + "a1:4, a2:8, a3-ab:4, ac:7, ad:6, ae-b0:4\n"
                 + "a1-fe:1\n"
                 + "a1-fe:5\n"
                 + "a1-fe.u\n"
                 + "a1-a4:1, a5-fe:5\n"
                 + "a1-e2:1, e3-fe:5\n"
                 + "a1-f2:1, f3-fe:5";
        }
        else if (codepage.equals("ibm-970")) {
            return "00-9f, a1-fe:1\na1-fe";
        }
        return null;
    }

    private static Map createAliasMap() {
        HashMap map = new HashMap();
        String line;
        String mainName, aliasName, lastAliasName;
        BufferedReader in = null;
        String[] newValue;
        
        try {
            in = new BufferedReader(new FileReader("convrtrs.txt"), 4*1024);
            for (;;) {
                line = in.readLine();
                if (line == null)
                    break;
                // strip comments, continue if empty
                int commentPos = line.indexOf('#');
                if (commentPos >= 0)
                    line = line.substring(0,commentPos).trim();
                if (line.length() == 0)
                    continue;

                StringTokenizer strTok = new StringTokenizer(line);
                if (strTok.hasMoreTokens()) {
                    mainName = strTok.nextToken();
                    if (!map.containsKey(mainName.toLowerCase())) {
                        newValue = new String[ALIAS_INFO_MAX];
                        newValue[ALIAS_REAL] = removeIgnoreableAliasChars(mainName.toLowerCase());
                        newValue[ALIAS_ORIGINAL] = mainName;
                        lastAliasName = newValue[ALIAS_REAL];
                        map.put(lastAliasName, newValue);
                        mainName = lastAliasName;
                    }
                    else {
                        // No aliases or already aliased
                        if (map.containsKey(mainName)) {
                            System.out.println("Warning: " + mainName + "is already aliased");
                        }
                        continue;
                    }
                }
                else {
                    // Never should have happened anyway.
                    continue;
                }
                
                // get Aliases
                while (strTok.hasMoreTokens()) {
                    aliasName = strTok.nextToken();
                    if (map.containsKey(aliasName.toLowerCase())) {
                        newValue = new String[ALIAS_INFO_MAX];
                        newValue[ALIAS_REAL] = mainName;
                        newValue[ALIAS_ORIGINAL] = aliasName;
                        // Don't lower case it
                        lastAliasName = aliasName;
                        if (map.containsKey(lastAliasName)) {
                            System.out.println("Warning: " + lastAliasName + " is already aliased to "
                             + ((String[])map.get(lastAliasName))[ALIAS_ORIGINAL]);
                        }
                        else {
                            System.out.println("Warning: " + aliasName + " is aliased to similar alias name "
                             + ((String[])map.get(aliasName.toLowerCase()))[ALIAS_ORIGINAL]);
                            map.put(lastAliasName, newValue);
                        }
                    }
                    else if (aliasName.equals("{")) {
                        StringBuffer preferredBy = new StringBuffer();
                        String preferredName;
                        boolean firstName = true;
                        while (strTok.hasMoreTokens()) {
                            preferredName = strTok.nextToken();
                            if (preferredName.equals("}")) {
                                newValue = (String[])map.get(lastAliasName);
                                newValue[ALIAS_PREFERRED] = preferredBy.toString();
//                                System.out.println(newValue[ALIAS_PREFERRED] + " name: " + newValue[ALIAS_ORIGINAL]);
                                break;
                            }
                            else {
                                if (firstName) {
                                    firstName = false;
                                }
                                else {
                                    preferredBy.append(' ');
                                }
                                preferredBy.append(preferredName);
                            }
                        }
                        // If we got here without the "}" ignore the name
                    }
                    else {
                        newValue = new String[ALIAS_INFO_MAX];
                        newValue[ALIAS_REAL] = mainName;
                        newValue[ALIAS_ORIGINAL] = aliasName;
                        lastAliasName = removeIgnoreableAliasChars(aliasName.toLowerCase());
                        map.put(lastAliasName, newValue);
//                        System.out.println(aliasName + " -> " + lastAliasName + " -> " + mainName);
                    }
                }
//                System.out.println(line);
            }
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return map;
    }

    private static String removeIgnoreableAliasChars(String alias) {
        StringBuffer newAlias = new StringBuffer();
        
        for (int idx = 0; idx < alias.length(); idx++) {
            if (alias.charAt(idx) != '-' && alias.charAt(idx) != '_') {
                newAlias.append(alias.charAt(idx));
            }
        }
        return newAlias.toString();
    }

    /**
     * Does this codepage name have an alias even with guesssing?
     */
    static boolean hasAlias(String codepage) {
        return getAlias(codepage) != null;
    }

    /**
     * Is any guessing involved with getting this alias
     */
    static boolean hasDirectAlias(String codepage) {
        return ALIAS_MAP.get(removeIgnoreableAliasChars(codepage)) != null;
    }

    /**
     * Get the alias name for a codepage. Some guessing might be involved.
     */
    static String getAlias(String codepage) {
        if (codepage == null) {
            return null;
        }
    
        String[] realCPName = (String[])ALIAS_MAP.get(codepage);
        if (realCPName == null) {
            int lastDash = codepage.lastIndexOf('-');
            int firstDash = codepage.indexOf('-');
            String versionlessStr;
            
            if (firstDash != lastDash && firstDash != -1) {
                versionlessStr = codepage.substring(0, lastDash);
                realCPName = (String[])ALIAS_MAP.get(versionlessStr);
                if (realCPName == null) {
                    realCPName = (String[])ALIAS_MAP.get(removeIgnoreableAliasChars(versionlessStr));
                }
            }
            else {  // else missing one or both dashes.
                versionlessStr = codepage;
            }

            if (realCPName == null) {
                realCPName = (String[])ALIAS_MAP.get(removeIgnoreableAliasChars(codepage));
                if (realCPName == null) {
                    // Really special cases
                    if (codepage.startsWith("windows")) {
                        int idx = 0;
                        while (idx < codepage.length()
                               && (codepage.charAt(idx) < '0' || codepage.charAt(idx) > '9')) {
                            idx++;
                        }
    
                        realCPName = (String[])ALIAS_MAP.get("cp"
                         + codepage.substring(idx, codepage.length()));
                        if (realCPName == null && lastDash != -1) {
                            realCPName = (String[])ALIAS_MAP.get("cp"
                             + codepage.substring(idx, lastDash));
                        }
                    }
                    else if (codepage.equals("zh_cn.euc")) {
                        return "ibm-1383";
                    }
                    else if (codepage.equals("zh_tw_euc")) {
                        return "ibm-964";
                    }
                }
            }
        }
        if (realCPName != null) {
//            System.out.println("real: " + realCPName[ALIAS_REAL]);
//            System.out.println("original: " + realCPName[ALIAS_ORIGINAL]);
//            System.out.println("key real: " + ((String[])ALIAS_MAP.get(realCPName[ALIAS_REAL]))[ALIAS_REAL]);
//            System.out.println("key original: " + ((String[])ALIAS_MAP.get(realCPName[ALIAS_REAL]))[ALIAS_ORIGINAL]);
            return ((String[])ALIAS_MAP.get(realCPName[ALIAS_REAL]))[ALIAS_ORIGINAL];
        }
        else {
            return null;
        }
    }

    static String getValidityType(String platformCodepageName) {
        return null;
    }
}