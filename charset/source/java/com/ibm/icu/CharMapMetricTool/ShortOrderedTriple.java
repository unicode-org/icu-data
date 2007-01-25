/*
 *****************************************************************************
 * Copyright (C) 2000-2007, International Business Machines Corporation and  *
 * others. All Rights Reserved.                                              *
 *****************************************************************************
*/
import java.io.Serializable;

/* This contains the percent difference */
public class ShortOrderedTriple implements Serializable {
    public short rt;
    public short fb;
    public short rfb;
    
    /** constructor */
    public ShortOrderedTriple() {
        rt = fb  = rfb = 0;
    }
    
    /** constructor */
    public ShortOrderedTriple(short roundTrip,
                             short fallBack,
                             short reverseFBack) 
    {
        this.rt = roundTrip;
        this.fb = fallBack;
        this.rfb = reverseFBack;
    }
    
    public static double asPercent(int metric,
                                   double desiredPrecision) {
        return round(((double)metric * 100.0) / (double)Short.MAX_VALUE, desiredPrecision);  
    }
    
    // converts a short metric value to a representative percentage rounded to the nearest 
    // hundredth then returns this value as a 6 character string left padded with spaces and
    // right padded with 0's as necessary.  If printed in a column the decimal points of all
    // Strings returned by this method will line up.
    public static String asStringPercent2(int metric) {  
        double d = asPercent(metric, 0.01);
        if (d == 100.0) {           // 3.1 => 3.2
            return "100.00";
        }
        if (d == 0.0) {
            return "0";
        }

        String s = Double.toString(d);
        int len = s.length();
        if (d < 10.0) {              
            if (len < 4) {          // 1.1 => 1.2
                s = s + "0";
            }
        } else if (len < 5) {
            s = s + "0";            // 2.1 => 2.2
        }
        s = s.substring(0, s.indexOf('.') + 3);      // 2.n -> 2.2
        return s;
    }
    
    public static double round(double number, 
                               double placeValue ) {
/*	    double recipPlaceValue = 1.0/placeValue;
        return ( (int)(number*recipPlaceValue+0.5) / recipPlaceValue );*/
        return ( (double)((int)((number/placeValue)+0.5)) * placeValue );
    }
    
/*    public String toString() {
        String rTrips = "RT: " + asStringPercent2(rt) + "%   ";
        String fBacks = "FB: " + asStringPercent2(fb) + "%   ";
        String rfBacks = "RFB: " + asStringPercent2(rfb) + "%   ";
        return rTrips + fBacks + rfBacks;
    }*/
    
    // test showing we seem to have about two decimal places of accuracy upon converting
    // our short metric value back to a decimal percentage.
/*    public static void main(String[] args) {
        ShortOrderedTriple sot = new ShortOrderedTriple(
            (int)Math.round(Integer.MAX_VALUE),
            (int)Math.round(3.0/5.0*Integer.MAX_VALUE), 
            (int)Math.round(1.0/12.0*Integer.MAX_VALUE)
        );
        System.out.println("Just printing a selected value (rfb) to varying degrees of accuracy");    
        System.out.println(sot.asPercent(sot.rfb, 1.0));
        System.out.println(sot.asPercent(sot.rfb, 0.1));
        System.out.println(sot.asPercent(sot.rfb, 0.01));
        System.out.println(sot.asPercent(sot.rfb, 0.001));
        System.out.println(sot.asPercent(sot.rfb, 0.0001));
        
        System.out.println("\n\nHere's the whole object:");
        System.out.println(sot);
    }*/
}