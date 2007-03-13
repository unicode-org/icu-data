/*
 *******************************************************************************
 * Copyright 2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/

package com.ibm.icu.jdk;

import java.io.*;
import sun.io.*;
import java.util.Hashtable;
/**
 * A tool to querry the JDK sun.io.converter.* and generate a ICU UCM tables 
 * This tool can be modified to generate UCM files from java.nio.
 */
public class JDKConverter2UCM{
    static boolean isFallbackMapping = false; 
    public static void main(String args[]){   
        if (args.length < 1) {
            System.out.println("syntax: java JDKConverter2UCM encoding/all");
            return;
        }
        if(args[0].equals("all")){
            getAllMappings();
        }else{
            getMappings(args[0]);
        }                 
    }
    public static void getMappings(String enc){
        try{
            CharToByteConverter cbConv = CharToByteConverter.getConverter(enc);
            ByteToCharConverter bcConv = ByteToCharConverter.getConverter(enc);
            final FileOutputStream fromUniOutFile = new FileOutputStream("Java_"+cbConv.getCharacterEncoding()+"_fromUni.ucm");
            final PrintWriter pWriter = new PrintWriter(fromUniOutFile);
            writeHeader(pWriter,cbConv.getMaxBytesPerChar(),1,"Java_"+cbConv.getCharacterEncoding(),isEBCDIC (enc));
            printStateTable(cbConv,pWriter);
            byte[] bArr=new byte[20];
            for(int i=0; i<=0xffff; i++){
                /* Do not map surrogate area */
                if (i>=0xd800 && i<=0xdfff)
                    continue;
               
                int num=getFromUMapping((char)i,cbConv,bcConv,bArr,enc);
                
                if(num!=0 && bArr[0]!= 0x3f){
                    pWriter.print("<U"+toHexString(i,16,4)+">     ");
                    int j=0;
                    while(j<num){
                        int tK=((bArr[j]>>4&0x0f)*16);
                        tK+=bArr[j]&0x0f;
                        hexByteToDecimal(bArr[j]);
                        String tS =("\\x"+toHexString((int)(tK),16,2));
                        pWriter.print(tS);
                        j++;
                    }
                    if(isFallbackMapping==true){
                        pWriter.print("   |1");
                       
                    }
                    else{
                        pWriter.print("   |0");
                    }
                    pWriter.println("");
                }
                isFallbackMapping = false;
            }
           pWriter.close();
            
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void getAllMappings(){
        String[] encNames = {
            "ASCII",
            "ISO8859_1",
            "ISO8859_2",
            "ISO8859_3",
            "ISO8859_4",
            "ISO8859_5",
            "ISO8859_6",
            "ISO8859_7",
            "ISO8859_8",
            "ISO8859_9",
            "ISO8859_13",
            "ISO8859_15",
            "UTF8",
            "UnicodeBigUnmarked", 
            "UnicodeLittleUnmarked",
            "UTF16",
            "Unicode",
            "Cp037",
            "Cp273",
            "Cp277",
            "Cp278",
            "Cp280",
            "Cp284",
            "Cp285",
            "Cp297",
            "Cp420",
            "Cp424",
            "Cp437",
            "Cp500",
            "Cp737",
            "Cp775",
            "Cp838",
            "Cp850",
            "Cp852",
            "Cp855",
            "Cp856",
            "Cp857",
            "Cp860",
            "Cp861",
            "Cp862",
            "Cp863",
            "Cp864",
            "Cp865",
            "Cp866",
            "Cp868",
            "Cp869",
            "Cp870",
            "Cp871",
            "Cp874",
            "Cp875",
            "Cp918",
            "Cp921",
            "Cp922",
            "Cp930",
            "Cp933",
            "Cp935",
            "Cp937",
            "Cp939",
            "Cp942",
            "Cp943",
            "Cp948",
            "Cp949",
            "Cp950",
            "Cp964",
            "Cp970",
            "Cp1006",
            "Cp1025",
            "Cp1026",
            "Cp1097",
            "Cp1098",
            "Cp1112",
            "Cp1122",
            "Cp1123",
            "Cp1124",
            "Cp1381",
            "Cp1383",
            "ISO2022JP",
            "MS932",
            "SJIS",
            "EUC_JP",
            "MS874",
            "Cp1250",
            "Cp1251",
            "Cp1252",
            "Cp1253",
            "Cp1254",
            "Cp1255",
            "Cp1256",
            "Cp1257",
            "Cp1258",
            "Cp33722",
            "KOI8_R",
            "EUC_CN",
            "Big5",
            "EUC_TW",
            "EUC_KR",
            "Johab", 
            "MS949",
            "ISO2022KR",
            "TIS620",
            "Cp942C",
            "Cp943C",
            "Cp949C",
            "ISCII91"
            };
            for(int i=0; i<encNames.length;i++){
                getMappings(encNames[i]);
            }

    }
    public static int hexByteToDecimal(byte i){
        /*get the value in hex */
        int j= (i>>4&0x0f)*10*16+i&0x0f;
        
        return j;
    }
    
    public static String toHexString(int ch, int radix, int pad){
        final int MAX_DIGITS = 10;
        int length = 0;
        char buffer[] = new char[10];
        int num = 0;
        int digit;
        int j;
        char temp;
        long i =(long)ch;
      
        do{
            digit = (int)(i % radix);
            buffer[length++]=(char)(digit<=9?(0x0030+digit):(0x0030+digit+7));
            i=(i/radix);
        }while(i>0);

        while (length < pad){   
            buffer[length++] =  0x0030;/*zero padding */
        }
        /* null terminate the buffer */
        if(length<MAX_DIGITS){
            buffer[length] =  0x0000;
        }
        num= (pad>=length) ? pad :length;
              
        /* Reverses the string */
        for (j = 0; j < (num / 2); j++){
            temp = buffer[(length-1) - j];
            buffer[(length-1) - j] = buffer[j];
            buffer[j] = temp;
            }
        String tS =new String(buffer,0,num);
        return tS;
    }
    
    public static boolean isEBCDIC(String enc){
        boolean isEbcdicDbcs=false;
        byte[] bsource = new byte[2];
        byte   dbcslead[] = new byte[256];
        int touniCount=0;
        int leadCount =0;
        String source;
        for (int i=0; i<256; i++) {
            dbcslead[i] = 0;
            bsource[0] = (byte)i;
            try {
                source = new String(bsource,enc);
                if (source.charAt(0) != 0xfffd) {
                    touniCount++;
                }
            } catch (Exception e) {
                if (leadCount>0 || i<0x20 || (i>=0x80 && i<0x90)) {
                   // System.out.println("DBCS Lead Byte " + Hex2(i));
                    if (i==0x0e && leadCount==0)
                        isEbcdicDbcs = true;
                    leadCount++;
                 }
            }
        }
        return isEbcdicDbcs;
    }
    public static void writeHeader(PrintWriter out,int maxCharLength, int minCharLength,String ucmname,boolean isEbcdicDBCS){
        try{
            /* write the header */
            out.println(
                ("# *******************************************************************************\n"+
                "# *\n"+
                "# *   Copyright (C) 1995-2001, International Business Machines\n"+
                "# *   Corporation and others.  All Rights Reserved.\n"+
                "# *\n"+
                "# *******************************************************************************\n"+
                "#\n"+
                "# File created by JDKConverter2UCM.java\n"+
                "#"));
            /* ucmname does not have a path or .ucm */
            out.println(("<code_set_name>               " + ucmname));
            out.println(("<char_name_mask>              \"AXXXX\""));
            out.println(("<mb_cur_max>                  " + maxCharLength));
            out.println(("<mb_cur_min>                  " + minCharLength ));
            out.println(("<subchar>                     " + "\\x3f"));
            if(minCharLength==maxCharLength && minCharLength==1){
                out.println("<uconv_class>              "+"\"SBCS\"");        
            }
            else if(minCharLength==maxCharLength && minCharLength==2){
                //if(!isEbcdicDBCS){
                    out.println("<uconv_class>              "+"\"DBCS\"");
                //}
            }
            else if(minCharLength!=maxCharLength){
                if(isEbcdicDBCS){
                    out.println("<uconv_class>                 "+"\"EBCDIC_STATEFUL\"");
                }else{
                    out.println("<uconv_class>                 "+"\"MBCS\"");
                }
            } 
            out.println("");
                
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static void printStateTable(CharToByteConverter cbConv, PrintWriter out){
        Hashtable myHash = new Hashtable();      
        myHash.put("UTF8", "ALGORITHMIC");
        myHash.put("UnicodeBigUnmarked", "ALGORITHMIC");
        myHash.put("UnicodeLittleUnmarked", "ALGORITHMIC");
        myHash.put("UTF16", "ALGORITHMIC");
        myHash.put("Unicode", "ALGORITHMIC");
        myHash.put("Cp942", "<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"+
                            "<icu:state>                   40-7e, 80-fc");
        myHash.put("Cp943", "<icu:state>                   0-7f, 81-9f:1, a0-df, e0-fc:1\n"+
                            "<icu:state>                   40-7e, 80-fc");
        myHash.put("Cp949", "<icu:state>                   0-84, 8f-fe:1\n"+
                            "<icu:state>                   40-7e, 80-fe");
        myHash.put("Cp950", "<icu:state>                   0-7f, 81-fe:1\n"+
                            "<icu:state>                   40-7e, 81-fe");
        myHash.put("Cp964", "<icu:state>                   0-8d, 8e:2, 90-9f, a1-fe:1, aa-c1:5, c3:5, fe:5\n"+
                            "<icu:state>                   a1-fe\n"+
                            "<icu:state>                   a1-b0:3, a1:4, a2:8, a3-ab:4, ac:7, ad:6, ae-b0:4\n"+
                            "<icu:state>                   a1-fe:1\n"+
                            "<icu:state>                   a1-fe:5\n"+
                            "<icu:state>                   a1-fe.u\n"+
                            "<icu:state>                   a1-a4:1, a5-fe:5\n"+
                            "<icu:state>                   a1-e2:1, e3-fe:5\n"+
                            "<icu:state>                   a1-f2:1, f3-fe:5");
        myHash.put("Cp970", "<icu:state>                   0-9f, a1-fe:1\n"+
                            "<icu:state>                   a1-fe");
        myHash.put("Cp1381", "<icu:state>                   0-84, 8c-fe:1\n"+
                             "<icu:state>                   a1-fe");
        myHash.put("Cp1383", "<icu:state>                   0-9f, a1-fe:1\n"+
                             "<icu:state>                   a1-fe");
        myHash.put("ISO2022JP", "ALGORITHMIC");
        myHash.put("MS932", "<icu:state>                   0-7f, 81-9f:1, a0-df, e0-fc:1\n"+
                            "<icu:state>                   40-7e, 80-fc");
        myHash.put("SJIS", "<icu:state>                   0-7f, 81-9f:1, a0-df, e0-fc:1\n"+
                           "<icu:state>                   40-7e, 80-fc");
        myHash.put("EUC_JP", "<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"+
                             "<icu:state>                   a1-fe\n"+
                             "<icu:state>                   a1-e4\n"+
                             "<icu:state>                   a1-fe:1, a1:4, a3-af:4, b6:4, d6:4, da-db:4, ed-f2:4\n"+
                             "<icu:state>                   a1-fe.u");
        myHash.put("Cp33722","<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"+
                             "<icu:state>                   a1-fe\n"+
                             "<icu:state>                   a1-e4\n"+
                             "<icu:state>                   a1-fe:1, a1:4, a3-af:4, b6:4, d6:4, da-db:4, ed-f2:4\n"+
                             "<icu:state>                   a1-fe.u");
        myHash.put("EUC_CN", "<icu:state>                   0-9f, a1-fe:1\n"+
                             "<icu:state>                   a1-fe");
        myHash.put("Big5", "<icu:state>                   0-80, 81-fe:1\n"+
                           "<icu:state>                   40-7e, 81-fe");
        myHash.put("EUC_TW","<icu:state>                   0-8d, 8e:2, 90-9f, a1-fe:1, aa-c1:5, c3:5, fe:5\n"+
                            "<icu:state>                   a1-fe\n"+
                            "<icu:state>                   a1-b0:3, a1:4, a2:8, a3-ab:4, ac:7, ad:6, ae-b0:4\n"+
                            "<icu:state>                   a1-fe:1\n"+
                            "<icu:state>                   a1-fe:5\n"+
                            "<icu:state>                   a1-fe.u\n"+
                            "<icu:state>                   a1-a4:1, a5-fe:5\n"+
                            "<icu:state>                   a1-e2:1, e3-fe:5\n"+
                            "<icu:state>                   a1-f2:1, f3-fe:5");
        myHash.put("EUC_KR","<icu:state>                   0-9f, a1-fe:1\n"+
                            "<icu:state>                   a1-fe");
        myHash.put("Johab", "<icu:state>                   0-84, 8f-fe:1\n"+
                            "<icu:state>                   40-7e, 80-fe");
        myHash.put("MS949", "<icu:state>                   0-84, 8f-fe:1\n"+
                            "<icu:state>                   40-7e, 80-fe");
        myHash.put("ISO2022KR", "<icu:state>                   0-7f, e:1.s, f:0.s\n"+
                                "<icu:state>                   initial, 0-20:3, e:1.s, f:0.s, 21-7e:2, 7f-ff:3\n"+
                                "<icu:state>                   0-20:1.i, 21-7e:1., 7f-ff:1.i\n"+
                                "<icu:state>                   0-ff:1.i");
        myHash.put("TIS620", "UNSUPPORTED");
        myHash.put("Cp942C", "UNSUPPORTED");
        myHash.put("Cp943C","<icu:state>                   0-84, 8f-fe:1\n"+
                            "<icu:state>                   40-7e, 80-fe");
        myHash.put("Cp949C", "<icu:state>                   0-84, 8f-fe:1\n"+
                            "<icu:state>                   40-7e, 80-fe");
        myHash.put("ISCII91", "UNSUPPORTED");
        String enc = cbConv.getCharacterEncoding();
        String stateTable = (String)myHash.get(enc);
        if(stateTable==null ||  (stateTable.equals("UNSUPPORTED")||
                        stateTable.equals("EBCDIC_STATEFUL")||
                        stateTable.equals("ALGORITHMIC"))){
            System.out.println("Warning: Couldnot generate state table for enc; " + enc);
        }
        else{
            out.println(stateTable);
        }
        
    }
    public static int getFromUMapping(char fromU, CharToByteConverter cbConv,ByteToCharConverter bcConv, byte[] byteArr,String enc){
        char [] charArr = {fromU,fromU,fromU,fromU};
        int num=0;
        char[] tCharArr = new char[20];
        //byte [] byteArr = new byte[20];
        for(int i=0;i<byteArr.length;i++){
            byteArr[i]=0;
        }
        try{
            /* convert it once to get capture any state changes and discard */
            num=cbConv.convert(charArr,0,1,byteArr,0,20);
            bcConv.convert(byteArr,0,num,tCharArr,0,20);
            if(tCharArr[0]!=fromU && fromU!=0x0e && fromU!=0x0f && fromU!=0x1b){
                isFallbackMapping= true;
            }
            byteArr[0]=0;
            byteArr[1]=0;
            num=0;
            /* convert again to get a clean mapping */
            num=cbConv.convert(charArr,1,2,byteArr,0,20);
            bcConv.reset();
            cbConv.reset();
            return num;
        }
        catch(Exception e){
            System.out.println(e);
            e.printStackTrace();
        }
        return num;
    }
}
    
