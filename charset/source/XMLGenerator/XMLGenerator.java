import java.util.*;
import java.io.*;
import java.text.*;

public class XMLGenerator {
    static final int ERROR_LIMIT = 100;
    static final String INDEX_DIR = "../../";
    static final String XML_DIR = INDEX_DIR + "CharMaps-XML/";
//    static final String SOURCE_DIR = "C:/Development/icu/data/";
//    static final String SOURCE_DIR = INDEX_DIR + "CharMaps-UCM/";
    static final String SOURCE_DIR = INDEX_DIR + "data/ucm/";
//    static final String SOURCE_DIR = INDEX_DIR + "ICU-data-UCM/";
//    static final String SOURCE_DIR = INDEX_DIR + "CharMaps-UCM.orig/";

    static boolean verboseOutput = true;

    static final int VALIDITY_EMPTY         = 0;
    static final int VALIDITY_7BIT          = 1;
    static final int VALIDITY_8BIT          = 2;
    static final int VALIDITY_DBCS          = 3;
    static final int VALIDITY_EBCDIC_STATEFUL = 4;
    static final int VALIDITY_CUSTOM        = 5;
    static final int VALIDITY_UNKNOWN       = 6;
    
    public static void main(String[] args) {
        parseParams(args);

        File file = new File(SOURCE_DIR);
        String[] fileList = file.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".ucm");
            }
        });
        System.out.println("Files to process: " + fileList.length);
        for (int idx = 0; idx < fileList.length; idx++) {
            String filename = fileList[idx];
            //String number = getFileNumber(filename);
            File temp = new File(SOURCE_DIR + filename);
            BufferedReader in = null;
            try {
                System.out.println(
                    DateFormat.getInstance().format(new Date(temp.lastModified()))
                    + "; " + NumberFormat.getInstance().format(temp.length())
                    + "; " + temp.getCanonicalFile());
                String canonicalName = getBaseFilename(filename); // "windows-" + number + "-NT4.0.5";
                if (!isValidCharsetName(canonicalName)) {
                    System.out.println("Warning: The filename format should be <source>-<name_on_source>-<version>.ucm");
                }
                        
                in = new BufferedReader(
                    new FileReader(SOURCE_DIR + filename),
                    4*1024);
                generateFromIBMFile(canonicalName, in);
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Done");
    }

    private static void parseParams(String[] args) {
        for (int idx = 0; idx < args.length; idx++) {
            if (args[idx].equals("-m")) {
                verboseOutput = false;
            }
            else if (args[idx].equals("-h")) {
                System.out.println("-m  Minimum XML data. Do not show things like the \"c=\" attribute.");
                System.out.println("-h  This help message");
            }
        }
    }

    public static void generateFromIBMFile(String name, BufferedReader in) 
      throws java.io.IOException {
        int count = 0;
        byte[] bytes = new byte[10]; // way larger than max bytes/cp. Usually isn't more than 4
        byte[] subChars = new byte[10]; // way larger than max bytes/cp. Usually isn't more than 4
        int subCharCount = 0;
        String line;
        boolean inCHARMAP = false;
        int errorCount = 0;
        int stateNum = 0;
        int validityType = VALIDITY_EMPTY;
        String uconvName = null;
        String csName = null;
        StringWriter customValidity = new StringWriter();
        StringWriter normalMap = new StringWriter(8196);
        StringWriter fallbackToBytes = new StringWriter(8196);
        StringWriter fallbackToCP = new StringWriter(8196);
        PrintWriter customValidityBuf = new PrintWriter(new BufferedWriter(customValidity));
        PrintWriter normalMapBuf = new PrintWriter(new BufferedWriter(normalMap));
        PrintWriter fallbackToBytesBuf = new PrintWriter(new BufferedWriter(fallbackToBytes));
        PrintWriter fallbackToCPBuf = new PrintWriter(new BufferedWriter(fallbackToCP));
        
        for (;;) {
            line = in.readLine();
//            if (DEBUG) System.out.println(line);
            if (line == null)
                break;
            // strip comments, continue if empty
            int commentPos = line.indexOf('#');
            if (commentPos >= 0)
                line = line.substring(0,commentPos).trim();
            else
                line = line.trim();
            if (line.length() == 0)
                continue;
            // ugly format: 
            // <U0000>         \x00     0
            // <UFFFD>         \xFC\xFC  1
            try {
                if (!inCHARMAP) {
                    if (line.startsWith("<subchar>")) {
                        String subChar = line.substring("<subchar>".length(), line.length()).trim();
                        for (int i = 0; i < subChar.length(); i+=4) {
                            if (subChar.charAt(i) != '\\' || subChar.charAt(i+1) != 'x') {
                                throw new Exception("Byte values must be '\\x99'");
                            }
                            subChars[subCharCount++] = (byte)Integer.parseInt(subChar.substring(i+2,i+4), 16);
                        }
                        
                    }
                    else if (line.startsWith("<code_set_name>")) {
                        int nameEnd = line.indexOf('>');
                        String nameLine = line.substring(nameEnd + 1, line.length()).trim();

                        if (nameLine.charAt(0) != '\"' || nameLine.charAt(nameLine.length() - 1) != '\"') {
                            System.out.println("Warning: <code_set_name> " + nameLine + " not quoted");
                        }
                        csName = nameLine.substring(1, nameLine.length() - 1).toLowerCase();
                        //System.out.println(csName);
                    }
                    else if (line.startsWith("<icu:state>")) {
                        int icuStateEnd = line.indexOf('>');
                        String stateLine = line.substring(icuStateEnd + 1, line.length()).trim();

                        // Store it for parsing later
                        if (stateLine.equals("")) {
                            // ICU assumption
                            customValidityBuf.println("00-ff.i");
                        }
                        else {
                            customValidityBuf.println(stateLine);
                        }
                        validityType = VALIDITY_CUSTOM;
                    }
                    else if (line.startsWith("<uconv_class>")) {
                        int uconvClassEnd = line.indexOf('>');
                        uconvName = line.substring(uconvClassEnd + 1, line.length()).trim();

                        if (validityType != VALIDITY_CUSTOM) {
                            if (validityType != VALIDITY_EMPTY) {
                                System.out.println("Warning: Encountered more than one <uconv_class> tags");
                            }
                            if (uconvName.equals("\"DBCS\""))
                            {
                                validityType = VALIDITY_DBCS;
                            }
                            else if (uconvName.equals("\"EBCDIC_STATEFUL\""))
                            {
                                System.out.println("Warning: Can not handle EBCDIC_STATEFUL at this time");
                                validityType = VALIDITY_EBCDIC_STATEFUL;
                            }
                            // For MBCS the state table better be included!
                        }
                    }
                    else if (line.equals("CHARMAP")) {
                        inCHARMAP = true;
                    }
                    continue;
                }
                if (line.equals("END CHARMAP"))
                    break;
                int unicodeEnd = line.indexOf('>');
                if (!line.startsWith("<U") || unicodeEnd < 6 || unicodeEnd > 8) {
                    throw new Exception("Line must start with '<U9999>'");
                }
                
                String cpStr = line.substring(2, unicodeEnd);
                int cp = Integer.parseInt(cpStr, 16);
                if ('\uD800' <= cp && cp <= '\uDFFF') {
                    System.out.println("Warning: Encountered single surrogate pair " + cp);
                }
                
                int type, typeLen;
                char typeDelimiter = line.charAt(line.length() - 2);
                if (typeDelimiter == '|') {
                    typeLen = 2;
                    type = line.charAt(line.length() - 1) - '0';
                } else if (typeDelimiter == ' ') {
                    typeLen = 1;
                    type = line.charAt(line.length() - 1) - '0';
                } else {
                    typeLen = 0;
                    type = 0;        // round trip mapping by default
                }
                
                if (type < 0 || type > 3) {
                    throw new Exception("Type out of bounds");
                }
                
                
                String byteStr = line.substring(unicodeEnd + 1, line.length() - typeLen).trim();
                int bufferCount = 0;
                for (int i = 0; i < byteStr.length(); i+=4) {
                    if (byteStr.charAt(i) != '\\' || byteStr.charAt(i+1) != 'x') {
                        throw new Exception("Byte values must be '\\x99'");
                    }
                    bytes[bufferCount++] = (byte)Integer.parseInt(byteStr.substring(i+2, i+4), 16);
                }
                
                if (validityType <= VALIDITY_8BIT) {
                    if (bufferCount == 1) {
                        if (bytes[0] < 0) {
                            validityType = VALIDITY_8BIT;
                        }
                        else if (validityType == VALIDITY_EMPTY) {
                            validityType = VALIDITY_7BIT;
                        }
                        // else it's already 7BIT or 8BIT
                    }
                    else if (validityType == VALIDITY_EMPTY && bufferCount == 2) {
                        System.out.println("Warning: Assuming this is a DBCS codepage");
                        validityType = VALIDITY_DBCS;   // It might be DBCS
                    }
                    else {
                        validityType = VALIDITY_UNKNOWN;
                    }
                } else if (validityType == VALIDITY_DBCS && bufferCount != 2) {
                    if (uconvName != null) {
                        // The tag was wrong
                        System.out.println("Warning: Found non-double byte "
                                           + byteName(bytes, bufferCount)
                                           + " in DBCS. Will now assume this is non-DBCS");
                    }
                    // else missing the <uconv_class> tag.
                    validityType = VALIDITY_UNKNOWN;
                }
                
                // we now have: cp, bytes, and type
                if (type == 2) {
                    if (subCharCount <= 0) {
                        System.out.println("Warning: found unusual UCM format. Found subChar without the tag in the header");
                        subCharCount = bufferCount;
                        System.arraycopy(bytes, 0, subChars, 0, bufferCount);
                    }
                    continue; // throw away sub chars
                }
                count++;
                
                switch (type) {
                case 0: //CharMap.NORMAL
                    normalMapBuf.println("  <a u=\"" + cpStr
                        + "\" b=\"" + byteName(bytes, bufferCount)
                        + '"' + paramChar(cp)
                        + "/>");
                    break;
                case 1: //CharMap.FALLBACK_TO_BYTES
                    fallbackToBytesBuf.println("  <fub u=\"" + cpStr
                        + "\" b=\"" + byteName(bytes, bufferCount)
                        + '"' + paramChar(cp)
                        + "/>");
                    break;
                case 3: //CharMap.FALLBACK_TO_CODEPOINTS
                    fallbackToCPBuf.println("  <fbu u=\"" + cpStr
                        + "\" b=\"" + byteName(bytes, bufferCount)
                        + "\"/>");
                    break;
                default: 
                    throw new Exception("Problem in CharMapGenerator.java -- " + 
                        "variable 'type' is out of range (lines 106 - 112)");
                }
            } catch (Exception e) {
                normalMapBuf.flush();
                fallbackToBytesBuf.flush();
                fallbackToCPBuf.flush();
                if (errorCount++ < ERROR_LIMIT) {
                    System.out.println("Bad line: \"" + line + "\", " + e.getMessage());
                }
            }
        }
        
        normalMapBuf.flush();
        fallbackToBytesBuf.flush();
        fallbackToCPBuf.flush();

        if (subCharCount <= 0) {
            subChars[subCharCount++] = 0x1A;
            System.out.println("Warning: Using default substitute character: "
                               + Integer.toHexString(subChars[0]));
        }

        String validity;
        if (validityType == VALIDITY_CUSTOM) {
            customValidityBuf.flush();
            validity = UCMValidityParser.getXMLValidity(customValidity.toString());
        }
        else {
            if (uconvName == null) {
                System.out.println("Warning: Missing <uconv_class> tag");
            }
            else {
                if (uconvName.equals("\"SBCS\"")
                 && (validityType != VALIDITY_8BIT && validityType != VALIDITY_7BIT)) {
                    System.out.println("Warning: This UCM file does not seem to be SBCS");
                }
                else if (!uconvName.equals("\"SBCS\"")
                 && (validityType == VALIDITY_8BIT || validityType == VALIDITY_7BIT)) {
                    System.out.println("Warning: This UCM file seems to be SBCS, and it's labeled as "
                     + uconvName);
                }
            }
            if (validityType == VALIDITY_UNKNOWN || validityType == VALIDITY_EBCDIC_STATEFUL) {
                if (validityType != VALIDITY_EBCDIC_STATEFUL && UCMValidityParser.hasAlias(csName)) {
                    System.out.println("Warning: Guessing validity table for " + csName);
                    if (!UCMValidityParser.hasDirectAlias(csName)) {
                        System.out.println("Warning: Guessing that " + csName
                         + " is really " + UCMValidityParser.getAlias(csName));
                    }
                    String validityTableUCM = UCMValidityParser
                               .getCodepageValidity(UCMValidityParser.getAlias(csName));
                    if (validityTableUCM == null) {
                        System.out.println("Internal Error: missing validity table for " + csName);
                        System.exit(1);
                    }
                    validity = UCMValidityParser.getXMLValidity(validityTableUCM);
                }
                else {
                    System.out.println("Warning: Validity table is not known for " + csName);
                    customValidityBuf.println(" <!-- Unknown validity -->");
                    customValidityBuf.flush();
                    validity = customValidity.toString();
                }
            }
            else {
                validity = UCMValidityParser.getXMLValidity(getXMLValidity(validityType));
            }
        }
        writeXML(name,
                 validity,
                 normalMap.toString(),
                 fallbackToBytes.toString(),
                 fallbackToCP.toString(),
                 byteName(subChars, subCharCount));
    }

    static final void writeXML(String name,
                               String validity,
                               String normalMap,
                               String fallbackToBytes,
                               String fallbackToCP,
                               String subChars) throws IOException
    {
        PrintWriter out = new PrintWriter(
            new BufferedWriter(
            new OutputStreamWriter(
                new FileOutputStream(XML_DIR + name + ".xml"),
                "UTF8"),
            4*1024));
        
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
//        out.println("<!DOCTYPE characterMapping SYSTEM \"CharacterMapping.dtd\">");
        out.println("<!DOCTYPE characterMapping SYSTEM \"http://www.unicode.org/unicode/reports/tr22/CharacterMapping.dtd\">");
        Date now = new Date();
//        out.println("<!-- File generated on " + now + " -->");
//        out.println("<!-- Warning! Preliminary version: not all features implemented! -->");
        out.println("<characterMapping id=\"" + name + "\" version=\"1\">");
        out.println(" <history>");
        out.println("  <modified version=\"1\" date=\""
            + (new SimpleDateFormat("yyyy-MM-dd")).format(now)
//            + "2001-04-18"
            + "\">This file was generated with XMLSimpleGenerator.java.</modified>");
        out.println(" </history>");
        // first write validity check
//        out.println(" <sub illegal='" + illegal
//            + "' unmapped='" + unmapped
//            + "'/>");
            
        // print validity table
        
        out.print(validity);
        //ByteValidityChecker byteChecker = ;
//        writeXMLValidity(out, map.byteChecker);
        
        // print mapping table
        
        out.println(" <assignments sub=\"" + subChars+ "\">");
        out.println("  <!-- One to one mappings -->");
        out.print(normalMap);
        
        out.println("  <!-- Fallback mappings from Unicode to bytes -->");
        if (fallbackToBytes.equals(""))
            out.println("  <!-- NONE -->");
        else
            out.print(fallbackToBytes);
        
        out.println("  <!-- Fallback mappings from bytes to Unicode -->");
        if (fallbackToCP.equals(""))
            out.println("  <!-- NONE -->");
        else
            out.print(fallbackToCP);
        
        out.println(" </assignments>");
        out.println("</characterMapping>");
        out.close();
    }
    
    static String getXMLValidity(int validityType) {
        if (validityType == VALIDITY_7BIT) {
            return "00-7f";
        }
        else if (validityType == VALIDITY_8BIT) {
            return "00-ff";
        }
        else if (validityType == VALIDITY_DBCS) {
            StringWriter dbcsValidity = new StringWriter();
            PrintWriter dbcsValidityBuf = new PrintWriter(new BufferedWriter(dbcsValidity));

            // This is implied by DBCS
            dbcsValidityBuf.println("00-3f:3, 40:2, 41-fe:1, ff:3");
            dbcsValidityBuf.println("41-fe");
            dbcsValidityBuf.println("40");
            dbcsValidityBuf.print  ("00-ff.i");
            dbcsValidityBuf.flush();
            return dbcsValidity.toString();
        }
        else {
            System.err.println("Internal Error: Unusual validityType: " + validityType);
            System.exit(0);
        }
        return "";
    }

    static boolean isValidCharsetName(String s) {
        int srcNamePos = s.indexOf('-');
        int nextPos = -1;
        int verPos = s.lastIndexOf('-');

        if (srcNamePos >= 0) {
            nextPos = s.indexOf('-', srcNamePos + 1);
        }
        return srcNamePos >= 0 && verPos >= 0 && nextPos >= 0
         && srcNamePos < verPos && nextPos == verPos;
    }
    
    static String byteName(byte i) {
        String result = Integer.toHexString(i&0xFF).toUpperCase();
        if (result.length() < 2)
            result = '0' + result;
        return result;
    }

    static String byteName(byte[] source, int len) {
        if (source == null)
            return "ERROR";
        if (len < 1)
            return "";
        String result = byteName(source[0]);
        for (int i = 1; i < len; ++i) {
            result += " " + byteName(source[i]);
        }
        return result;
    }

    static String getBaseFilename(String s) {
        int dotPos = s.lastIndexOf('.');
        return s.substring(0,dotPos);
    }

    public static final String paramChar(int source) {
        if (verboseOutput && !Character.isISOControl((char)source) && source < '\uFFFE') {
            return " c=\"" + XMLChar(source) + '"';
        }
        return "";
    }

    public static final String XMLChar(int source) {
        if (source > 0xFFFF) {
            // Do surrogates
            return "&#x" + Integer.toHexString(source).toUpperCase() + ";";
        } else if (Character.isISOControl((char)source) || source >= '\uFFFE') {
            // Can't show it
            return "\uFFFD";
        } else if (source == '\'') {
            return "&apos;";
        } else if (source == '\"') {
            return "&quot;";
        } else if (source == '<') {
            return "&lt;";
        } else if (source == '&') {
            return "&amp;";
        } else if (source == '>') {
            return "&gt;";
        }
        return String.valueOf((char)source);
    }

}