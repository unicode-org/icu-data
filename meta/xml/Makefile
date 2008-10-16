# Copyright (C) 2008, International Business Machines Corporation and others.  All Rights Reserved.

all: icuinfo.xml

XML_META=icumeta.xml
XML_TESTING=$(shell ls testing_*.xml)

XML_ALL=$(XML_META) $(XML_TESTING)
CLASSPATH=lib/cldr.jar:lib/icu4j.jar:lib/utilities.jar:lib/xalan.jar:lib/

icuinfo.xml: $(XML_ALL) lib/Merger.class tmp/
	-mv $@ $@~
	java -DCLDR_DTD_DEBUG=y -DCLDR_DTD_CACHE=tmp -DCLDR_DTC_OVERRIDE=../dtd/ -classpath $(CLASSPATH) Merger $(XML_ALL) > $@ || ( rm $@ ; exit 1 )

tmp:
	mkdir tmp/

lib/Merger.class: lib/Merger.java
	javac  -classpath $(CLASSPATH)  -d lib lib/Merger.java
