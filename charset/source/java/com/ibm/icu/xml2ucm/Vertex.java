/*
 *******************************************************************************
 * Copyright (C) 2002-2004, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/
package com.ibm.icu.shanghai.xml2ucm;

import java.util.Iterator;
import java.util.TreeSet;

import org.w3c.dom.Element;


class Vertex {
        String vertexName;
        TreeSet neighbors;          // TreeSet<Neighbor>
        String ucmStateNumber;      // for ucm state

        Vertex(String name) {
            vertexName = name;
        }

        Vertex(Element e) {
            neighbors = new TreeSet();
            this.vertexName = e.getAttribute("type");
            addNeighbor(e);
        }

        void addNeighbor(Element e) {
            neighbors.add(new Neighbor(this, e));
        }
        
        String listNeighborsInUcmFormat(int initStateNum){
            // some vertexes does not have neighbors, for example, final states:
            // "VALID", "INVALID", "UNASSIGNED"
            if (neighbors == null) return "";
            
            String state = new String();
            for (Iterator e = neighbors.iterator(); e.hasNext();) {
                Neighbor n = (Neighbor) e.next();
                state += ", "  + n.listInUcmFormat(initStateNum);
            }
            // elimite leading ',' sign
            if (state.startsWith(",")) {
                state = state.substring(1, state.length());
            }            
            return state;
        }

} // class Vertex