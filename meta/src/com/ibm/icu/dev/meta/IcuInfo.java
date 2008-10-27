/**
 * Copyright (c) 2008 IBM and others, all rights reserved
 */
package com.ibm.icu.dev.demo.icu4jweb;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ibm.icu.util.VersionInfo;

/**
 * @author srl
 *
 */
public class IcuInfo implements Iterable<IcuInfo.IcuProduct> {
    
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
        public Set<String> platformList() {
            return sortedKeys(platforms);
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
        protected Release(Document fromDoc, Node n) {
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            versionString= XMLUtil.getAttributeValue(n, VERSION);
            draft = XMLUtil.getAttributeValue(n, DRAFT);
            versionInfo = VersionInfo.getInstance(versionString);
            NodeList datesList = XMLUtil.findChild(n, DATES).getChildNodes();
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
        }
        public Iterator<Platform> iterator() {
            return platforms.values().iterator();
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

    private Hashtable<String,IcuProduct> products = new Hashtable<String,IcuProduct>();

    public static final String ICU_DOWNLOAD_ROOT = "icu_download_root";
    public static final String ICU_DOWNLOAD_HTTP = "icu_download_http";
    public static final String ICU_DOWNLOAD_HTTPS = "icu_download_https";
    
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
}
