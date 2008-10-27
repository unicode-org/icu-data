/**
 * Copyright (c) 2008 IBM Corporation and others. All Rights Reserved.
 */
package com.ibm.icu.dev.demo.icu4jweb;


public abstract class Nameable implements Comparable<Nameable> {
    public int compareTo(Nameable other) {
        return name().compareTo(other.name());
    }

    public abstract String name();
}