/**
 * Copyright (c) 2008 IBM and others, all rights reserved
 */
package com.ibm.icu.dev.meta;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;

import org.unicode.cldr.util.LDMLUtilities;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.ibm.icu.dev.meta.IcuInfo.Feature;
import com.ibm.icu.dev.meta.IcuInfo.IcuProduct;
import com.ibm.icu.dev.meta.IcuInfo.Release;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.VersionInfo;

//TODO: Not at all efficient or elegant. Needs rewrite

/**
 * Merge down Unicode support.
 * Depends on UAgeLang, and depends on a set of input files with the 'unicode' capability already set.
 * @author srl
 *
 */
public class PropagateCharacters {
    private static boolean DBG_INSANE=true;
    
    
    /**
     * @param args
     * @throws ParserConfigurationException 
     * @throws IOException 
     * @throws SAXException 
     */
    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
        if(args.length==0) {
            throw new InternalError("Usage: java PropagateFeatures someicu.xml ...");
        }
        Map<String,Map<String,String>> outdata = new TreeMap<String,Map<String,String>>();  // the whole thing.
        for(String arg : args) {
            process(outdata, arg);
        }
        print(System.out, createDocument(outdata));
    }
    

    static void process(Map<String,Map<String,String>> outdata, String arg) throws SAXException, IOException, ParserConfigurationException {
        File f = new File(arg);
        if(f.exists()) {
            if(DBG_INSANE) if(DBG_INSANE) System.err.println("# willread: " + f.getAbsolutePath());
        }
        IcuInfo someVersion = new IcuInfo(f);

        for(IcuProduct p:someVersion) {
            if(DBG_INSANE) System.err.println("## prod: " + p.name());
            Map<String,String> prod_map = outdata.get(p.name());
            if(prod_map == null) {
                prod_map = new TreeMap<String,String>();
                outdata.put(p.name(), prod_map);
            }
            for(Release r:p) {
                if(DBG_INSANE) System.err.println("### release: " + r.name());
                if(prod_map.containsKey(r.name())) {
                    System.err.println("Already have "+p.name()+":"+r.name()+" from " + f.getAbsolutePath());
                    continue;
                }

                Set<Feature> feats = r.featureList();
                if(feats == null) {
                    System.err.println("Cannot find any features in "+p.name()+":"+r.name()+" from " + f.getAbsolutePath());
                    continue;
                }

                Feature ufeature = (Feature) Nameable.findLike("unicode", feats);
                if(ufeature == null) {
                    System.err.println("Cannot find Unicode feature in "+p.name()+":"+r.name()+" from " + f.getAbsolutePath());
                    continue;
                }

                VersionInfo uver = ufeature.getVersion();
                if(uver == null) {
                    System.err.println("Cannot unicode version in feature in "+p.name()+":"+r.name()+" from " + f.getAbsolutePath());
                    continue;
                }

                if(DBG_INSANE) System.err.println("#### uver: " + uver.toString());
                Set<ULocale> localesSupported = UCharacterSupport.getInstance().localesSupportedBy(uver);
                if(DBG_INSANE) System.err.println("#### num locs: " + localesSupported.size());
                StringBuffer rv = new StringBuffer();
                for(ULocale l : localesSupported) {
                    if(rv.length()>0) {
                        rv.append(' ');
                    }
                    rv.append(l.getBaseName());
                }
                prod_map.put(r.name(), rv.toString());
            }
        }
    }
    
    static Document createDocument(Map<String, Map<String, String>> outdata) throws ParserConfigurationException {
        // create outgoing document
        Document out = XMLUtil.getBuilder().newDocument();

        Element base = out.createElement(IcuInfo.ICU_INFO);
        out.appendChild(base);

        Element products = out.createElement(IcuInfo.ICU_PRODUCTS);
        base.appendChild(products);
        
        for(Entry<String, Map<String, String>> prod : outdata.entrySet()) {
            if(prod.getValue().isEmpty()) {
                continue; // empty product
            }

            Element productNode = out.createElement(IcuInfo.ICU_PRODUCT);
            productNode.setAttribute(IcuInfo.TYPE, prod.getKey());
            products.appendChild(productNode);
            Element releasesNode = out.createElement(IcuInfo.RELEASES);
            productNode.appendChild(releasesNode);
            for(Entry<String, String> v : prod.getValue().entrySet()) { // ->  <ver> -> <loc loc loc ...>
                Element releaseNode = out.createElement(IcuInfo.RELEASE);
                releaseNode.setAttribute(IcuInfo.VERSION, v.getKey());
                Element capabilities = out.createElement(IcuInfo.CAPABILITIES);
                releaseNode.appendChild(capabilities);
                Feature feat = new Feature("characters", null);
                feat.setContents(v.getValue());
                feat.appendTo(out, capabilities);
                releasesNode.appendChild(releaseNode);
            }
        }
        return out;
    }

    static void print(OutputStream outstr, Document out) throws IOException {
         // write
         {
//             java.io.FileOutputStream fos = null;
//             if(outfile!=null) {
//                 fos = new FileOutputStream(outfile);
//                 out = fos;
//                 if(verbose) System.err.println("# Write <"+outfile+">");
//             } else {
//                 out = System.out;
//                 if(verbose) System.err.println("# Write <stdout>");
//             }
//             try {
                  java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                          outstr);
                  String copy = "";
                  if(true) copy = ("<!-- Copyright (c) "+Calendar.getInstance().get(Calendar.YEAR)+" IBM Corporation and Others, All Rights Reserved. -->\n");
                  LDMLUtilities.printDOMTree(out, new PrintWriter(writer), copy+"\n<!-- This file was generated from:  somewhere -->\n<!DOCTYPE icuInfo SYSTEM \"http://icu-project.org/dtd/icumeta.dtd\">\n",null); //
                  writer.flush();
//             } catch (IOException e) {
//                 Syste
//             }
//               if(fos!=null) fos.close();
         }
    
    }

}
