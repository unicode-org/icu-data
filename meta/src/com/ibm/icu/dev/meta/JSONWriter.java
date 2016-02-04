// Copyright (C) 2016, International Business Machines Corporation and others.  All Rights Reserved.

package com.ibm.icu.dev.meta;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JSONWriter {
	/**
	 * 
	 * @param full - full contents
	 * @param outjson - out filename
	 * @param copyin - (c) or null
	 * @param sources - "sources" string
	 */
	public static void write(Document full, String outjson, String copyin, String sources) {
		Gson g = new GsonBuilder().setPrettyPrinting().create();
		
		try(final FileOutputStream fos = new FileOutputStream(outjson)) {
			try(final OutputStreamWriter osw = new OutputStreamWriter(fos, Charset.forName("UTF-8"))) {
				JsonObject j = new JsonObject();
				j.addProperty("//", copyin);
				j.addProperty("docs", Merger.DOCS_URL);
				j.addProperty("metaversion", Merger.METAVERSION);
				
				j.add("projects", getProjects(full));
				
				osw.write(g.toJson(j));
				osw.write('\n');
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static JsonObject getProjects(Document full) {
		JsonObject p = new JsonObject();
		final NodeList elementsByTagNameNS = full.getElementsByTagNameNS("*", "icuProduct");
		for(int i=0;i< elementsByTagNameNS.getLength();i++) {
			Node item = elementsByTagNameNS.item(i);
			final String type = item.getAttributes().getNamedItem("type").getTextContent();
			p.add(type, getProject(item));
		}
		return p;
	}

	private static JsonElement getProject(Node item) {
		final JsonObject p = new JsonObject();
		
		final NodeList s = item.getChildNodes();
		for(int i=0;i< s.getLength();i++) {
			final Node sitem = s.item(i);
			if(sitem.getNodeName().equals("releases")) {
				extractReleases(p, sitem);
			}
		}
		return p;
	}

	private static void extractReleases(final JsonObject p, final Node sitem) {
        final JsonObject finalReleases = new JsonObject();
        final JsonObject draftReleases = new JsonObject();
        
		final NodeList releases = sitem.getChildNodes();
		for(int si=0;si< releases.getLength();si++) {
			final Node release = releases.item(si);
			
			if(release.getNodeName().equals("release")) {
                JsonObject addTo = finalReleases;
                if(release.getAttributes().getNamedItem("draft") != null) {
                    addTo = draftReleases;
                }
				extractRelease(addTo, sitem, releases, release);
			}
			
		}
        if(!finalReleases.entrySet().isEmpty()) {
            p.add("releases", finalReleases);
        }
        if(!draftReleases.entrySet().isEmpty()) {
            p.add("proposedReleases", draftReleases);
        }
	}

	private static void extractRelease(final JsonObject p, final Node sitem, final NodeList releases,
			final Node release) {
		JsonObject r = extractReleaseStuff(release);
		final String v = release.getAttributes().getNamedItem("version").getNodeValue();
		p.add(v, r);
	}

	private static JsonObject extractReleaseStuff(final Node release) {
		final JsonObject r = new JsonObject();
		final NodeList stuff = release.getChildNodes();
		for(int ssi=0;ssi< stuff.getLength();ssi++) {
			final Node stuff2 = stuff.item(ssi);
			if(stuff2.getNodeName().equals("dates")) {
                JsonObject d = new JsonObject();
				extractDates(stuff2, d);
                r.add("dates", d);
			} else {
//				System.err.println(stuff2.getNodeName());
			}
		}
		return r;
	}

	private static void extractDates(final Node sitem, JsonObject r) {
		final NodeList dates = sitem.getChildNodes();
		for(int sssi=0;sssi< dates.getLength();sssi++) {
			final Node date = dates.item(sssi);
			if(!(date.getNodeType() == Node.ELEMENT_NODE)) continue;
			r.addProperty(date.getAttributes().getNamedItem("type").getNodeValue(), 
						  date.getAttributes().getNamedItem("date").getNodeValue());
		}
	}

}
