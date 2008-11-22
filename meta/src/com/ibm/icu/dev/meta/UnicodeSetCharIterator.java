/**
 * Copyright (c) 2008 IBM and others, all rights reserved
 */
package com.ibm.icu.dev.meta;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import com.ibm.icu.text.UCharacterIterator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.util.ULocale;

/**
 * Iterate over a UnicodeSet providing the Iterator and Iterable interface, one codepoint at a time.
 * @author srl
 *
 */
public class UnicodeSetCharIterator implements Iterator<Integer>, Iterable<Integer> {
    private UnicodeSet set;
    private int r = 0;
    private int len = 0;
    private int rstart = 0;
    private int rend = 0;
    private int ch = 0;

    final void dump(String where) {
        if(false) System.err.println(where+": r="+r+", len="+len+", ["+Integer.toHexString(rstart)+"-"+Integer.toHexString(rend)+"], ch="+Integer.toHexString(ch));
    }
    
    UnicodeSetCharIterator(UnicodeSet set) {
        this.set = set;
        len = set.getRangeCount();
        r = 0;
        load();
        dump("init");
        ch--;
    }
    public boolean hasNext() {
        dump("hasNext?");
        return (ch < rend) || (ch==rend && r<(len-1));
    }
    
    /* Load the current range */
    private void load() {
        dump("preload");
        if(r>=len) {
            ch=0;
            rend=0;
            dump("load->END");
            return; // end
        } else {
            rstart = set.getRangeStart(r);
            rend = set.getRangeEnd(r);
            ch = rstart;
            dump("load()");
        }
    }

    public Integer next() {
        dump("prenext");
        if(ch < rend) {
            ch++;
            dump("next");

            return ch;
        } else if(ch == rend) {
            r++;
            if(r<len) {
                load();
                dump("next->load");
                return ch;
            }
        }
        dump("next->END");
        return 0xFFFF;
    }

    public void remove() {
        throw new UnsupportedOperationException("read only set");
    }
    public Iterator<Integer> iterator() {
        return this;
    }
    
    /**
     * Test case
     * @param args
     */
    public static void main(String args[]) {
        System.out.println("Test case for UnicodeSetCharIterator.");
        int errs = 0;
        errs += testAll();
       // errs += test(new ULocale("mt"));
        if(errs>0) {
            System.err.println("ERR: had " + errs+ " errors.");
        } else {
            System.out.println(" -->> All OK. ");
        }
    }
    
    public static int testAll() {
        int errs = 0;
        for(ULocale loc : ULocale.getAvailableLocales()) {
            errs += test(loc);
        }
        return errs;
    }
    
    private static int test(ULocale loc) {
        return test(loc. toString(), UCharacterSupport.setForLocale(loc));
    }
    
    private static int test(String name, UnicodeSet set) {
        int errs = 0;
        UnicodeSetCharIterator usci = new UnicodeSetCharIterator(set);

        System.out.println("# " + name);
        /*
        for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.next();) {
            processString(it.getString());
        }
        */

        Set<Integer> s_usi = new TreeSet<Integer>();
        Set<Integer> s_usci = new TreeSet<Integer>();
               
        for(int u : usci) {
            s_usci.add(u); // EASY
            if(!set.contains(u)) { 
                System.out.println("UH: set doesn't contain codepoint " + Integer.toHexString(u));
                errs++;
            }
        }
        
        
        for (UnicodeSetIterator it = new UnicodeSetIterator(set); it.nextRange();) {
            // HARD
            if (it.codepoint != UnicodeSetIterator.IS_STRING) {
                for(int u=it.codepoint;u<=it.codepointEnd;u++) {
                    s_usi.add(u);
                }
            } else {
                UCharacterIterator chariter = UCharacterIterator.getInstance(it.getString());
                int i=-1;
                for(;i!=UCharacterIterator.DONE;) {
                    i = chariter.next();
                    s_usi.add(i);
                }
            }
        }
        
        System.out.println("usCi: " + s_usci.size() + " , usi: " + s_usi.size());
        if(s_usci.equals(s_usi)) {
            System.out.println("-> OK");
        } else {
            System.out.println(" NOT OK: different.");
            errs++;
        }
        return errs;
    }
}