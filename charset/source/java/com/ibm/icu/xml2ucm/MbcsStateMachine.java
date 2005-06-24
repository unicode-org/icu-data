/*
 *******************************************************************************
 * Copyright (C) 2002-2005, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
*/

package com.ibm.icu.shanghai.xml2ucm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @author YANG RongWei
 *
 * This class is a vertex-based, linked list implementation, of 'State Machine'.
 * 
 * The validity of byte sequences in MBCS is described by 'State Machine'.
 * ('State Machine' is a type of data stucture 'Graph').
 * 
 * In .XML file, the description is edge-based.
 *      <validity>  is a list of edges
 *      <state>     is the represtation of edge 
 * In .UCM file, the description is vertex-based.
 *      all <icu:state>             is a list of vertexes
 *      each <icu:state> line       is the represtation of vertex
 *      each entry of <icu:state>   is the represtation of neighbor
 *       
 */
public class MbcsStateMachine {
    public class StateMachineHasLoop extends Exception{}
    private static final String eol = System.getProperty("line.separator", "\r\n");
    private Vertexes vertexList; // linked list of vertexes (or Nodes)
    private int minSteps;
    private int maxSteps;
    private HashSet steps;

    public MbcsStateMachine(Element validity) throws StateMachineHasLoop {
        vertexList     = new Vertexes();
        NodeList edges = validity.getElementsByTagName("state");
        for (int i = 0; i < edges.getLength(); i++) {
            Element e       = (Element) edges.item(i);
            String sv_name  = e.getAttribute("type"); // source Vertex name
            Vertex  v;
            if (!vertexList.containsKey(sv_name)) {
                v = new Vertex(e);  // new and add neighbor
                vertexList.put(sv_name, v);
            } else {
                v = (Vertex) vertexList.get(sv_name);
                v.addNeighbor(e);
            } // if ()
        } // for ()
        
        vertexList.vertex_GenName_Xml2Ucm(0);
        vertexList.vertex_Neighbors_GenFormat_xml2all();
        
        steps = new HashSet();
        findMaxSteps();
        steps.clear();
        findMinSteps();
    } // MbcsStateMachine(NodeList edges)
    
    public String listAs_simple_SateMachine(){
        String[] ucmStateList;
        ucmStateList = listStateMachine_ucm(0);
        return list2String(ucmStateList);
    }

    public String listAs_siso_1_byte(){
        String[] ucmStateList;
        ucmStateList = listStateMachine_ucm(0);
        ucmStateList[0] = ucmStateList[0] + ", e:1.s, f:0.s";
        return list2String(ucmStateList);
   }
    
    public String listAs_siso_2_byte(){
        String[] ucmStateList;
        ucmStateList = listStateMachine_ucm(1);
        ucmStateList[0] = " initial," + ucmStateList[0] + ", e:1.s, f:0.s";
        return list2String(ucmStateList);
    }
    
    private String[] listStateMachine_ucm(int initStateNum ){
        Set xmlKeys = vertexList.getNonFinalVertexes();
        int stateCount = xmlKeys.size();
        String[] ucmStateList = new String[stateCount];
        for (Iterator iter = xmlKeys.iterator(); iter.hasNext();) {
            String key = (String) iter.next();
            Vertex v = (Vertex) vertexList.get(key);
            int i = Integer.parseInt(v.ucmStateNumber, 16);
            ucmStateList[i] = v.listNeighborsInUcmFormat(initStateNum);
        }
        
        return ucmStateList; 
        
    }
    
    private String list2String(String[] ucmStateList) {
        String result = "";        
        for (int i = 0; i < ucmStateList.length; i++) {
            String state = ucmStateList[i];
            result += "<icu:state>\t" + state + eol;
        }
        return result;
    }
    
    public int getMinBytes(){
        return minSteps;
    }
    public int getMaxBytes(){
        return maxSteps;
    }
    
    
    private int findMinSteps() throws StateMachineHasLoop{
        minSteps = -1;
        Vertex v = (Vertex) vertexList.get("FIRST");
        findMinSteps(v,0);
        return minSteps;
    }
    
    private void findMinSteps(Vertex v, int parentSteps) throws StateMachineHasLoop {
        if (v.neighbors == null) {
            if (parentSteps < minSteps || minSteps == -1) {
                minSteps = parentSteps;
            }
            return;
        } else {
            if(steps.contains(v)){
                throw new StateMachineHasLoop();
            } else {
                steps.add(v); //record current step
            }

            int currentSteps = parentSteps + 1;
            for (Iterator iter = v.neighbors.iterator(); iter.hasNext();) {
                Neighbor n = (Neighbor) iter.next();
                Vertex nv = (Vertex) vertexList.get(n.neighborXmlName);
                findMinSteps(nv, currentSteps);
            }

            steps.remove(v); // erase current step 
        } // if else
    } // findMinSteps(Vertex v, int parentSteps)

    
    private int findMaxSteps() throws StateMachineHasLoop{
        maxSteps = 0;
        Vertex v = (Vertex) vertexList.get("FIRST");
        findMaxSteps(v, 0);
        return maxSteps;
    }
    
    
    private void findMaxSteps(Vertex v, int parentSteps) throws StateMachineHasLoop {
        if (v.neighbors == null) {
            return;
        } else {
            if(steps.contains(v)){
                    System.err.println(v.vertexName);
                    System.err.println("-----------");
                for (Iterator iter = steps.iterator(); iter.hasNext();) {
					Vertex vv = (Vertex) iter.next();
					System.err.println(vv.vertexName);
				}
                throw new StateMachineHasLoop();
            } else {
                steps.add(v); //record current step
            }

            int currentSteps = parentSteps + 1;
            if (currentSteps > maxSteps) {
                maxSteps = currentSteps;
            }
            for (Iterator iter = v.neighbors.iterator(); iter.hasNext();) {
                Neighbor n = (Neighbor) iter.next();
                Vertex nv = (Vertex) vertexList.get(n.neighborXmlName);
                findMaxSteps(nv, currentSteps);
            }

            steps.remove(v); // erase current step 
        } // if else
    } // findMaxSteps(Vertex v, int parentSteps)
    
    private class Vertexes extends HashMap {
        Vertexes(){
            // "VALID", "INVALID", "UNASSIGNED"
            // final states are other state's nighbor,
            // although they do not have neighbors,
            // so these should be exist. 
            // (Why ? Can we assume?)
            // These vetexes are to help code like vertex_Neighbors_GenFormat_xml2all()
            this.put("VALID", new Vertex("VALID"));
            this.put("INVALID", new Vertex("INVALID"));
            this.put("UNASSIGNED", new Vertex("UNASSIGNED"));
        }

        Set getNonFinalVertexes(){
            Set keys = new HashSet(keySet());
            keys.remove("VALID");
            keys.remove("INVALID");
            keys.remove("UNASSIGNED");
            return keys;
        }
        
        /**
		 * Generate state's UCM implicit line number
		 */
		void vertex_GenName_Xml2Ucm(int start){
		    // 'final state' is a concept in .UCM file.
		    // It is not listed by a line in .UCM file.
		    // It is represented as an 'action' follow an entry.
		    // It SHOULD be skipped, or we cannot continual line numbers for other states.
		    Set keys = getNonFinalVertexes();

		    Vertex  v;
		    if (keys.contains("FIRST")){
		        keys.remove("FIRST");
		        v = (Vertex)get("FIRST");
		        v.ucmStateNumber = Integer.toString(start);
		    }
		    
            // order the keys by our knowledge
            List known_keys = new ArrayList();
            for (Iterator iter = STATE_NAMES.iterator(); iter.hasNext();) {
				String name = (String) iter.next();
				if (keys.contains(name)){
                    known_keys.add(name);
                    keys.remove(name);
				}
			}
            
            // generate known keys' number
            int i;
            for (i = 0; i < known_keys.size(); i++) {
				String name = (String) known_keys.get(i);
                v = (Vertex) this.get(name);
                int j = i + start + 1;  // shift +1 position after 'start'
				v.ucmStateNumber = Integer.toHexString(j);
			}            
            
            // generate other keys' number            
		    for (Iterator e = keys.iterator(); e.hasNext();) {
		        String name = (String) e.next();
		        v = (Vertex) this.get(name);
		        v.ucmStateNumber = Integer.toHexString(i);
		        i++;
		    }
		} // vertex_GenName_Xml2Ucm()
        
        /**
         * Copy neighbor's information to various format
         */
        void vertex_Neighbors_GenFormat_xml2all(){
            // Traverse all vertexes
            for (Iterator e1 = getNonFinalVertexes().iterator(); e1.hasNext();) {
                String name = (String) e1.next();
                Vertex v = (Vertex) this.get(name);

                // Traverse all neighbors
                for (Iterator e2 = v.neighbors.iterator(); e2.hasNext();) {
                    Neighbor n = (Neighbor) e2.next();
                    // fullfill neighbor information in other format
                    // (Why not failed?)
                    // Because we can guarantee that if we have a vertex name,
                    // we really have a vertex in vertex list.
                    // This is guaranteed by Vertexes()
                    Vertex  x = (Vertex)this.get(n.neighborXmlName);
                    n.neighbor = x;
                    n.neighborUcmNumber = x.ucmStateNumber;
                } // Traverse all neighbors

            } // Traverse all vertexes
        } // VertexNeighborsInfo_xml2all()
    } // class Vertices
    

private static final List STATE_NAMES = new ArrayList(
Arrays.asList( 
    new String [] {
        "FIRST",
        "SECOND",
        "THIRD",
        "FOURTH",
        "FIFTH",
        "SIXTH",
        "SEVENTH",
        "EIGHTH",
        "NINTH",
        "TENTH",
        "ELEVENTH",
        "TWELFTH",
        "THIRTEENTH",
        "FOURTEENTH",
        "FIFTEENTH",
        "SIXTEENTH",
        "SEVENTEENTH",
        "EIGHTTEENTH",
        "NINETEENTH",
        "TWENTIETH" 
        }
    )
);

} // class MbcsStateMachine
