/**
 * Copyright (c) 2008 IBM Corporation and others. All Rights Reserved.
 */
package com.ibm.icu.dev.meta;



public abstract class Nameable implements Comparable<Nameable> {
    public int compareTo(Nameable other) {
        return name().compareTo(other.name());
    }

    public abstract String name();

    /**
     *  Find something named 'like' in 'fromWhere
     * @param like the example item - has a certain name
     * @param fromWhere the source of the items
     * @return
     */
    @SuppressWarnings("unchecked")
    public static Nameable findLike(Nameable like, Iterable fromWhere) {
        for(Object o : fromWhere) {
            Nameable n = (Nameable)o;
            if(like.name().equals(n.name())) {
                return n;
            }
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    public static Nameable findLike(String name, Iterable fromWhere) {
        for(Object o : fromWhere) {
            Nameable n = (Nameable)o;
            if(name.equals(n.name())) {
                return n;
            }
        }
        return null;
    }
}