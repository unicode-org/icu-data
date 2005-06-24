/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.ucm2xml;

public class UnknownRow extends Exception {
    UnknownRow(){
    }
    UnknownRow(String msg, Throwable cause){
        super(msg, cause);
    };
}