/*
*******************************************************************************
*
*   Copyright (C) 2000-2003, International Business Machines
*   Corporation and others.  All Rights Reserved.
*
*******************************************************************************
*   file name:  ucmkbase.c
*   encoding:   US-ASCII
*   tab size:   8 (not used)
*   indentation:4
*
*   created on: 2003nov01
*   created by: Markus W. Scherer
*
*   This tool generates a base conversion file that is suitable for multiple
*   delta files. See usage text.
*
*   ucmkbase requires a build using the ICU common (icuuc) and toolutil libraries.
*   On Windows (on one line):
*
*   cl -nologo -MD
*      -I..\..\..\icu\source\common
*      -I..\..\..\icu\source\tools\toolutil
*      ucmkbase.c -link /LIBPATH:..\..\..\icu\lib icuuc.lib icutu.lib
*/

/* ### TODO special mode to ignore intersectBase when the base mapping is SBCS? -> for 1390 vs. 16684 */

#include "unicode/utypes.h"
#include "cstring.h"
#include "cmemory.h"
#include "filestrm.h"
#include "ucnv_ext.h"
#include "ucm.h"
#include <stdio.h>

static void
stripEndline(char *s) {
    char *end=uprv_strchr(s, 0);
    while(s<end && *(end-1)=='\r' || *(end-1)=='\n') {
        --end;
    }
    *end=0;
}

extern int
main(int argc, const char *argv[]) {
    char line[200];
    char *key, *value;
    int i;

    FileStream *fileStream;
    UCMFile *baseFile, *extFile;
    UCMStates *baseStates;
    UErrorCode errorCode;
    UBool isSISO, intersectBase;

    baseFile=ucm_open();
    extFile=ucm_open();

    if(argc<3) {
        fprintf(stderr,
            "usage: %s basefile deltafile1 deltafile2 ...\n"
            "\n"
            "    Reads the base file and processes it\n."
            "    Then reads each delta file and processes it such that the resulting base\n"
            "    file's base table is the intersection of itself with all files' mappings\n"
            "    and also does not contain any mappings that would prevent the delta files\n"
            "    from functioning.\n"
            "\n"
            "    The base file extension table gets all mappings that are moved out of\n"
            "    the base file base table.\n"
            "\n"
            "    The resulting base file contains the same set of mappings as the original\n"
            "    base file, with a different distribution between the two tables.\n"
            "    It is output to stdout.\n",
            argv[0]);
    }

    /* parse the base file -------------------------------------------------- */
    fileStream=T_FileStream_open(argv[1], "r");
    if(fileStream==NULL) {
        fprintf(stderr, "unable to open the base file \"%s\"\n", argv[1]);
        return U_FILE_ACCESS_ERROR;
    }

    /* read and copy header */
    for(;;) {
        if(T_FileStream_readLine(fileStream, line, sizeof(line))==NULL) {
            fprintf(stderr, "error: no mapping section");
            return 1;
        }
        stripEndline(line);
        if(0==uprv_strcmp(line, "CHARMAP")) {
            break;
        }
        puts(line);
        ucm_parseHeaderLine(baseFile, line, &key, &value);
    }

    if(baseFile->baseName[0]!=0) {
        fprintf(stderr, "error: \"%s\" is a delta file\n", argv[1]);
        return U_INVALID_TABLE_FORMAT;
    }

    baseStates=&baseFile->states;
    ucm_processStates(baseStates);
    isSISO=(UBool)(baseStates->outputType==MBCS_OUTPUT_2_SISO);

    /* read the base file base table */
    errorCode=U_ZERO_ERROR;
    ucm_readTable(baseFile, fileStream, TRUE, baseStates, &errorCode);
    if(U_FAILURE(errorCode)) {
        fprintf(stderr, "error %s parsing the base file base table\n", u_errorName(errorCode));
        return errorCode;
    }

    /* read the base file extension table, if any */
    while(T_FileStream_readLine(fileStream, line, sizeof(line))!=NULL) {
        stripEndline(line);
        if(line[0]!=0 && line[0]!='#') {
            if(0==uprv_strcmp(line, "CHARMAP")) {
                ucm_readTable(baseFile, fileStream, FALSE, baseStates, &errorCode);
                break;
            } else {
                fprintf(stderr, "unexpected text after the base mapping table\n");
                return U_INVALID_TABLE_FORMAT;
            }
        }
    }

    if(U_FAILURE(errorCode)) {
        fprintf(stderr, "error %s parsing the base file extension table\n", u_errorName(errorCode));
        return errorCode;
    }

    T_FileStream_close(fileStream);

    if(!ucm_checkBaseExt(baseStates, baseFile->base, baseFile->ext, baseFile->ext, FALSE)) {
        fprintf(stderr, "error processing the base file \"%s\"\n", argv[1]);
        return U_INVALID_TABLE_FORMAT;
    }

    /* read and process each delta file ------------------------------------- */
    for(i=2; i<argc; ++i) {
        /* parse a delta file */
        fileStream=T_FileStream_open(argv[i], "r");
        if(fileStream==NULL) {
            fprintf(stderr, "unable to open the delta file \"%s\"\n", argv[i]);
            return U_FILE_ACCESS_ERROR;
        }

        /* read and ignore header, except for DBCS */
        intersectBase=TRUE;
        for(;;) {
            if(T_FileStream_readLine(fileStream, line, sizeof(line))==NULL) {
                fprintf(stderr, "error: no mapping section");
                return 1;
            }
            stripEndline(line);
            if(0==uprv_memcmp(line, "<uconv_class>", 13) && NULL!=uprv_strstr(line, "DBCS")) {
                intersectBase=2; /* special mode for ucm_checkBaseExt() */
            } else if(0==uprv_strcmp(line, "CHARMAP")) {
                break;
            }
        }

        /* read the delta file base table */
        ucm_readTable(extFile, fileStream, FALSE, baseStates, &errorCode);
        if(U_FAILURE(errorCode)) {
            fprintf(stderr, "error %s parsing the delta file \"%s\" base table\n", u_errorName(errorCode), argv[i]);
            return errorCode;
        }

        /* read the delta file extension table, if any */
        while(T_FileStream_readLine(fileStream, line, sizeof(line))!=NULL) {
            stripEndline(line);
            if(line[0]!=0 && line[0]!='#') {
                if(0==uprv_strcmp(line, "CHARMAP")) {
                    ucm_readTable(extFile, fileStream, FALSE, baseStates, &errorCode);
                    break;
                } else {
                    fprintf(stderr, "unexpected text after the \"%s\" base mapping table\n", argv[i]);
                    return U_INVALID_TABLE_FORMAT;
                }
            }
        }

        if(U_FAILURE(errorCode)) {
            fprintf(stderr, "error %s parsing the delta file \"%s\" extension table\n", u_errorName(errorCode), argv[i]);
            return errorCode;
        }

        T_FileStream_close(fileStream);

        if(!ucm_checkBaseExt(baseStates, baseFile->base, extFile->ext, baseFile->ext, intersectBase)) {
            fprintf(stderr, "error processing the delta file \"%s\"\n", argv[i]);
            return U_INVALID_TABLE_FORMAT;
        }

        ucm_resetTable(extFile->ext);
    }

    /* sort and write all mappings; write at least one table, even if empty */
    puts("CHARMAP");
    ucm_printTable(baseFile->base, stdout, TRUE);
    puts("END CHARMAP");

    if(baseFile->ext->mappingsLength>0) {
        puts("\nCHARMAP");
        ucm_printTable(baseFile->ext, stdout, TRUE);
        puts("END CHARMAP");
    }

    ucm_close(baseFile);
    ucm_close(extFile);
    return 0;
}
