# Copyright (C) 2006-2011 IBM and Others. All Rights Reserved.

all:
	@echo 
	@echo You are in a maze of twisty subdirectories, all alike.
	@echo You see:
	@echo '   A moldy scroll labeled "readme.txt"'
	@echo

ICU_CONFIG=icu-config
FETCH=wget -nc -nd
FULL_BUILD_OLD=false
BUILD_OLD_STRUCTURE=false
-include Makefile.local

TZSRC=$(ICU_SRC)/tools/tzcode
VERSION_LIST=36 38 40 42 44
ENDIAN_LIST=be le ee

update-mirror:
	cd mirror && ${FETCH} 'ftp://munnari.oz.au/pub/tzdata*'
	svn add mirror/tzdata*.tar.gz
	cd mirror && ${FETCH} 'ftp://munnari.oz.au/pub/tzcode*'
	svn add mirror/tzcode*.tar.gz

update-icu:
	@for tzver in `ls mirror/tzdata*.tar.gz` ; do \
		tzver=`basename $$tzver .tar.gz | cut -c7-`; \
		tzyear=`echo $$tzver | cut -c1-4`; \
		echo "Processing $$tzver"; \
		if [ $$tzyear -ge 2010 ]; then \
			$(MAKE) icunew/$$tzver/zoneinfo64.txt; \
			$(MAKE) icu/$$tzver/zoneinfo.txt; \
		else \
			echo "No loner genearate tzdata for year 2009 or older."; \
		fi \
	done

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
	@for icuver in $(VERSION_LIST); do \
		echo "### Processing $$icuver" ; \
		LOGFILE=`basename $(@D)`.log ; \
		mkdir $(@D)/$$icuver ; \
		for endian in $(ENDIAN_LIST); do \
			mkdir $(@D)/$$icuver/$$endian ; \
		done ; \
		GENRBOPT="--formatVersion 1" ; \
		if [ $$icuver = "36" ]; then \
			TXTFILES="$(TZSRC)/zoneinfo.txt" ; \
		elif [ $$icuver = "38" ]; then \
			TXTFILES="$(TZSRC)/zoneinfo.txt supplemental/metazoneInfo.txt supplemental/38/supplementalData.txt" ; \
		elif [ $$icuver = "40" ]; then \
			TXTFILES="$(TZSRC)/zoneinfo.txt supplemental/metazoneInfo.txt supplemental/40/supplementalData.txt" ; \
		elif [ $$icuver = "42" ]; then \
			TXTFILES="$(TZSRC)/zoneinfo.txt supplemental/metazoneInfo.txt supplemental/42/supplementalData.txt" ; \
		else \
			TXTFILES="$(TZSRC)/zoneinfo64.txt supplemental/metaZones.txt supplemental/windowsZones.txt supplemental/timezoneTypes.txt" ; \
			GENRBOPT="" ; \
		fi ; \
		for srctxt in $$TXTFILES; do \
			cp $$srctxt $(@D)/$$icuver ; \
			$(shell icu-config --invoke=genrb) $$GENRBOPT -d $(@D)/$$icuver $$srctxt 2>> $$LOGFILE >> $$LOGFILE ; \
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
				$(shell icu-config --invoke=icupkg) $$ENDIANARG -s $(@D)/$$icuver $$RESFILE $(@D)/$$icuver/$$endian/$$RESFILE 2>> $$LOGFILE >> $$LOGFILE ; \
			done ; \
			rm $(@D)/$$icuver/$$RESFILE ; \
		done ; \
	done

	find $(@D)/* -type f
	svn add $(@D)
	@echo "## Build OK."

ifeq (BUILD_OLD_STRUCTURE, true)
icu/%/zoneinfo.txt: mirror/tzdata%.tar.gz mirror/tzcode%.tar.gz
	@test ! -f $@ || (echo Remove $(@D)  and $@ manually - will not overwrite. ; touch $@ ;  false)
	@test ! -d $(@D) || (echo Remove $(@D)  manually - will not overwrite. ;   false)
ifeq (FULL_BUILD_OLD, true)
	@echo "#" building $(@D) .. results are in ./$(shell basename $(@D))_old.log
	@$(MAKE) -C $(TZSRC) dataclean 2> $(shell basename $(@D))_old.log > $(shell basename $(@D))_old.log
	@rm -f $(TZSRC)/tzdata*.tar.gz 2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log
	@cp $^ $(TZSRC)

	@echo "## Building all in $(TZSRC)"
	@$(MAKE) -C $(TZSRC) all  2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log

	@echo "## Rebuilding icu data in $(ICU_SRC)/data"
	@$(MAKE) -C $(ICU_SRC)/data  2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log

	@echo "## Testing icu data against tz algorithm"
	@$(MAKE) -C $(TZSRC) check-dump  2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log

	@echo "## Test passed."
endif
	@mkdir $(@D)
	cp $(TZSRC)/zoneinfo.txt supplemental/metazoneInfo.txt $(@D)
	@mkdir $(@D)/38
	cp supplemental/38/supplementalData.txt $(@D)/38
	@mkdir $(@D)/40
	cp supplemental/40/supplementalData.txt $(@D)/40
	@mkdir $(@D)/42
	cp supplemental/42/supplementalData.txt $(@D)/42

	@echo "#" genrb and creating le/be/ee versions
	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D) -d $(@D) zoneinfo.txt 2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log
	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D) -d $(@D) metazoneInfo.txt 2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log
	@mkdir $(@D)/le $(@D)/be $(@D)/ee
	@$(shell icu-config --invoke=icupkg) -s $(@D) -tl zoneinfo.res $(@D)/le/zoneinfo.res
	@$(shell icu-config --invoke=icupkg) -s $(@D) -tb zoneinfo.res $(@D)/be/zoneinfo.res
	@$(shell icu-config --invoke=icupkg) -s $(@D) -te zoneinfo.res $(@D)/ee/zoneinfo.res
	@rm $(@D)/zoneinfo.res
	@$(shell icu-config --invoke=icupkg) -s $(@D) -tl metazoneInfo.res $(@D)/le/metazoneInfo.res
	@$(shell icu-config --invoke=icupkg) -s $(@D) -tb metazoneInfo.res $(@D)/be/metazoneInfo.res
	@$(shell icu-config --invoke=icupkg) -s $(@D) -te metazoneInfo.res $(@D)/ee/metazoneInfo.res
	@rm $(@D)/metazoneInfo.res

	@echo "#" creating version specific supplementalData resource files

	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D)/38 -d $(@D)/38 supplementalData.txt 2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log
	@mkdir $(@D)/le/38 $(@D)/be/38 $(@D)/ee/38
	@$(shell icu-config --invoke=icupkg) -s $(@D)/38 -tl supplementalData.res $(@D)/le/38/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/38 -tb supplementalData.res $(@D)/be/38/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/38 -te supplementalData.res $(@D)/ee/38/supplementalData.res
	@rm $(@D)/38/supplementalData.res

	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D)/40 -d $(@D)/40 supplementalData.txt 2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log
	@mkdir $(@D)/le/40 $(@D)/be/40 $(@D)/ee/40
	@$(shell icu-config --invoke=icupkg) -s $(@D)/40 -tl supplementalData.res $(@D)/le/40/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/40 -tb supplementalData.res $(@D)/be/40/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/40 -te supplementalData.res $(@D)/ee/40/supplementalData.res
	@rm $(@D)/40/supplementalData.res

	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D)/42 -d $(@D)/42 supplementalData.txt 2>> $(shell basename $(@D))_old.log >> $(shell basename $(@D))_old.log
	@mkdir $(@D)/le/42 $(@D)/be/42 $(@D)/ee/42
	@$(shell icu-config --invoke=icupkg) -s $(@D)/42 -tl supplementalData.res $(@D)/le/42/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/42 -tb supplementalData.res $(@D)/be/42/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/42 -te supplementalData.res $(@D)/ee/42/supplementalData.res
	@rm $(@D)/42/supplementalData.res

	find $(@D)/* -type f
	svn add $(@D)
else
icu/%/zoneinfo.txt:
	# Do nothing
endif

update: update-mirror update-icu

clean:
	-rm -f ./*.log
