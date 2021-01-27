# (C) 2016 and later: Unicode, Inc. and others.
# License & terms of use: http://www.unicode.org/copyright.html#License

# Copyright (C) 2006-2015 IBM and Others. All Rights Reserved.

all:
	@echo 
	@echo You are in a maze of twisty subdirectories, all alike.
	@echo You see:
	@echo '   A moldy scroll labeled "readme.txt"'
	@echo

ICU_CONFIG=icu-config
FETCH=wget -nc -nd
-include Makefile.local

TZSRC=$(ICU_SRC)/tools/tzcode
ENDIAN_LIST=be le ee
ICUVER=44

update-mirror:
ifndef TZDBVER
	$(error TZDBVER is not defined.)
endif
	cd mirror && ${FETCH} 'ftp://ftp.iana.org/tz/releases/tzdata${TZDBVER}.tar.gz'
	cd mirror && ${FETCH} 'ftp://ftp.iana.org/tz/releases/tzcode${TZDBVER}.tar.gz'

update-icu:
	@echo 'Processing ${TZDBVER}'
	$(MAKE) icunew/${TZDBVER}/zoneinfo64.txt

icunew/%/zoneinfo64.txt: mirror/tzdata%.tar.gz mirror/tzcode%.tar.gz
	@test ! -f $@ || (echo Remove $(@D)  and $@ manually - will not overwrite. ; touch $@ ;  false)
	@test ! -d $(@D) || (echo Remove $(@D)  manually - will not overwrite. ;   false)
	@echo "#" building $(@D) .. results are in ./$(shell basename $(@D)).log
	@$(MAKE) -C $(TZSRC) dataclean 2> $(shell basename $(@D)).log > $(shell basename $(@D)).log
	@rm -f $(TZSRC)/tzdata*.tar.gz 2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log
	@cp $^ $(TZSRC)

	@echo "## Building all in $(TZSRC)"
	@$(MAKE) -C $(TZSRC) all  2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log

	@echo "## Rebuilding icu data in $(ICU_SRC)/data"
	@$(MAKE) -C $(ICU_SRC)/data  2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log

	@echo "## Testing icu data against tz algorithm"
	@$(MAKE) -C $(TZSRC) check-dump  2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log
	@echo "## Test passed"

	@echo "## Building resource bundles for all ICU versions"

	@mkdir $(@D)
	@mkdir $(@D)/${ICUVER}
	@for endian in $(ENDIAN_LIST); do \
		mkdir $(@D)/$(ICUVER)/$$endian; \
	done

	@LOGFILE=`basename $(@D)`.log ; \
	GENRBOPT="--formatVersion 2" ; \
	TXTFILES="$(TZSRC)/zoneinfo64.txt supplemental/metaZones.txt supplemental/windowsZones.txt supplemental/timezoneTypes.txt" ; \
	for srctxt in $$TXTFILES; do \
		cp $$srctxt $(@D)/$(ICUVER) ; \
		$(shell icu-config --invoke=genrb) $$GENRBOPT -d $(@D)/$(ICUVER) $$srctxt 2>> $$LOGFILE >> $$LOGFILE ; \
		RESFILE=`basename $$srctxt .txt`.res; \
		for endian in $(ENDIAN_LIST); do \
			echo "### Source: $$srctxt Endian: $$endian" ; \
			if [ $$endian = "be" ]; then \
				ENDIANARG="-tb" ; \
			elif [ $$endian = "le" ]; then \
				ENDIANARG="-tl" ; \
			else \
				ENDIANARG="-te" ; \
			fi ; \
			$(shell icu-config --invoke=icupkg) $$ENDIANARG -s $(@D)/$(ICUVER) $$RESFILE $(@D)/$(ICUVER)/$$endian/$$RESFILE 2>> $$LOGFILE >> $$LOGFILE ; \
		done ; \
		rm $(@D)/$(ICUVER)/$$RESFILE ; \
	done

	@find $(@D)/* -type f
	@echo "## Build OK."

update: update-mirror update-icu

clean:
	-rm -f ./*.log
