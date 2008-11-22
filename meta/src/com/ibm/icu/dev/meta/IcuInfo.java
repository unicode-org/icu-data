/**
 * Copyright (c) 2008 IBM and others, all rights reserved
 */
package com.ibm.icu.dev.meta;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ibm.icu.util.VersionInfo;

/**
 * @author srl
 *
 */
public class IcuInfo implements Iterable<IcuInfo.IcuProduct> {


    public static String FILE_RELATIVE_PATH = "xml/icuinfo.xml";
    public static String ICU_INFO_URL = "http://icu-project.org/"+FILE_RELATIVE_PATH;

    
    
    public class IcuProduct extends Nameable implements Iterable<Release> {
        private String type;
        private Hashtable<String,String> names = new Hashtable<String,String>();
        private Map<String,Release> releases = new TreeMap<String,Release>(sortStringsBackwards());
        protected IcuProduct(Document fromDoc, Node fromNode) {
            type = XMLUtil.getAttributeValue(fromNode, TYPE);

            Node namesNode = XMLUtil.findChild(fromNode, NAMES);
            if(namesNode != null)  {
                NodeList namesList = namesNode.getChildNodes();
                for(int k=0;k<namesList.getLength();k++) {
                    Node n = namesList.item(k);
                    if(n.getNodeType()!=Node.ELEMENT_NODE) continue ;
                    names.put(XMLUtil.getAttributeValue(n, "type"), XMLUtil.getNodeValue(n));
                }
            }
            Node relsNode = XMLUtil.findChild(fromNode, RELEASES);
            if(relsNode != null) {
                NodeList rels = relsNode.getChildNodes();
                for(int k=0;k<rels.getLength();k++) {
                    Node n = rels.item(k);
                    if(n.getNodeType()!=Node.ELEMENT_NODE) continue ;
                    Release r = new Release(fromDoc, n);
                    releases.put(r.name(), r);
                }
            }
        }
        @Override
        public String name() {
            return type;
        }
        public String name(String nameType) {
            String s = names.get(nameType);
            if(s == null) {
                return name();
            } else {
                return s;
            }
        }
        public String fullName() {
            return name(FULL);
        }
        public String shortName() {
            return name(SHORT);
        }
        public Set<String> releaseList() {
            return sortedKeysReverse(releases);
        }
        public Release release(String ver) {
            return releases.get(ver);
        }
        public Iterator<Release> iterator() {
            return releases.values().iterator();
        }
    }
    
    public class Release extends Nameable implements Iterable<Platform> {
        /**
         * == version
         */
        public String name() {
            return versionString; //TODO: investigate 'shortened' version?
        }
        public String draft() {
            return draft;
        }
        public String getDate() {
            return getDate(GA);
        }
        public String getDate(String kind) {
            return dates.get(kind);
        }
        public VersionInfo getVersionInfo() {
            return versionInfo;
        }
        public Set<String> platformList() {
            return sortedKeys(platforms);
        }
        public Set<Feature> featureList() {
            return features;
        }
        public Platform platform(String kind) {
            return platforms.get(kind);
        }
        public Set<Platform> platformsBySupport() {
            Set<Platform> newSet = new TreeSet<Platform>(sortPlatformsBySupport());
            newSet.addAll(platforms.values());
            return newSet;
        }
        public Set<Platform> supportsPlatform(String attrib, String value, Status minTesting) {
            Set<Platform> newSet = new TreeSet<Platform>(sortPlatformsBySupport());
            for(Platform p : this) {
                if(p.matches(attrib, value) && p.status().isAtLeast(minTesting)) {
                    newSet.add(p);
                }
            }
            return newSet;
        }
        Set<Platform> supportsPlatform(String attrib, String value) {
            return supportsPlatform(attrib, value, Status.WORKS);
        }
        private String draft;
        private String versionString;
        private VersionInfo versionInfo;
        private Hashtable <String,String> dates = new Hashtable<String,String>();
        private Map <String,Platform> platforms= new Hashtable<String,Platform>();
        private Set<Feature> features;
        protected Release(Document fromDoc, Node n) {
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            versionString= XMLUtil.getAttributeValue(n, VERSION);
            draft = XMLUtil.getAttributeValue(n, DRAFT);
            versionInfo = VersionInfo.getInstance(versionString);
            Node dn = XMLUtil.findChild(n, DATES);
            if(dn != null) {
                NodeList datesList = dn.getChildNodes();
                for(int q=0;q<datesList.getLength();q++) {
                    Node nn = datesList.item(q);
                    if(nn.getNodeType()!=Node.ELEMENT_NODE) continue ;
                    String dtype = XMLUtil.getAttributeValue(nn, TYPE);
                    String ddate = XMLUtil.getAttributeValue(nn, DATE);
                    dates.put(dtype, ddate);
    //                try {
    //                    dates.put(dtype, sdf.parse(ddate));
    //                } catch (ParseException e) {
    //                    // TODO Auto-generated catch block
    //                    e.printStackTrace();
    //                    dates.put(dtype, null);
    //                }
                    // TODO: time zone issue?
    //                if(!"ga".equals(dtype)) {
    //                    out.println("unknown type " + dtype+" on " + nn.getNodeName()+ "<br/>");
    //                    continue;
    //                }
    //                // calculate 6 year out
    //                c.clear();
    //                ga = sdf.parse(ddate);
    //                c.setTime(ga);
    //                c.add(Calendar.YEAR, 6);
    //                eos = c.getTime();
                }
            }
            
//            if(ga!=null && eos!=null) {
//                String pastdue =  "";
            NodeList possplats = n.getChildNodes();
            for(int q=0;q<possplats.getLength();q++) {
                Node nn = possplats.item(q);
                if(nn.getNodeType()!=Node.ELEMENT_NODE) continue ;
                if(!PLATFORMS.equals(nn.getNodeName())) continue;
                String owner = getAndRecordAttributeValue(nn, OWNER);
                String date = XMLUtil.getAttributeValue(nn, DATE);
                NodeList pp = nn.getChildNodes();
                for(int j=0;j<pp.getLength();j++) {
                    Node jj = pp.item(j);
                    if(jj.getNodeType()!=Node.ELEMENT_NODE) continue ;
                    if(!PLATFORM.equals(jj.getNodeName())) continue;
                    Platform plat = new Platform(owner, date, fromDoc,jj);
                    platforms.put(plat.name(), plat);
                }
            }
            
            Node cn = XMLUtil.findChild(n, CAPABILITIES);
            if(cn != null) {
                Set<Feature> feats = new TreeSet<Feature>();
                NodeList featList = cn.getChildNodes();
                for(int j=0;j<featList.getLength();j++) {
                    Node jj = featList.item(j);
                    if(jj.getNodeType()!=Node.ELEMENT_NODE) continue;
                    Feature feat = new Feature(fromDoc, jj);
                    feats.add(feat);
                }
                features = feats;
            }
        }
        public Iterator<Platform> iterator() {
            return platforms.values().iterator();
        }
    }

    public static class Feature extends Nameable {
        String type;
        String version;
        int count = 0;
        String contents;
        String comment = null;
        public Feature(Document fromDoc, Node n) {
            version = XMLUtil.getAttributeValue(n, "version");
            type = XMLUtil.getAttributeValue(n, "type");
            //count
            contents = XMLUtil.getNodeValue(n);
            if(contents!=null) {
                contents=contents.trim();
            }
        }
        public Feature(Feature other) {
            type = other.type;
            contents = other.contents;
            version = other.version;
            comment = other.comment;
        }
        public Feature(String type, String version) {
            this.type = type;
            this.version = version;
        }
        
        public void setContents(String contents) {
            this.contents = contents;
        }
        
        public void setComment(String c) {
            comment = c;
        }

        @Override
        public String name() {
            // TODO Auto-generated method stub
            return type;
        }
        public Feature clone() {
            return new Feature(this);
        }
        
        public void addContentsFrom(Feature other) {
            this.contents = merge(this.contents, other.contents);
            if(other.version!=null) {
                this.version = other.version;
            }
        }
        
        public String toString() {
            return "{Feature type="+type+", version="+version+", contents=<"+contents+">}";
        }
        public void appendTo(Document doc, Element capabilities) {
            Element me = doc.createElement(FEATURE);
            if(type!=null) {
                me.setAttribute(TYPE, type);
            }
            if(version!=null) {
                me.setAttribute(VERSION,version);
            }
            if(contents!=null) {
                Node n = doc.createTextNode(contents);
                me.appendChild(n);
            }
            //System.err.println(this.toString());
            if(comment!=null) {
                Node cmt = doc.createComment(comment);
                capabilities.appendChild(cmt);
            }
            capabilities.appendChild(me);
        }
        /**
         * Get the version as a UVersionInfo, or null if not present.
         * @return
         */
        public VersionInfo getVersion() {
           if(version == null) {
               return null;
           } else {
               return VersionInfo.getInstance(version);
           }
        }
    }

    public  Map<String,Set<String>> allAttributes() {
        return allAttributes;
    }
    private Map<String,Set<String>> allAttributes = new TreeMap<String,Set<String>>();
    
    private void recordAttributeValue(String attribute, String s) {
        if(s != null) {
            Set<String> attribSet = allAttributes.get(attribute);
            if(attribSet == null) {
                attribSet = new TreeSet<String>();
                allAttributes.put(attribute,attribSet);
            }
            attribSet.add(s);
        }
    }

    private String getAndRecordAttributeValue(Node n, String attribute) {
        String s = XMLUtil.getAttributeValue(n, attribute);
        recordAttributeValue(attribute, s);
        return s;
    }

    public class Platform extends Nameable {
        public Map<String,String> attribIndex = new TreeMap<String,String>();
        boolean matches(String attribute, String value) {
            return value.equals(attribIndex.get(attribute));
        }
        private Status status = Status.UNKNOWN;
        public Status status() {
            return status;
        }
        public boolean binary = false;
        public String arch = null;
        public int bits = 0;
        public String os = null;
        public String os_version = null;
        public String tool = null;
        public String tool_version = null;
        
        public String name() {
            return os + " " + os_version + ", " + arch + " " + bits + "-bit: " + tool + " " + tool_version;
        }
        Set<Builder> builders = new HashSet<Builder>();
        public Set<Builder> builders() { return builders; } // TODO: sanity check.
        private String getAndIndexAttributeValue(Node n, String attribute) {
            return indexAttributeValue(attribute, getAndRecordAttributeValue(n, attribute));
        }
        
        private String indexAttributeValue(String attribute, String value) {
            attribIndex.put(attribute, value);
            recordAttributeValue(attribute, value);
            return value;
        }
        
        protected Platform(String owner, String date, Document fromDoc, Node n) {
            arch = getAndIndexAttributeValue(n, ARCH);
            bits = Integer.parseInt(getAndIndexAttributeValue(n, BITS));
            os  = getAndIndexAttributeValue(n, OS);
            os_version  = getAndIndexAttributeValue(n, OS_VERSION);
            tool  = getAndIndexAttributeValue(n, TOOL);
            tool_version  = getAndIndexAttributeValue(n, TOOL_VERSION);
            
            // add some combinations
            indexAttributeValue("os+version",os+" "+os_version);
            indexAttributeValue("tool+version",tool+" "+tool_version);
            NodeList pp = n.getChildNodes();
            for(int j=0;j<pp.getLength();j++) {
                Node jj = pp.item(j);
                if(jj.getNodeType()!=Node.ELEMENT_NODE) continue ;
                if(!BUILDER.equals(jj.getNodeName())) continue;
                Builder t = new Builder(owner, date, fromDoc, jj);
                if(t.status().ordinal()>status.ordinal()) {
                    status = t.status();
                }
                if(t.binary!=null) {
                    binary=true;
                }
                builders.add(t);
            }
        }
        
    }

    private static Comparator<Platform> sortPlatformsBySupport() {
        return new Comparator<Platform>() {
            public int compare(Platform o1, Platform o2) {
                int r = o2.status().compareTo(o1.status()); // descending.
                if(r==0) {
                    r = new Boolean(o2.binary).compareTo(o1.binary); // descending.
                }
                if(r==0) {
                    r = o1.name().compareTo(o2.name());
                }
                return r;
            }
        };
    }
    private static Comparator<String> sortStringsBackwards() {
        return new Comparator<String>() {
            public int compare(String o1, String o2) {
                int r = o2.compareTo(o1); // descending.
                return r;
            }
        };
    }

    /**
     * Merge the contents of feature description
     * @param s1 'existing' text
     * @param s2 'new' text, may start with '+' for addition, or none for replacement
     * @return new text
     */
    public static String merge(String s1, String s2) {
        String out;
        if(s2!=null) {
            if(s2.startsWith("+")) {
                s2 = s2.substring(1).trim(); // remove plus
                if(s1==null) {
                    out = s2;
                } else {
                    out = s1+" "+s2;
                }
            } else {
                out = s2;
            }
        } else {
            if(s1!=null && s1.startsWith("+")) {
                s1 = s1.substring(1).trim();
            }
            out = s1;
        }
        return out;
    }

    private Hashtable<String,IcuProduct> products = new Hashtable<String,IcuProduct>();

    public static final String ICU_DOWNLOAD_ROOT = "icu_download_root";
    public static final String ICU_DOWNLOAD_HTTP = "icu_download_http";
    public static final String ICU_DOWNLOAD_HTTPS = "icu_download_https";
    
    public static final String ICU_INFO = "icuInfo";
    public static final String ICU_PRODUCT = "icuProduct";
    public static final String TYPE = "type";
    public static final String BINARY = "binary";
    public static final String RELEASES = "releases";
    public static final String NAMES = "names";
    public static final String NAME = "name";
    public static final String GA = "ga";
    public static final String FULL = "full";
    public static final String SHORT = "short";
    public static final String VERSION = "version";
    public static final String DATE = "date";
    public static final String DATES = "dates";
    public static final String DRAFT = "draft";
    public static final String ARCH = "arch";
    public static final String BITS = "bits";
    public static final String OS = "os";
    public static final String OS_VERSION = "os-version";
    public static final String TOOL = "tool";
    public static final String TOOL_VERSION = "tool-version";
    public static final String PLATFORM = "platform";
    public static final String PLATFORMS= "platforms";
    public static final String TESTER= "tester";
    public static final String OWNER= "owner";
    public static final String FREQUENCY= "frequency";
    public static final String STATUS= "status";
    public static final String BUILDER= "builder";
    public static final String CAPABILITIES = "capabilities";
    public static final String FEATURE = "feature";
    public static final String ICU_PRODUCTS = "icuProducts";
    public static final String RELEASE = "release";
    
//  public enum Frequency {
//  NEVER,
//  RARELY,
//  OFTEN,
//  REGULARLY;
//  
//  public String toString() {
//      return name().toLowerCase();
//  }
//  public boolean isAtLeast(Frequency minTesting) {
//      return this.ordinal()>=minTesting.ordinal();
//  }
//  public static Frequency fromValue(String str) {
//      return valueOf(str.toUpperCase());
//  }
//}
  public enum Status {
      UNKNOWN,
      BROKEN,
      WORKS;
      
      public String toString() {
          return name().toLowerCase();
      }
      public boolean isAtLeast(Status min) {
          return this.ordinal()>=min.ordinal();
      }
      public static Status fromValue(String str) {
          return valueOf(str.toUpperCase());
      }
    }
    public class Builder extends Nameable {
//        private Frequency frequency = Frequency.NEVER;
//        public Frequency frequency() { 
//            return frequency;
//        }
        private Status status = Status.UNKNOWN;
        public Status status () { 
            return status;
          }
        public String name = null;
        public String date = null;
        public String owner = null;
        public String binary = null;
        
        public String name() {
            return owner+":"+name + " ("+date+")"+"-"+status.toString();
        }
        
        protected Builder(String owner, String date, Document fromDoc, Node n) {
            this.owner = owner;
            this.date = date;
            name = getAndRecordAttributeValue(n, NAME);
            String newDate = XMLUtil.getAttributeValue(n, DATE);
            if(newDate != null) {
                this.date= newDate;
            }
//            String freq = getAndRecordAttributeValue(n,FREQUENCY);
//            frequency = Frequency.fromValue(freq);
            String freq = getAndRecordAttributeValue(n,STATUS);
            binary = XMLUtil.getAttributeValue(n,BINARY); // no reason to save this one.
            status = Status.fromValue(freq);
        }
    }

    private void parseFrom(Document fromDocument) {
        //clear();
        
        Element el = fromDocument.getDocumentElement();
        
        parseFrom(fromDocument, el);

    }

    public void parseFrom(Document fromDoc, Node fromNode) {
        NodeList prods = fromDoc.getElementsByTagName(ICU_PRODUCT);
        for(int i=0;i<prods.getLength();i++) {
            Node prod = prods.item(i);
            IcuProduct aprod = new IcuProduct(fromDoc, prod);
            products.put(aprod.name(),aprod);
        }
    }
    
    // public interface
    /**
     * Construct an IcuInfo
     * @param fromDocument the document to parse
     */
    public IcuInfo(Document fromDocument) {
        parseFrom(fromDocument);
    }
    
    public IcuInfo(File fromFile) throws SAXException, IOException, ParserConfigurationException {
        parseFrom(XMLUtil.getBuilder().parse(fromFile));
    }
    
    private static IcuInfo info=null;
    /**
     * Get an IcuInfo
     * @return the icuinfo
     * @throws IOException 
     * @throws SAXException 
     * @throws ParserConfigurationException 
     * @throws ParserConfigurationException
     */
    public static IcuInfo getInstance() throws ParserConfigurationException, SAXException, IOException {
        if(info == null) {
            info = createInstance();
        }
        return info;
    }
    
    public static IcuInfo createInstance() throws ParserConfigurationException, SAXException, IOException {
        return new IcuInfo(getDocument());
    }
    private static Document aDoc = null;
    /**
     * Set the default document
     * @param doc
     */
    public static void setDocument(Document doc) {
        aDoc = doc;
    }
    /**
     * Set the default document
     * @param fromUrl
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public static void setDocument(String fromUrl) throws SAXException, IOException, ParserConfigurationException {
        aDoc = XMLUtil.getBuilder().parse(fromUrl);
    }
    /**
     * Set the default document
     * @param fromFile
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public static void setDocument(File fromFile) throws SAXException, IOException, ParserConfigurationException {
        aDoc = XMLUtil.getBuilder().parse(fromFile);
    }
    /**
     * Get the default document
     * @return
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public static Document getDocument() throws SAXException, IOException, ParserConfigurationException {
        if(aDoc == null) {
            aDoc = createDocument();
        }
        return aDoc;
    }
    /**
     * Get the default document (default to load from URL)
     * @return
     * @throws SAXException
     * @throws IOException
     * @throws ParserConfigurationException
     */
    public static Document createDocument() throws SAXException, IOException, ParserConfigurationException {
        setDocument(ICU_INFO_URL);
        return aDoc;
    }
    
    
    public Set<String> productList() {
        return sortedKeys(products);
    }
    public IcuProduct product(String name) {
        return products.get(name);
    }
    
    public static Set<String> sortedKeys(Hashtable<String, ?> t) {
        return sort(t.keySet());
    }
    public static Set<String> sortedKeys(Map<String, ?> t) {
        return sort(t.keySet());
    }
    public static Set<String> sortedKeysReverse(Hashtable<String, ?> t) {
        return sortReverse(t.keySet());
    }
    public static Set<String> sortedKeysReverse(Map<String, ?> t) {
        return sortReverse(t.keySet());
    }
    public static Set<String> sort(Set<String> s) {
        Set<String> ret = new TreeSet<String>();
        ret.addAll(s);
        return ret;
    }
    public static Set<String> sortReverse(Set<String> s) {
        Set<String> ret = new TreeSet<String>(sortStringsBackwards());
        ret.addAll(s);
        return ret;
    }

    public Iterator<IcuProduct> iterator() {
        return products.values().iterator();
    }
    
    /**
     * Covert a VersionInfo to a String. Omit trailing 0's in the milli and micro place.
     * @param info
     * @return
     */
    public static String versionInfoToShortString(VersionInfo info) {
        StringBuffer buf = new StringBuffer();
        buf.append(info.getMajor());
        buf.append(".");
        buf.append(info.getMinor());
        if(info.getMicro()>0 || info.getMilli()>0) {
            buf.append(".");
            buf.append(info.getMilli());
            if(info.getMicro()>0) {
                buf.append(".");
                buf.append(info.getMicro());
            }
        }
        return buf.toString();
    }
}
