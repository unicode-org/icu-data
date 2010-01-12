# Copyright (C) 2006-2010 IBM and Others. All Rights Reserved.

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

update-mirror:
	cd mirror && ${FETCH} 'ftp://elsie.nci.nih.gov/pub/tzdata*'
	svn add mirror/tzdata*.tar.gz
	cd mirror && ${FETCH} 'ftp://elsie.nci.nih.gov/pub/tzcode*'
	svn add mirror/tzcode*.tar.gz

update-icu:
	@for tzver in `ls mirror/tzdata*.tar.gz` ; do tzver=`basename $$tzver .tar.gz | cut -c7-`; $(MAKE) icu/$$tzver/zoneinfo.txt; done

icu/%/zoneinfo.txt: mirror/tzdata%.tar.gz mirror/tzcode%.tar.gz
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

	@echo "## Build OK."

	@mkdir $(@D)
	cp $(TZSRC)/zoneinfo.txt supplemental/metazoneInfo.txt $(@D)
	@mkdir $(@D)/38
	cp supplemental/38/supplementalData.txt $(@D)/38
	@mkdir $(@D)/40
	cp supplemental/40/supplementalData.txt $(@D)/40
	@mkdir $(@D)/42
	cp supplemental/42/supplementalData.txt $(@D)/42

	@echo "#" genrb and creating le/be/ee versions
	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D) -d $(@D) zoneinfo.txt 2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log
	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D) -d $(@D) metazoneInfo.txt 2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log
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

	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D)/38 -d $(@D)/38 supplementalData.txt 2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log
	@mkdir $(@D)/le/38 $(@D)/be/38 $(@D)/ee/38
	@$(shell icu-config --invoke=icupkg) -s $(@D)/38 -tl supplementalData.res $(@D)/le/38/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/38 -tb supplementalData.res $(@D)/be/38/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/38 -te supplementalData.res $(@D)/ee/38/supplementalData.res
	@rm $(@D)/38/supplementalData.res

	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D)/40 -d $(@D)/40 supplementalData.txt 2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log
	@mkdir $(@D)/le/40 $(@D)/be/40 $(@D)/ee/40
	@$(shell icu-config --invoke=icupkg) -s $(@D)/40 -tl supplementalData.res $(@D)/le/40/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/40 -tb supplementalData.res $(@D)/be/40/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/40 -te supplementalData.res $(@D)/ee/40/supplementalData.res
	@rm $(@D)/40/supplementalData.res

	@$(shell icu-config --invoke=genrb) --formatVersion 1 -s $(@D)/42 -d $(@D)/42 supplementalData.txt 2>> $(shell basename $(@D)).log >> $(shell basename $(@D)).log
	@mkdir $(@D)/le/42 $(@D)/be/42 $(@D)/ee/42
	@$(shell icu-config --invoke=icupkg) -s $(@D)/42 -tl supplementalData.res $(@D)/le/42/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/42 -tb supplementalData.res $(@D)/be/42/supplementalData.res
	@$(shell icu-config --invoke=icupkg) -s $(@D)/42 -te supplementalData.res $(@D)/ee/42/supplementalData.res
	@rm $(@D)/42/supplementalData.res

	find $(@D)/* -type f
	svn add $(@D)

update: update-mirror update-icu

clean:
	-rm -f ./*.log
