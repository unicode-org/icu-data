# Copyright (c) 2008 IBM Corp. All Rights Reserved.
#
# Auto gen .dtd and .xsd from .rnc.   ( RelaxNG format schema )


# latest version from http://www.thaiopensource.com/download/ - if download fails, check these numbers
TRANG.VER=20030619
JING.VER=20030619

# List of source RNCs.
RNCS=icumeta.rnc
YEAR=$(shell date '+%Y')
COPYRIGHT=Copyright (C) $(YEAR) IBM Corporation and Others. All Rights Reserved.

# --- boring stuff under this line --

# Stuff for trang and jing
TRANG=trang-$(TRANG.VER)
TRANG.JAR=$(TRANG)/trang.jar
TRANG.RUN=java -classpath $(JING.JAR) -jar $(TRANG.JAR)
JING=jing-$(JING.VER)
JING.JAR=$(JING)/jing.jar

DTDS=$(RNCS:%.rnc=%.dtd)
XSDS=$(RNCS:%.rnc=%.xsd)

all: $(DTDS) $(XSDS)

clean:
	-rm -rf trang-* jing-*
	
distclean: clean
	-rm -f $(DTDS) $(XSDS)

%.dtd: %.rnc $(TRANG)
	$(TRANG.RUN) -I rnc -O dtd $< $@

%.xsd: %.rnc $(TRANG)
	$(TRANG.RUN) -I rnc -O xsd $< $@ || ( rm $@ ; false )
	( mv $@ _$@ ; echo "<!-- $(COPYRIGHT) -->" > $@ ; cat _$@ >> $@ ; rm _$@ ) || (rm $@;false)

$(TRANG): $(TRANG).zip $(JING)
	unzip $(TRANG).zip

$(TRANG).zip:
	wget -c http://www.thaiopensource.com/download/$(TRANG).zip

$(JING).zip:
	wget -c http://www.thaiopensource.com/download/$(JING).zip

$(JING): $(JING).zip
	unzip $(JING).zip

