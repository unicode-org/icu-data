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
*   cl -nologo
*      -I..\..\..\icu\source\common
*      -I..\..\..\icu\source\tools\toolutil
*      canonucm.c -link /LIBPATH:..\..\..\icu\lib icuucd.lib icutud.libcanonucm.c
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
    UBool byUnicode;

    if(argc>=2 && 0==uprv_strcmp(argv[1], "-b")) {
        byUnicode=FALSE;
    } else {
        byUnicode=TRUE;
    }

    ucm=ucm_open();
    baseStates=NULL;

    /* parse the input file from stdin */
    /* read and copy header */
    do {
        if(gets(line)==NULL) {
            fprintf(stderr, "error: no mapping section");
            return 1;
        }
        puts(line);
    } while(ucm_parseHeaderLine(ucm, line, &key, &value) ||
            0!=uprv_strcmp(line, "CHARMAP"));

    ucm_processStates(&ucm->states);

    /*
     * If there is _no_ <icu:base> base table name, then parse the base table
     * and then an optional extension table.
     *
     * If there _is_ a base table name, then only parse
     * the then-mandatory extension table.
     */
    if(ucm->baseName[0]==0) {
        /* copy empty and comment lines before the first mapping */
        for(;;) {
            if(gets(line)==NULL) {
                fprintf(stderr, "error: no mappings");
                return 1;
            }
            if(line[0]!=0 && line[0]!='#') {
                break;
            }
            puts(line);
        }

        baseStates=&ucm->states;

        /* process the base charmap section, start with the line read above */
        for(;;) {
            /* ignore empty and comment lines */
            if(line[0]!=0 && line[0]!='#') {
                if(0!=uprv_strcmp(line, "END CHARMAP")) {
                    if(!ucm_addMappingFromLine(ucm, line, TRUE, baseStates)) {
                        exit(U_INVALID_TABLE_FORMAT);
                    }
                } else {
                    /* sort and write all mappings */
                    if(!ucm_checkBaseExt(baseStates, ucm->base, ucm->ext, TRUE)) {
                        return U_INVALID_TABLE_FORMAT;
                    }
                    ucm_printTable(ucm->base, stdout, byUnicode);

                    /* output "END CHARMAP" */
                    puts(line);
                    break;
                }
            }
            /* read the next line */
            if(gets(line)==NULL) {
                fprintf(stderr, "incomplete charmap section\n");
                return U_INVALID_TABLE_FORMAT;
            }
        }
    }

    /* do the same with an extension table section, ignore lines before it */
    for(;;) {
        if(gets(line)==NULL) {
            if(ucm->baseName[0]==0) {
                break; /* the extension table is optional if we parsed a base table */
            } else {
                fprintf(stderr, "missing extension charmap section when <icu:base> specified\n");
                return U_INVALID_TABLE_FORMAT;
            }
        }
        if(line[0]!=0 && line[0]!='#') {
            if(uprv_strcmp(line, "CHARMAP")) {
                /* process the extension table's charmap section, start with the line read above */
                for(;;) {
                    if(gets(line)==NULL) {
                        fprintf(stderr, "incomplete extension charmap section\n");
                        return U_INVALID_TABLE_FORMAT;
                    }

                    /* ignore empty and comment lines */
                    if(line[0]!=0 && line[0]!='#') {
                        if(0!=uprv_strcmp(line, "END CHARMAP")) {
                            if(!ucm_addMappingFromLine(ucm, line, FALSE, baseStates)) {
                                exit(U_INVALID_TABLE_FORMAT);
                            }
                        } else {
                            break;
                        }
                    }
                }
                break;
            } else {
                fprintf(stderr, "unexpected text after the base mapping table\n");
                return U_INVALID_TABLE_FORMAT;
            }
        }
    }

    if(ucm->ext->mappingsLength>0) {
        puts("\nCHARMAP");

        /* sort and write all extension mappings */
        ucm_sortTable(ucm->ext);
        ucm_printTable(ucm->ext, stdout, byUnicode);

        /* output "END CHARMAP" */
        puts(line);
    }

    ucm_close(ucm);
    return 0;
}
