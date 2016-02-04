ICU Release Metadata
====================

![ICU logo](http://icu-project.org/repos/icu/icuhtml/trunk/design/iculogo/iculogo_64.png)

*Copyright (C) 2016, International Business Machines Corporation and others.  All Rights Reserved.*

Status
------

This is an experimental file, no formal stability guarantees are in place.
Practically, it is the intent to keep the contents and URLs stable
as described herein, and they have remained stable.

Introduction
------------

ICU provides machine-readable metadata about ICU releases.
This document serves as the documentation for such metadata.

http://icu-project.org/xml/README.md is the canonical URL for this document.
See http://icu-project.org for general ICU information.

Note that the URLs given redirect to specific locations, be sure to
follow redirects (`curl -L` for example) to be able to fetch the document.

Scope
-----

The primary scope of the metadata is to answer the following questions:

* What versions of ICU are available? (56.1, 55.1, …)
* When was a particular version released? (ICU4C 56.1 was released 2015-10-07)

There may be additional information available in the metadata files, as these
are stabilized this document may be updated.

XML version
-----------

The XML version of ICU metadata is located at the canonical URL:

* http://icu-project.org/xml/icumeta.xml

Here is an simplified example snippet of the file:

	<icuInfo>
	    <icuProducts>
	        <icuProduct type="icu4c">
	            <releases>
	              <release version="57.1" draft="proposed">
	                  <dates>
	                      <date date="2016-03-31" type="ga"/>
	                  </dates>
	              </release>
	              <release version="56.1">
	                <dates>
	                  <date date="2015-10-07" type="ga"/>
	                </dates>
	              </release>
	         </icuProduct>
	         <!-- icu4j, icu4jni … -->
	     </icuProducts>
	</icuInfo>

The top level element is `<icuInfo>`. Under the `<icuProducts>` element, there is one `<icuProduct>` entry for the three projects: `icu4c`, `icu4j`,
and the discontinued `<icu4jni>`.

Under the `<releases>` element, there is one `<release>` element for each ICU release.
If the `<release>` is marked with the `draft="proposed"` attribute, it notes that the
release date is not finalized and this element should NOT be considered as the official
release date for ICU.

Under the `<dates>` element there may be multiple `<date>` elements.
The `<date>` element with a `type="ga"` attribute is the only one which may
be used to determine the ICU GA (General Availability date).
The `date=` attribute on the `<date>` element gives the release date, in
Year-Month-Day format. For example, `2015-10-07` indicates October 7th, 2015 AD
(Gregorian calendar).


The actual XML file contains a reference to an XML DTD and may be validated.

JSON version
------------

The JSON version is new, please give feedback if you have any.

The JSON version of ICU metadata is located at the canonical URL:

* http://icu-project.org/xml/icumeta.json

A simplified view of the data follows:

	{
	  "//": "Copyright (c) 2016 IBM Corporation and Others, All Rights Reserved.",
	  "docs": "http://icu-project.org/xml/README.md",
	  "metaversion": 1,
	  "projects": {
	    "icu4c": {
	      "releases": {
	        "56.1": {
	          "dates": {
	            "ga": "2015-10-07"
	          }
	        },
	        "55.1": {
	          "dates": {
	            "ga": "2015-04-01"
	          }
	        },
	      …  many more versions …
	      "proposedReleases": {
	         "57.1": {
	           "dates": {
	           "ga": "2016-03-31"
	         }
	       }
	    },
	    …  icu4j, icu4jni …
	  }
	}

* The `//` value is a copyright string, it may be ignored for processing
* The `docs` value is the URL of this document
* The `metaversion` value is the version number of the metadata document. It's currently "1".
* The `projects` hash contains a value for each of the projects (icu4c, icu4j, icu4jni)
 * Under a project such as `icu4c` is the hash `releases`
  * Under the `releases` hash is a hash for each version, such as `56.1`
   * under `56.1` there is a hash `dates` having a key of `ga` and value with the date in year-month-day format
 * `proposedReleases` (if present) shows any draft (non-final) releases.
