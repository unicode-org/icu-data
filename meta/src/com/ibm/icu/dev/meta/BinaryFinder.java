// Copyright (c) 2008 IBM Corporation and others. All Rights Reserved.

package com.ibm.icu.dev.demo.icu4jweb;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.icu.dev.demo.icu4jweb.IcuInfo.Builder;
import com.ibm.icu.dev.demo.icu4jweb.IcuInfo.IcuProduct;
import com.ibm.icu.dev.demo.icu4jweb.IcuInfo.Platform;
import com.ibm.icu.dev.demo.icu4jweb.IcuInfo.Release;

/**
 * Locate binaries, give URL
 * @author srl
 *
 */
public class BinaryFinder {
   static String icu_download_root = System.getProperty("ICU_DOWNLOAD_ROOT");
   static String icu_download_http= System.getProperty("ICU_DOWNLOAD_HTTP");
   static String icu_download_https= System.getProperty("ICU_DOWNLOAD_HTTPS");
   static File rootDir = icu_download_root!=null?new File(icu_download_root):null;
   
   private IcuInfo info;
   private IcuProduct product;
   private Release release;

   
   String prodStub;
   String releaseStub;
   File prodDir;
   File releaseDir;
   String releasePath;
   
   private static String EXTENSIONS[] = { ".tgz", ".zip", ".tar.bz2", ".jar", "tar.gz" };
   
   public BinaryFinder(IcuInfo info,IcuProduct product,Release release) {
       this.info = info;
       this.product = product;
       this.release = release;

       prodStub = product.name();
       releaseStub = release.name();
       
       if(rootDir != null && rootDir.exists()) {
           prodDir = new File(rootDir, prodStub);
           releaseDir = new File(prodDir, releaseStub);
       }
       releasePath = prodStub+"/"+releaseStub;
   }
   
   public Set<Binary> binariesForPlatform(Platform platform) {

       String releaseFileName = release.name().replace('.', '_');

       if(releaseDir == null) { 
           return null;
       }

       TreeSet<Binary> bins = new TreeSet<Binary>();
       Set<String> binStubs = new HashSet<String>();
       // collect all stub names
       for(Builder b : platform.builders()) {
           if(b.binary != null) {
               binStubs.add(b.binary);
           }
       }
       
       for(String stub : binStubs) {
          for(String ext : EXTENSIONS) {
              String candidate = product.name()+"-"+releaseFileName+"-"+stub+ext;
              File f = new File(releaseDir,candidate);
              if(f.exists()) {
                  bins.add(new Binary(f));
//              } else {
//                  System.err.println(candidate);
              }
          }
       }
       
       return bins;
   }
   
   public class Binary extends Nameable {
       String name;
       File file;
       protected Binary(File f) {
           file = f;
           this.name = file.getName();
       }

        @Override
        public String name() {
            // TODO Auto-generated method stub
            return name;
        }
        public File file() {
            return file;
        }
        public String size() {
            // TODO: replace with unit formatter http://unicode.org/cldr/bugs/locale-bugs?findid=1896
            return getSize(file);
        }
        String getSize(File f) {
            double size = f.length();
            double kb = size / 1024.0;
            double mb = size / 1048576; // 1000000 ; // 1048576 // kb / 1000.0;
            double mb1 = ((int)java.lang.Math.round(mb*10.0))/10.0;
            double kb1 = ((int)java.lang.Math.round(kb*10.0))/10.0;
            double b1 = ((int)java.lang.Math.round(size*10.0))/10.0;
            if(kb1  < 1.0) {
                return ""+(int)java.lang.Math.round(b1) + "\u00A0B";
            } else if(mb1 < 1.0) {
                return ""+(int)java.lang.Math.round(kb) + "\u00A0KB";
            } else {
                return ""+mb1 + "\u00A0MB";
            }
       }
       public String url() {
           return icu_download_http + "/" + releasePath + "/" + name;
       }
   }
}
