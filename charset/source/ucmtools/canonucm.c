/*
*******************************************************************************
*
*   Copyright (C) 2000-2003, International Business Machines
*   Corporation and others.  All Rights Reserved.
*
*******************************************************************************
*   file name:  canonucm.c
*   encoding:   US-ASCII
*   tab size:   8 (not used)
*   indentation:4
*
*   created on: 2000nov08
*   created by: Markus W. Scherer
*
*   This tool reads a .ucm file and canonicalizes it: In the CHARMAP section,
*   - sort by Unicode code points
*   - print all code points in uppercase hexadecimal
*   - print all Unicode code points with 4, 5, or 6 digits as needed
*   - remove the comments
*   - remove unnecessary spaces
*
*   Use the -b option to sort the output by bytes instead of by Unicode.
*
*   Starting 2003oct09, canonucm handles m:n mappings as well, but requires
*   a more elaborate build using the ICU common (icuuc) and toolutil libraries.
*   On Windows (on one line):
*
*   cl -nologo -MD
*      -I..\..\..\icu\source\common
*      -I..\..\..\icu\source\tools\toolutil
*      canonucm.c -link /LIBPATH:..\..\..\icu\lib icuuc.lib icutu.lib
*/

#include "unicode/utypes.h"
#include "cstring.h"
#include "ucnv_ext.h"
#include "ucm.h"
#include <stdio.h>

extern int
main(int argc, const char *argv[]) {
    char line[200];
    char *key, *value;

    UCMFile *ucm;
    UCMStates *baseStates;
    UBool byUnicode, forBase, isOK;

    if(argc>=2 && 0==uprv_strcmp(argv[1], "-b")) {
        byUnicode=FALSE;
    } else {
        byUnicode=TRUE;
    }

    ucm=ucm_open();

    /* parse the input file from stdin */
    /* read and copy header */
    for(;;) {
        if(gets(line)==NULL) {
            fprintf(stderr, "error: no mapping section");
            return 1;
        }
        if(0==uprv_strcmp(line, "CHARMAP")) {
            break;
        }
        puts(line);
        ucm_parseHeaderLine(ucm, line, &key, &value);
    }

    /*
     * If there is _no_ <icu:base> base table name, then parse the base table
     * and then an optional extension table.
     *
     * If there _is_ a base table name, then parse one or two tables as well
     * but put all mappings into the extension table.
     */
    if(ucm->baseName[0]==0) {
        baseStates=&ucm->states;
        ucm_processStates(baseStates);
        forBase=TRUE;
    } else {
        baseStates=NULL;
        forBase=FALSE;
    }

    isOK=TRUE;

    /* parse the base charmap section */
    for(;;) {
        /* read the next line */
        if(gets(line)==NULL) {
            fprintf(stderr, "incomplete charmap section\n");
            return U_INVALID_TABLE_FORMAT;
        }

        if(0==uprv_strcmp(line, "END CHARMAP")) {
            break;
        }
        if(!ucm_addMappingFromLine(ucm, line, forBase, baseStates)) {
            isOK=FALSE;
        }
    }

    if(!isOK) {
        fprintf(stderr, "error parsing the first CHARMAP");
        return U_INVALID_TABLE_FORMAT;
    }

    /* do the same with an optional extension table section, ignore lines before it */
    while(gets(line)!=NULL) {
        if(line[0]!=0 && line[0]!='#') {
            if(0==uprv_strcmp(line, "CHARMAP")) {
                /* process the extension table's charmap section */
                for(;;) {
                    if(gets(line)==NULL) {
                        fprintf(stderr, "incomplete extension charmap section\n");
                        return U_INVALID_TABLE_FORMAT;
                    }

                    if(0==uprv_strcmp(line, "END CHARMAP")) {
                        break;
                    }
                    if(!ucm_addMappingFromLine(ucm, line, FALSE, baseStates)) {
                        isOK=FALSE;
                    }
                }
                break;
            } else {
                fprintf(stderr, "unexpected text after the base mapping table\n");
                return U_INVALID_TABLE_FORMAT;
            }
        }
    }

    if(!isOK) {
        fprintf(stderr, "error parsing the second CHARMAP");
        return U_INVALID_TABLE_FORMAT;
    }

    /* sort and write all mappings; write at least one table, even if empty */
    if(ucm->baseName[0]==0) {
        if(!ucm_checkBaseExt(baseStates, ucm->base, ucm->ext, ucm->ext, FALSE)) {
            return U_INVALID_TABLE_FORMAT;
        }

        puts("CHARMAP");
        ucm_printTable(ucm->base, stdout, byUnicode);
        puts("END CHARMAP");

        if(ucm->ext->mappingsLength>0) {
            puts("\nCHARMAP");
            ucm_printTable(ucm->ext, stdout, byUnicode);
            puts("END CHARMAP");
        }
    } else {
        ucm_sortTable(ucm->ext);

        puts("CHARMAP");
        ucm_printTable(ucm->ext, stdout, byUnicode);
        puts("END CHARMAP");
    }

    ucm_close(ucm);
    return 0;
}
