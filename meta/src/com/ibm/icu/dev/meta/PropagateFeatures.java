/**
 * Copyright (c) 2008 IBM and others, all rights reserved
 */
package com.ibm.icu.dev.meta;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;

import org.unicode.cldr.util.LDMLUtilities;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.ibm.icu.dev.meta.IcuInfo.Feature;
import com.ibm.icu.dev.meta.IcuInfo.IcuProduct;
import com.ibm.icu.dev.meta.IcuInfo.Release;

//TODO: Not at all efficient or elegant. Needs rewrite

/**
 * Merge down features.  If it was introduced in version X, then a feature continues until appended to or replaced.
 * @author srl
 *
 */
public class PropagateFeatures {
    private static boolean DBG_INSANE=false;
    
    
    /**
     * @param args
     * @throws ParserConfigurationException 
     * @throws IOException 
     * @throws SAXException 
     */
    public static void main(String[] args) throws ParserConfigurationException, SAXException, IOException {
        if(args.length!=2) {
            throw new InternalError("Usage: java PropagateFeatures icumeta.xml features.xml [ -o:out.xml ]");
        }
        File f = new File(args[1]);
        if(f.exists()) {
            if(DBG_INSANE) if(DBG_INSANE) System.err.println("# willread: " + f.getAbsolutePath());
        } else {
            throw new InternalError("Doesn't exist: " + f.getAbsolutePath());
        }
        IcuInfo.setDocument(new File(args[0]));
         IcuInfo allVersions = IcuInfo.createInstance();
         //Document verDoc = IcuInfo.getDocument();
         Document inDoc = XMLUtil.getBuilder().parse(f);
         IcuInfo theFeatures = new IcuInfo(inDoc);
         
         // create outgoing document
         Document out = XMLUtil.getBuilder().newDocument();
         
         Element base = out.createElement(IcuInfo.ICU_INFO);
         out.appendChild(base);
         
         Element products = out.createElement(IcuInfo.ICU_PRODUCTS);
         base.appendChild(products);
         
         for(IcuProduct p:theFeatures) {
             IcuProduct all_p = (IcuProduct)Nameable.findLike(p, allVersions);
             if(all_p == null) {
                 throw new InternalError("Couldn't find product like " + p.name());
             }
             Map<String,Set<Feature>> allfeatures  = new TreeMap<String,Set<Feature>>();
             
             // list of all versions for this product
             String versions[] = IcuInfo.sort(all_p.releaseList()).toArray(new String[0]);
             
             for(String aRelease : IcuInfo.sort(p.releaseList())) {
                 Release r = p.release(aRelease); 
                 if(DBG_INSANE) if(DBG_INSANE) System.err.println(">> " + p.name() + " - " + r.name());
                 for(Feature feat:r.featureList()) {
                     
                     
                     if(DBG_INSANE) System.err.println(" .. " + feat.name());
                     int i;
                     if(DBG_INSANE) System.err.println("Vlen " + versions.length);
                     for(i=0;i<versions.length&&!versions[i].equals(aRelease);i++) {
                     }
                     if(i==versions.length) {
                         System.err.println("Warning: Couldn't find version " + aRelease);
                         if(aRelease.compareTo(versions[0])>0) {
                             System.err.println("Warning: assuming " + aRelease + " is before all other releases.");
                             i = -1; // propagate from all
                         }
                     }
                     if(DBG_INSANE) System.err.println("I @ " + versions[i]+ ": " + i + " / " + versions.length + " - " + feat);
                     // i = index to current feature
                     // now, propagate.
                     
                     Set<Feature> thisVer = allfeatures.get(aRelease);
                     if(thisVer==null) {
                         thisVer = new TreeSet<Feature>();
                         allfeatures.put(aRelease,thisVer);
                     }
                     Feature sf = (Feature)Nameable.findLike(feat,thisVer);
                     if(sf == null) {
                         sf = feat.clone();
                         thisVer.add(sf);
                     } else {
                         sf.addContentsFrom(feat); // perform '+' operation
                     }
                     // now, propagate s down until we have another copy of feature
                     for(int j=i+1;j<versions.length;j++) {
                         if(DBG_INSANE) System.err.println("J @ " + versions[j]+ ": " + j + " / " + versions.length + " - " + sf.toString());
                         Set<Feature> thisVer2 = allfeatures.get(versions[j]);
                         if(thisVer2==null) {
                             thisVer2 = new TreeSet<Feature>();
                             allfeatures.put(versions[j], thisVer2);
                         }
                         if(thisVer2.contains(feat)) {
                             throw new InternalError("duplicate feature " + feat.name() + " in propagate to " + p.name() + versions[j]);
                         }
                         Feature newFeature = sf.clone();
                         newFeature.setComment("(from "+aRelease+")");
                         thisVer2.add(newFeature); // each one gets a clone of the feature

                         // Is this feature defined in the version?
                         Release subr = p.release(versions[j]);
                         if(DBG_INSANE) System.err.println("V " + versions[j] + " , feat " + feat.name());
                         if(subr!=null) {
                             if(Nameable.findLike(feat, subr.featureList())!=null) {
                                 if(DBG_INSANE) System.err.println("STOP: J @ " + versions[j]+ ": " + j + " / " + versions.length + " - " + sf.toString());
                                 newFeature.setComment("("+aRelease+"+"+versions[j]+")");
                                 break;   
                             }
                         }
                     }
                 }
             }
             
             // OK. All features of all releases are in hash. 
             if(!allfeatures.isEmpty()) {
                 Element productNode = out.createElement(IcuInfo.ICU_PRODUCT);
                 productNode.setAttribute(IcuInfo.TYPE, p.name());
                 products.appendChild(productNode);
                 Element releasesNode = out.createElement(IcuInfo.RELEASES);
                 productNode.appendChild(releasesNode);
                 for(String v : IcuInfo.sort(allfeatures.keySet())) {
                     Element releaseNode = out.createElement(IcuInfo.RELEASE);
                     releaseNode.setAttribute(IcuInfo.VERSION, v);
                     Element capabilities = out.createElement(IcuInfo.CAPABILITIES);
                     releaseNode.appendChild(capabilities);
                     for(Feature feat : allfeatures.get(v)) {
                         feat.appendTo(out, capabilities);
                     }
                     releasesNode.appendChild(releaseNode);
                 }
             }
         }

         // write
         {
             OutputStream outstr = null;
             outstr = System.out;
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
                  LDMLUtilities.printDOMTree(out, new PrintWriter(writer),copy+"\n<!-- This file was generated from: "+args[0]+" -->\n<!DOCTYPE icuInfo SYSTEM \"http://icu-project.org/dtd/icumeta.dtd\">\n",null); //
                  writer.flush();
//             } catch (IOException e) {
//                 Syste
//             }
//               if(fos!=null) fos.close();
         }
    
    }

}
