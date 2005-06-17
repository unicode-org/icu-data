/*
*******************************************************************************
*
*   Copyright (C) 2000-2005, International Business Machines
*   Corporation and others.  All Rights Reserved.
*
*******************************************************************************
*   file name:  rptp2ucm.c
*   encoding:   US-ASCII
*   tab size:   8 (not used)
*   indentation:4
*
*   created on: 2001feb16
*   created by: Markus W. Scherer
*
*   This tool reads two CDRA conversion table files (RPMAP & TPMAP or RXMAP and TXMAP) and
*   generates a canonicalized ICU .ucm file from them.
*   If the RPMAP/RXMAP file does not contain a comment line with the substitution character,
*   then this tool also attempts to read the header of the corresponding UPMAP/UXMAP file
*   to extract subchar and subchar1.
*
*   R*MAP: Unicode->codepage
*   T*MAP: codepage->Unicode
*
*   Starting 2003oct25, rptp2ucm handles m:n mappings as well, but requires
*   a more elaborate build using the ICU common (icuuc) and toolutil libraries.
*   On Windows (on one line):
*
*   cl -nologo -MD
*      -I..\..\..\icu\source\common
*      -I..\..\..\icu\source\tools\toolutil
*      rptp2ucm.c -link /LIBPATH:..\..\..\icu\lib icuuc.lib icutu.lib
*/

#include "unicode/utypes.h"
#include "unicode/ustring.h"
#include "cmemory.h"
#include "cstring.h"
#include "ucnv_ext.h"
#include "ucm.h"
#include "uparse.h"
#include <stdio.h>
#include <time.h>

#define LENGTHOF(array) (int32_t)(sizeof(array)/sizeof((array)[0]))

typedef struct UCMSubchar {
    const char *name;
    uint32_t subchar, subchar1;
} UCMSubchar;

static const UCMSubchar
knownSubchars[]={
    "274_P100", 0x3f, 0,
    "850_P100", 0x7f, 0,
    "913_P100", 0x1a, 0,
    "1047_P100", 0x3f, 0,
    "8612_X110", 0x3f, 0
};

typedef struct CCSIDStateTable {
    uint16_t ccsid;
    const char *table;
} CCSIDStateTable;

#define japanesePCDBCSStates \
    "<icu:state>                   0-80:2, 81-fc:1, fd-ff:2\n" \
    "<icu:state>                   40-7e, 80-fc\n" \
    "<icu:state>\n"

#define states1390 \
    "# includes mappings for surrogate pairs\n" \
    "<icu:state>                   0-ff, e:1.s, f:0.s\n" \
    "<icu:state>                   initial, 0-3f:4, e:1.s, f:0.s, 40:3, 41-fe:2, ff:4, b3-b7:5\n" \
    "<icu:state>                   0-40:1.i, 41-fe:1., ff:1.i\n" \
    "<icu:state>                   0-ff:1.i, 40:1.\n" \
    "<icu:state>                   0-ff:1.i\n" \
    "<icu:state>                   0-40:1.i, 41-fe:1.p, ff:1.i\n"

#define states16684 \
    "# includes mappings for surrogate pairs\n" \
    "<icu:state>                   0-3f:3, 40:2, 41-fe:1, ff:3, b3-b7:4\n" \
    "<icu:state>                   41-fe\n" \
    "<icu:state>                   40\n" \
    "<icu:state>                   \n" \
    "<icu:state>                   41-fe.p\n"

static const CCSIDStateTable
knownStateTables[]={

    301,  "<icu:state>                   0-80:2, 81-9f:1, a0-df:2, e0-fc:1, fd-ff:2\n"
          "<icu:state>                   40-7e, 80-fc\n"
          "<icu:state>\n",

    367,   "<icu:state>                   0-7f\n",

    927, japanesePCDBCSStates,

    926, japanesePCDBCSStates,

    928, japanesePCDBCSStates,

    932, "<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"
         "<icu:state>                   40-7e, 80-fc\n",

    941,  japanesePCDBCSStates,

    942,   "<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"
           "<icu:state>                   40-7e, 80-fc\n",

    943,   "<icu:state>                   0-7f, 81-9f:1, a0-df, e0-fc:1\n"
           "<icu:state>                   40-7e, 80-fc\n",

    944,   "<icu:state>                   0-80, 81-bf:1, c0-ff\n"
           "<icu:state>                   40-7e, 80-fe\n",

    946,   "<icu:state>                   0-80, 81-fb:1,fc:2,fd-ff\n"
           "<icu:state>                   40-7e, 80-fe\n"
           "<icu:state>                   80-fe.u,fc",

    947,   "<icu:state>                   81-fe:1\n"
           "<icu:state>                   81-fe\n",

    948,   "<icu:state>                   0-80, 81-fb:1,fc:2,fd-fe\n"
           "<icu:state>                   40-7e, 80-fe\n"
           "<icu:state>                   80-fe.u,fc\n",

    949,   "<icu:state>                   0-84, 8f-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    950,   "<icu:state>                   0-7f, 81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    954,   "<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-e4\n"
           "<icu:state>                   a1-fe:1, a1:4\n"
           "<icu:state>                   a1-fe.u\n",

    955,   "<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    963,   "<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    964,   "<icu:state>                   0-8d, 8e:2, 90-9f, a1-fe:1, aa-c1:5, c3:5, fe:5\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-b0:3, a1:4, a2:8, a3-ab:4, ac:7, ad:6, ae-b0:4\n"
           "<icu:state>                   a1-fe:1\n"
           "<icu:state>                   a1-fe:5\n"
           "<icu:state>                   a1-fe.u\n"
           "<icu:state>                   a1-a4:1, a5-fe:5\n"
           "<icu:state>                   a1-e2:1, e3-fe:5\n"
           "<icu:state>                   a1-f2:1, f3-fe:5\n",

    970,   "<icu:state>                   0-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n",

    1363,  "<icu:state>                   0-7f, 81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    1350,  "<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-e4\n"
           "<icu:state>                   a1-fe:1, a1:4, a3-a5:4, a8:4, ac-af:4, ee-f2:4\n"
           "<icu:state>                   a1-fe.u\n",

    1351,  "<icu:state>                   0-ff:2, 81-9f:1, e0-fc:1\n"
           "<icu:state>                   40-7e, 80-fc\n"
           "<icu:state>\n",

    1370,  "<icu:state>                   0-80, 81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    1373,  "<icu:state>                   0-7f, 81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    1375,  "<icu:state>                   0-7f, 81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    1381,  "<icu:state>                   0-84, 8c-fe:1\n"
           "<icu:state>                   a1-fe\n",

    1383,  "<icu:state>                   0-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n",

    1385,  "<icu:state>                   0-80:2, 81-fe:1, ff:2\n"
           "<icu:state>                   40-7e, 80-fe\n"
           "<icu:state>\n",

    1386,  "<icu:state>                   0-80, 81-fe:1\n" /* Was 0-7f, 81-fe:1 */
           "<icu:state>                   40-7e, 80-fe\n",

    1390, states1390,

    1399, states1390,

    5039,  "<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"
           "<icu:state>                   40-7e, 80-fc\n",

    5050,  "<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-e4\n"
           "<icu:state>                   a1-fe:1, a1:4, a3-af:4, b6:4, d6:4, da-db:4, ed-f2:4\n"
           "<icu:state>                   a1-fe.u\n",

    5067,  "<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    5478,  "<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    5487,  "<icu:state>                   81-fe:1\n"
           "<icu:state>                   30-39:2\n"
           "<icu:state>                   81-fe:3\n"
           "<icu:state>                   30-39\n",

    5488,  "<icu:state> 0-7f, 81:7, 82:8, 83:9, 84:a, 85-fe:4\n"    /* Modified form of ICU's gb18030 */
           "<icu:state> 30-39:2, 40-7e, 80-fe\n"
           "<icu:state> 81-fe:3\n"
           "<icu:state> 30-39\n"
           "<icu:state> 30-39:5, 40-7e, 80-fe\n"
           "<icu:state> 81-fe:6\n"
           "<icu:state> 30-39\n"
           "<icu:state> 30:2, 31-35:5, 36-39:2, 40-7e, 80-fe\n"
           "<icu:state> 30-35:2, 36-39:5, 40-7e, 80-fe\n"
           "<icu:state> 30-35:5, 36:2, 37-39:5, 40-7e, 80-fe\n"
           "<icu:state> 30-31:2, 32-39:5, 40-7e, 80-fe\n",

    9577,  "<icu:state>                   81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    16684, states16684,

    21427, "<icu:state>                   0-80:2, 81-fe:1, ff:2\n"
           "<icu:state>                   40-7e, 80-fe\n"
           "<icu:state>\n",

    25546, "<icu:state>                   0-7f, e:1.s, f:0.s\n"
           "<icu:state>                   initial, 0-20:3, e:1.s, f:0.s, 21-7e:2, 7f-ff:3\n"
           "<icu:state>                   0-20:1.i, 21-7e:1., 7f-ff:1.i\n"
           "<icu:state>                   0-ff:1.i\n",

    33722, "<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-e4\n"
           "<icu:state>                   a1-fe:1, a1:4, a3-af:4, b6:4, d6:4, da-db:4, ed-f2:4\n"
           "<icu:state>                   a1-fe.u\n"


};

#define MAX_YEAR 2900
#define MIN_YEAR 1940

static UCMFile *fromUFile, *toUFile;

static uint32_t subchar, subchar1;
static uint16_t ccsid;

/*Year when the ucm files were produced using this tool*/
static uint16_t year;

enum {
    U_UNKNOWN_CHARSET_FAMILY=9
};

static uint32_t minTwoByte, maxTwoByte;

static int32_t
    minCharLength,
    maxCharLength;

static uint8_t charsetFamily, oredBytes;

static UBool
    usesPUA,
    variantLF,
    variantASCII,
    variantControls,
    variantSUB,
    is7Bit,
    is_0xe_0xf_Stateful,
    isYearModificationDate;

static void
init() {
    fromUFile=ucm_open();
    toUFile=ucm_open();

    subchar=subchar1=0;
    ccsid=0;
    year=0;

    minTwoByte=0xffff;
    maxTwoByte=0;

    minCharLength=0;
    maxCharLength=0;
    charsetFamily=U_UNKNOWN_CHARSET_FAMILY;
    oredBytes=0;

    usesPUA=0;
    variantLF=0;
    variantASCII=0;
    variantControls=0;
    variantSUB=0;
    is7Bit=0;
    is_0xe_0xf_Stateful=0;
    isYearModificationDate=0;
}

static int32_t
parseDigit(char c) {
    if('0'<=c && c<='9') {
        return (int32_t)(c-'0');
    } else if('a'<=c && c<='f') {
        return (int32_t)(c-('a'-10));
    } else if('A'<=c && c<='F') {
        return (int32_t)(c-('A'-10));
    } else {
        return -1;
    }
}

/*
 * 0..ff - byte value
 * 0x100 - no byte (EUC)
 * -1 - c1 not a digit
 * -2 - c2 not a digit
 */
static int32_t
parseByte(char c1, char c2, UBool firstByte) {
    int32_t d1, d2;

    d1=parseDigit(c1);
    if(d1<0) {
        return -1;
    }
    d2=parseDigit(c2);
    if(d2<0) {
        if(firstByte && c2=='-' && d1<=3) {
            /* this is a special EUC format where the code set number prepends the bytes */
            switch(d1) {
            case 0:
            case 1:
                return 0x100;
            case 2:
                return 0x8e;
            case 3:
                return 0x8f;
            default:
                /* never occurs because of the above check */
                break;
            }
        }
        return -2;
    }
    return (d1<<4)|d2;
}

static void
parseMappings(FILE *f, UCMFile *ucm) {
    char line[200];
    char *s, *end;
    int32_t lineNum=0;
    int32_t startSkipLineNum=0, endSkipLineNum;
    UBool isOK;

    UCMapping m={ 0 };
    UChar32 codePoints[UCNV_EXT_MAX_UCHARS];
    uint8_t bytes[UCNV_EXT_MAX_BYTES];

    UChar32 cp;
    int32_t byte, charLength, u16Length;
    int8_t uLen, bLen;

    isOK=TRUE;

    while(fgets(line, sizeof(line), f)!=NULL) {
        s=(char *)u_skipWhitespace(line);
        lineNum++;

        /* skip empty lines or EOF characters */
        if(*s==0 || *s=='\n' || *s=='\r' || *s=='\x7F') {
            continue;
        }

        /* Skip useless mappings! */
        /* You'll see things like, "* only. They do not constitute part of the official UCS-2 to 1275 table."
                                or "* only. They do not constitute part of the official UCS2 table." */
        if(uprv_strstr(s, "* only. They do not constitute part of the official UCS")!=NULL) {
            startSkipLineNum = lineNum;
            /* Ignore the next few mappings. They have no value */
            while(fgets(line, sizeof(line), f)!=NULL) {
                s=(char *)u_skipWhitespace(line);
                lineNum++;
                if(uprv_strstr(s, "* The official table starts here:")!=NULL) {
                    break;  /* continue with outer loop */
                }
            }
            endSkipLineNum = lineNum-1;
            fprintf(stderr, "Warning: skipped lines %d-%d, since it doesn't seem to be real data\n", startSkipLineNum, endSkipLineNum);
        }

        /* explicit end of table */
        if(uprv_memcmp(s, "END CHARMAP", 11)==0) {
            break;
        }

        /* comment lines, parse substitution characters, otherwise skip them */
        if(*s=='#' || *s=='*') {
            /* get subchar1 */
            s=uprv_strstr(line, "for U+00xx");
            if(s!=NULL) {
                s=uprv_strstr(line, "x'");
                if(s!=NULL) {
                    s+=2;
                    subchar1=uprv_strtoul(s, &end, 16);
                    if(end!=s+2 || *end!='\'') {
                        fprintf(stderr, "error parsing subchar1 from \"%s\"\n", line);
                        exit(2);
                    }
                    continue;
                } else {
                    fprintf(stderr, "error finding subchar1 on \"%s\"\n", line);
                    exit(2);
                }
            }

            /* get subchar */
            s=uprv_strstr(line, "for U+xxxx");
            if(s!=NULL) {
                s=uprv_strstr(line, "x'");
                if(s!=NULL) {
                    s+=2;
                    subchar=uprv_strtoul(s, &end, 16);
                    if(end<s+2 || *end!='\'') {
                        fprintf(stderr, "error parsing subchar from \"%s\"\n", line);
                        exit(2);
                    }
                    continue;
                } else {
                    fprintf(stderr, "error finding subchar on \"%s\"\n", line);
                    exit(2);
                }
            }

            /* get modified date */
            s=uprv_strstr(line, "Modified");
            if(s!=NULL && uprv_strstr(s, ":") != NULL) {
                int len = uprv_strlen(s);
                while (!isdigit(s[len-1])) {
                    len--;
                }
                year=(uint16_t)uprv_strtoul(s+len-4, &end, 10);
                if(end!=s+len || year < MIN_YEAR || MAX_YEAR < year) {
                    fprintf(stderr, "error parsing year from \"%s\"; year is %d\n", line, year);
                    exit(2);
                }
                isYearModificationDate=1;
                continue;
            }

            s=uprv_strstr(line, "Updated      :");
            if(s!=NULL) {
                int len = uprv_strlen(s);
                while (s[len] != '(') {
                    len--;
                }
                while (!isdigit(s[len-1])) {
                    len--;
                }
                year=(uint16_t)uprv_strtoul(s+len-4, &end, 10);
                if(end!=s+len || year < MIN_YEAR || MAX_YEAR < year) {
                    fprintf(stderr, "error parsing year from \"%s\"; year is %d\n", line, year);
                    exit(2);
                }
                isYearModificationDate=1;
                continue;
            }

            /* get creation date */
            s=uprv_strstr(line, "Creation date:");
            if(s!=NULL && !isYearModificationDate) {
                int len = uprv_strlen(s);
                while (!isdigit(s[len-1])) {
                    len--;
                }
                year=(uint16_t)uprv_strtoul(s+len-4, &end, 10);
                if(end!=s+len || year < MIN_YEAR || MAX_YEAR < year) {
                    fprintf(stderr, "error parsing year from \"%s\"; year is %d\n", line, year);
                    exit(2);
                }
                continue;
            }

            continue;
        }

        /* parse a mapping */
        charLength=0;
        uLen=bLen=0;

        /* parse bytes */
        for(;;) {
            if(*s==' ' || *s=='\t' || *s=='+') {
                /* do some of the analysis while we know the character boundaries */
                if(minCharLength==0 || charLength<minCharLength) {
                    minCharLength=charLength;
                }
                if(maxCharLength==0 || charLength>maxCharLength) {
                    maxCharLength=charLength;
                }

                if(charLength==2) {
                    uint32_t twoByte;

                    twoByte=((uint32_t)bytes[bLen-2]<<8)|bytes[bLen-1];
                    if(twoByte<minTwoByte) {
                        minTwoByte=twoByte;
                    }
                    if(twoByte>maxTwoByte) {
                        maxTwoByte=twoByte;
                    }
                }

                /* skip an optional plus sign */
                if(bLen>0 && *s=='+') {
                    charLength=0; /* count codepage characters between plusses */
                    ++s;
                }
                if(*s==' ' || *s=='\t') {
                    break;
                }
            }

            byte=parseByte(s[0], s[1], (UBool)(bLen==0));
            if(byte<0) {
                fprintf(stderr, "%d: error parsing codepage bytes on \"%s\"\n", lineNum, line);
                isOK=FALSE;
                break;
            }
            if(byte>0xff) {
                /* special EUC prefix which does not result in a byte */
                s+=2;
                continue;
            }

            if(bLen==UCNV_EXT_MAX_BYTES) {
                fprintf(stderr, "%d: error: too many codepage bytes on \"%s\"\n", lineNum, line);
                isOK=FALSE;
                break;
            }

            bytes[bLen++]=(uint8_t)byte;
            oredBytes|=(uint8_t)byte;
            ++charLength;

            s+=2;
        }

        if(!isOK) {
            continue;
        }

        if(bLen==0) {
            fprintf(stderr, "%d: no codepage bytes on \"%s\"\n", lineNum, line);
            isOK=FALSE;
            continue;
        } else if(bLen<=4) {
            uprv_memcpy(m.b.bytes, bytes, bLen);
        }
        m.bLen=bLen;

        s=(char *)u_skipWhitespace(s);

        /* parse code points */
        for(;;) {
            /* skip a plus sign between codepage characters */
            if(uLen>0 && *s=='+') {
                ++s;
            }
            if(*s==0 || *s==' ' || *s=='\t' || *s=='\n' || *s=='\r') {
                break;
            }

            cp=(UChar32)uprv_strtoul(s, &end, 16);
            if(end==s) {
                if(uprv_strncmp(s, "????", 4)==0 || uprv_strstr(s, "UNASSIGNED")!=NULL) {
                    /* this is a non-entry, do not add it to the mapping table */
                    goto continueOuterLoop;
                }
                fprintf(stderr, "%d: error parsing Unicode code point on \"%s\"\n", lineNum, line);
                isOK=FALSE;
                break;
            }
            if((uint32_t)cp>0x10ffff || U_IS_SURROGATE(cp)) {
                fprintf(stderr, "%d: error: Unicode code point must be 0..d7ff or e000..10ffff - \"%s\"\n", lineNum, line);
                isOK=FALSE;
                break;
            }

            if(uLen==UCNV_EXT_MAX_UCHARS) {
                fprintf(stderr, "%d: error: too many Unicode code points on \"%s\"\n", lineNum, line);
                isOK=FALSE;
                break;
            }
            codePoints[uLen++]=cp;
            s=end+1;
        }

        if(!isOK) {
            continue;
        }

        if(uLen==0) {
            fprintf(stderr, "%d: no Unicode code points on \"%s\"\n", lineNum, line);
            isOK=FALSE;
            continue;
        } else if(uLen==1) {
            m.u=codePoints[0];
        } else {
            UErrorCode errorCode=U_ZERO_ERROR;
            u_strFromUTF32(NULL, 0, &u16Length, codePoints, uLen, &errorCode);
            if( (U_FAILURE(errorCode) && errorCode!=U_BUFFER_OVERFLOW_ERROR) ||
                u16Length>UCNV_EXT_MAX_UCHARS
            ) {
                fprintf(stderr, "%d: too many UChars on \"%s\"\n", lineNum, line);
                isOK=FALSE;
                continue;
            }
        }
        m.uLen=uLen;

        ucm_addMapping(ucm->base, &m, codePoints, bytes);
continueOuterLoop:
        ;
    }

    if (endSkipLineNum >= lineNum - 20) {
        /* Usually there are at least a few mappings. Let's say that 20 is the minimum */
        fprintf(stderr, "Internal Error: Skipped too many lines\n");
        isOK = FALSE;
    }

    if(!isOK) {
        exit(2);
    }
}

/* merge the mappings into fromUFile and set real precision flags */
static void
mergeMappings() {
    uint8_t subBytes[4];
    int32_t subcharLength;

    if(subchar>0xffffff) {
        subBytes[0]=(uint8_t)(subchar>>24);
        subBytes[1]=(uint8_t)(subchar>>16);
        subBytes[2]=(uint8_t)(subchar>>8);
        subBytes[3]=(uint8_t)subchar;
        subcharLength=4;
    } else if(subchar>0xffff) {
        subBytes[0]=(uint8_t)(subchar>>16);
        subBytes[1]=(uint8_t)(subchar>>8);
        subBytes[2]=(uint8_t)subchar;
        subcharLength=3;
    } else if(subchar>0xff) {
        subBytes[0]=(uint8_t)(subchar>>8);
        subBytes[1]=(uint8_t)subchar;
        subcharLength=2;
    } else {
        subBytes[0]=(uint8_t)subchar;
        subcharLength=1;
    }

    ucm_mergeTables(
        fromUFile->base, toUFile->base,
        subBytes, subcharLength,
        (uint8_t)subchar1);
}

static void
analyzeTable() {
    UCMTable *table;
    UCMapping *m, *mLimit;
    UChar32 *codePoints;
    uint8_t *bytes;

    UChar32 u;
    int32_t i, countASCII=0;
    uint8_t b;

    table=fromUFile->base;
    m=table->mappings;
    mLimit=m+table->mappingsLength;

    for(; m<mLimit; ++m) {
        codePoints=UCM_GET_CODE_POINTS(table, m);
        bytes=UCM_GET_BYTES(table, m);

        /* PUA used? */
        for(i=0; i<m->uLen; ++i) {
            u=codePoints[i];
            if((uint32_t)(u-0xe000)<0x1900 || (uint32_t)(u-0xf0000)<0x20000) {
                usesPUA=1;
            }
        }

        /* only consider roundtrip mappings for the rest */
        if(m->f!=0) {
            continue;
        }

        if(m->uLen==1) {
            u=*codePoints;
            b=*bytes;

            if(m->bLen==1) {
                /* ASCII or EBCDIC? */
                if(u==0x41) {
                    if(b==0x41) {
                        charsetFamily=U_ASCII_FAMILY;
                    } else if(b==0xc1) {
                        charsetFamily=U_EBCDIC_FAMILY;
                    }
                } else if(u==0xa) {
                    if(b==0xa) {
                        charsetFamily=U_ASCII_FAMILY;
                    } else if(b==0x25) {
                        charsetFamily=U_EBCDIC_FAMILY;
                        variantLF=0;
                    } else if(b==0x15) {
                        charsetFamily=U_EBCDIC_FAMILY;
                        variantLF=1;
                    }
                }
            }

            /* US-ASCII? */
            if((uint32_t)(u-0x21)<94) {
                if(m->bLen==1 && u==b) {
                    ++countASCII;
                } else {
                    variantASCII=1;
                }
            } else if(u<0x20 || u==0x7f) {
                /* non-ISO C0 controls? */
                if(u!=b) {
                    /* IBM PC rotation of SUB and other controls: 0x1a->0x7f->0x1c->0x1a */
                    if(u==0x1a && b==0x7f || u==0x1c && b==0x1a || u==0x7f && b==0x1c) {
                        charsetFamily=U_ASCII_FAMILY;
                        variantSUB=1;
                    } else {
                        variantControls=1;
                    }
                }
            }
        }
    }

    is7Bit= oredBytes<=0x7f;

    if(charsetFamily==U_UNKNOWN_CHARSET_FAMILY) {
        if(minCharLength==2 && maxCharLength==2) {
            /* guess the charset family for DBCS according to typical byte distributions */
            if( ((0x2020<=minTwoByte || minTwoByte<=0x217e) && maxTwoByte<=0x7e7e) ||
                ((0xa0a0<=minTwoByte || minTwoByte<=0xa1fe) && maxTwoByte<=0xfefe) ||
                ((0x8140<=minTwoByte || minTwoByte<=0x81fe) && maxTwoByte<=0xfefe)
            ) {
                charsetFamily=U_ASCII_FAMILY;
            } else if((minTwoByte==0x4040 || (0x4141<=minTwoByte && minTwoByte<=0x41fe)) && maxTwoByte<=0xfefe) {
                charsetFamily=U_EBCDIC_FAMILY;
            }
        }
        if(minCharLength==4 && maxCharLength==4) {
            /* guess the charset family for QBCS according to typical byte distributions */
            if (ccsid == 5487) {
                /* Special partial gb18030 table */
                charsetFamily=U_ASCII_FAMILY;
            }
        }
        if(charsetFamily==U_UNKNOWN_CHARSET_FAMILY) {
            fprintf(stderr, "error: unable to determine the charset family\n");
            exit(3);
        }
    }

    /* reset variant indicators if they do not apply */
    if(charsetFamily!=U_ASCII_FAMILY || minCharLength!=1) {
        variantASCII=variantSUB=variantControls=0;
    } else if(countASCII!=94) {
        /* if there are not 94 mappings for ASCII graphic characters, then set variantASCII */
        variantASCII=1;
    }

    if(charsetFamily!=U_EBCDIC_FAMILY || minCharLength!=1) {
        variantLF=0;
    }
    if(ccsid==25546) {
        /* Special case. It's not EBCDIC, but it is stateful like EBCDIC_STATEFUL. */
        is_0xe_0xf_Stateful = 1;
    }
}

static int
getSubchar(const char *name) {
    int i;

    for(i=0; i<sizeof(knownSubchars)/sizeof(knownSubchars[0]); ++i) {
        if(uprv_strcmp(name, knownSubchars[i].name)==0) {
            subchar=knownSubchars[i].subchar;
            subchar1=knownSubchars[i].subchar1;
            return 1;
        }
    }

    return 0;
}

static void
getSubcharFromUPMAP(FILE *f) {
    char line[200];
    char *s, *end;
    uint32_t *p;
    uint32_t value, bytes;

    while(fgets(line, sizeof(line), f)!=NULL && uprv_memcmp(line, "CHARMAP", 7)!=0) {
        s=(char *)u_skipWhitespace(line);

        /* skip empty lines */
        if(*s==0 || *s=='\n' || *s=='\r') {
            continue;
        }

        /* look for variations of subchar entries */
        if(uprv_memcmp(s, "<subchar>", 9)==0) {
            s=(char *)u_skipWhitespace(s+9);
            p=&subchar;
        } else if(uprv_memcmp(s, "<subchar1>", 10)==0) {
            s=(char *)u_skipWhitespace(s+10);
            p=&subchar1;
        } else if(uprv_memcmp(s, "#<subchar1>", 11)==0) {
            s=(char *)u_skipWhitespace(s+11);
            p=&subchar1;
        } else {
            continue;
        }

        /* get the value and store it in *p */
        bytes=0;
        while(s[0]=='\\' && s[1]=='x') {
            value=uprv_strtoul(s+2, &end, 16);
            s+=4;
            if(end!=s) {
                fprintf(stderr, "error parsing UPMAP subchar from \"%s\"\n", line);
                exit(2);
            }
            bytes=(bytes<<8)|value;
        }
        *p=bytes;
    }
}

static const char *
getStateTable() {
    int32_t i;

    for(i=0; i<LENGTHOF(knownStateTables); ++i) {
        if(ccsid==knownStateTables[i].ccsid) {
            return knownStateTables[i].table;
        }
    }

    return NULL;
}

static void
writeBytes(char *s, uint32_t b) {
    if(b<=0xff) {
        sprintf(s, "\\x%02lX", b);
    } else if(b<=0xffff) {
        sprintf(s, "\\x%02lX\\x%02lX", b>>8, b&0xff);
    } else if(b<=0xffffff) {
        sprintf(s, "\\x%02lX\\x%02lX\\x%02lX", b>>16, (b>>8)&0xff, b&0xff);
    } else {
        sprintf(s, "\\x%02lX\\x%02lX\\x%02lX\\x%02lX", b>>24, (b>>16)&0xff, (b>>8)&0xff, b&0xff);
    }
}

static void
writeUCM(FILE *f, const char *ucmname, const char *rpname, const char *tpname) {
    char buffer[200];
    char *key, *value;
    const char *s, *end, *next;

    UCMStates *states;

    states=&fromUFile->states;

    /* write the header */
    fprintf(f,
        "# ***************************************************************************\n"
        "# *\n"
        "# *   Copyright (C) 1995-2005, International Business Machines\n"
        "# *   Corporation and others.  All Rights Reserved.\n"
        "# *\n"
        "# ***************************************************************************\n"
        "#\n"
        "# File created by rptp2ucm (compiled on %s)\n"
        "# from source files %s and %s\n"
        "#\n", __DATE__, rpname, tpname);

    /* ucmname does not have a path or .ucm */
    fprintf(f, "<code_set_name>               \"%s\"\n", ucmname);

    fputs("<char_name_mask>              \"AXXXX\"\n", f);
    fprintf(f, "<mb_cur_max>                  %u\n", maxCharLength);
    fprintf(f, "<mb_cur_min>                  %u\n", minCharLength);

    states->maxCharLength=maxCharLength;
    states->minCharLength=minCharLength;

    states->conversionType=UCNV_MBCS;
    states->outputType=maxCharLength-1;

    if(maxCharLength==1) {
        fputs("<uconv_class>                 \"SBCS\"\n", f);
        states->conversionType=UCNV_SBCS;
    } else if(maxCharLength==2) {
        if(minCharLength==1) {
            if(charsetFamily==U_EBCDIC_FAMILY) {
                fputs("<uconv_class>                 \"EBCDIC_STATEFUL\"\n", f);
                is_0xe_0xf_Stateful = 1;
                states->conversionType=UCNV_EBCDIC_STATEFUL;
                states->outputType=MBCS_OUTPUT_2_SISO;
            } else {
                fputs("<uconv_class>                 \"MBCS\"\n", f);
            }
        } else if(minCharLength==2) {
            fputs("<uconv_class>                 \"DBCS\"\n", f);
            states->conversionType=UCNV_DBCS;
        } else {
            fputs("<uconv_class>                 \"MBCS\"\n", f);
        }
    } else {
        fputs("<uconv_class>                 \"MBCS\"\n", f);
    }

    if(subchar!=0) {
        writeBytes(buffer, subchar);
        fprintf(f, "<subchar>                     %s\n", buffer);
    }

    if(subchar1!=0) {
        if (minCharLength>1) {
            fprintf(stderr, "warning: <subchar1> \\x%02X is ignored for charsets without an SBCS portion.\n", subchar1);
        }
        else {
            fprintf(f, "<subchar1>                    \\x%02X\n", subchar1);
        }
    }

    /* write charset family */
    if(charsetFamily==U_ASCII_FAMILY) {
        fputs("<icu:charsetFamily>           \"ASCII\"\n", f);
    } else {
        fputs("<icu:charsetFamily>           \"EBCDIC\"\n", f);
    }

    /* write alias describing the codepage */
    sprintf(buffer, "<icu:alias>                   \"ibm-%u", ccsid);
    if(!usesPUA && !variantLF && !variantASCII && !variantControls && !variantSUB) {
        uprv_strcat(buffer, "_STD\"\n\n");
    } else {
        /* add variant indicators in alphabetic order */
        if(variantASCII) {
            uprv_strcat(buffer, "_VASCII");
        }
        if(variantControls) {
            uprv_strcat(buffer, "_VGCTRL");
        }
        if(variantLF) {
            uprv_strcat(buffer, "_VLF");
        }
        if(variantSUB) {
            uprv_strcat(buffer, "_VSUB");
        }
        if(usesPUA) {
            uprv_strcat(buffer, "_VPUA");
        }
        uprv_strcat(buffer, "\"\n\n");
    }
    fputs(buffer, f);

    /* write the state table - <icu:state> */
    s=getStateTable();
    if(s==NULL && is7Bit) {
        s="<icu:state>                   0-7f\n";
    }
    if(s!=NULL) {
        fputs(s, f);
        fputs("\n", f);

        /* set the state table */
        while(*s!=0) {
            /* separate the state table string into lines */
            end=uprv_strchr(s, '\n');
            if(end!=NULL) {
                next=end+1;
            } else {
                end=uprv_strchr(s, 0);
                next=end;
            }

            uprv_memcpy(buffer, s, end-s);
            buffer[end-s]=0;
            ucm_parseHeaderLine(fromUFile, buffer, &key, &value);
            s=next;
        }
    }

    ucm_processStates(states);

    /* separate extension mappings out of base table, and other checks */
    if(!ucm_separateMappings(fromUFile, is_0xe_0xf_Stateful)) {
        fprintf(stderr, "error: ucm_separateMappings() failed\n");
        exit(U_INVALID_FORMAT_ERROR);
    }

    /* merge the base and extension tables again to be friendlier to other tools */
    if(fromUFile->ext->mappingsLength>0) {
        UCMTable *base, *ext;
        UCMapping *m, *mLimit;

        base=fromUFile->base;
        ext=fromUFile->ext;
        m=ext->mappings;
        mLimit=m+ext->mappingsLength;
        while(m<mLimit) {
            ucm_addMapping(base, m, UCM_GET_CODE_POINTS(ext, m), UCM_GET_BYTES(ext, m));
            ++m;
        }

        ucm_sortTable(base);
        ext->mappingsLength=0;
    }

    /* write the mappings */
    fputs("CHARMAP\n", f);
    ucm_printTable(fromUFile->base, f, TRUE);
    fputs("END CHARMAP\n", f);

    if(fromUFile->ext->mappingsLength>0) {
        fputs("\nCHARMAP\n", f);
        ucm_printTable(fromUFile->ext, f, TRUE);
        fputs("END CHARMAP\n", f);
    }
}

static void
processTable(const char *arg) {
    char filename[1024], tpname[32];
    const char *basename, *s;
    FILE *rpmap, *tpmap, *ucm;
    uint32_t value, unicode;
    int length;

    init();

    /* separate path and basename */
    basename=uprv_strrchr(arg, '/');
    if(basename==NULL) {
        basename=uprv_strrchr(arg, '\\');
        if(basename==NULL) {
            basename=arg;
        } else {
            ++basename;
        }
    } else {
        ++basename;
        s=uprv_strrchr(arg, '\\');
        if(s!=NULL && ++s>basename) {
            basename=s;
        }
    }

    /* is this a standard RPMAP filename? */
    value=uprv_strtoul(basename, (char **)&s, 16);
    if( uprv_strlen(basename)!=17 ||
        (uprv_memcmp(basename+9, "RPMAP", 5)!=0 && uprv_memcmp(basename+9, "rpmap", 5)!=0 &&
         uprv_memcmp(basename+9, "RXMAP", 5)!=0 && uprv_memcmp(basename+9, "rxmap", 5)!=0) ||
        (s-basename)!=8 ||
        *s!='.'
    ) {
        fprintf(stderr, "error: \"%s\" is not a standard RPMAP filename\n", basename);
        exit(1);
    }

    /* is this really a Unicode conversion table? - get the CCSID */
    unicode=value&0xffff;
    if(unicode==13488 || unicode==17584) {
        ccsid=(uint16_t)(value>>16);
    } else {
        unicode=value>>16;
        if(unicode==13488 || unicode==17584 || unicode==1200 || unicode==61956 || unicode==21680) {
            ccsid=(uint16_t)(value&0xffff);
        } else {
            fprintf(stderr, "error: \"%s\" is not a Unicode conversion table\n", basename);
            exit(1);
        }
    }

    /* try to open the RPMAP file */
    rpmap=fopen(arg, "r");
    if(rpmap==NULL) {
        fprintf(stderr, "error: unable to open \"%s\"\n", arg);
        exit(1);
    }

    /* try to open the TPMAP file */
    uprv_strcpy(filename, arg);
    length=uprv_strlen(filename);

    /* guess the TPMAP filename; note that above we have checked the format of the basename */
    /* replace the R in RPMAP by T, keep upper- or lowercase */
    if(filename[length-8]=='R') {
        filename[length-8]='T';
    } else {
        filename[length-8]='t';
    }

    /* reverse the CCSIDs */
    uprv_memcpy(filename+length-17, basename+4, 4);
    uprv_memcpy(filename+length-13, basename, 4);

    /* first, keep the same suffix */
    tpmap=fopen(filename, "r");
    if(tpmap==NULL) {
        /* next, try reducing the second to last digit by 1 */
        --filename[length-2];
        tpmap=fopen(filename, "r");
        if(tpmap==NULL) {
            /* there is no TPMAP */
            fprintf(stderr, "error: unable to find the TPMAP file for \"%s\"\n", arg);
            exit(1);
        }
    }
    puts(filename);
    uprv_strcpy(tpname, filename+length-17);

    /* parse both files */
    parseMappings(rpmap, fromUFile);
    parseMappings(tpmap, toUFile);
    fclose(tpmap);
    fclose(rpmap);

    /* if there is no subchar, then try to get it from the corresponding UPMAP */
    if(subchar==0) {
        FILE *f;

        /* restore the RPMAP filename and just replace the R by U */
        uprv_strcpy(filename+length-17, basename);
        if(filename[length-8]=='R') {
            filename[length-8]='U';
        } else {
            filename[length-8]='u';
        }

        f=fopen(filename, "r");
        if(f==NULL) {
            /* try reversing the CCSIDs */
            uprv_memcpy(filename+length-17, basename+4, 4);
            uprv_memcpy(filename+length-13, basename, 4);
            f=fopen(filename, "r");
        }
        if(f!=NULL) {
            getSubcharFromUPMAP(f);
            fclose(f);
        }
    }

    /* generate the .ucm filename - necessary before getSubchar() */
    length=sprintf(filename, "ibm-%u_", ccsid);

    /* uppercase and append the suffix */
    filename[length++]=toupper(basename[10]);  /* P or X */
    filename[length++]=toupper(basename[14]);  /* last 3 suffix characters */
    filename[length++]=toupper(basename[15]);
    filename[length++]=toupper(basename[16]);
    filename[length]=0;
    /* find the subchar if still necessary - necessary before merging for correct |2 */
    if(subchar==0 && !getSubchar(filename+4)) {
        fprintf(stderr, "warning: missing subchar in \"%s\" (CCSID=0x%04X)\n", filename, ccsid);
    }
    /*concatenate year*/
    if (year <= 0) {
        fprintf(stderr, "warning: missing creation/modification date in \"%s\" (CCSID=0x%04X)\n", filename, ccsid);
        year=2002;
    }
    sprintf(filename+length, "-%d", year);

    /* merge the mappings */
    mergeMappings();

    /* analyze the conversion table */
    analyzeTable();

    /* open the .ucm file */
    uprv_strcat(filename, ".ucm");
    ucm=fopen(filename, "w");
    if(ucm==NULL) {
        fprintf(stderr, "error: unable to open output file \"%s\"\n", filename);
        exit(4);
    }

    /* remove the .ucm from the filename for the following processing */
    filename[uprv_strlen(filename)-4]=0;

    /* write the .ucm file */
    writeUCM(ucm, filename, basename, tpname);
    fclose(ucm);
}

extern int
main(int argc, const char *argv[]) {
    if(argc<2) {
        fprintf(stderr,
                "usage: %s { rpmap/rxmap-filename }+\n",
                argv[0]);
        exit(1);
    }

    while(--argc>0) {
        puts(*++argv);
        processTable(*argv);
    }

    return 0;
}
