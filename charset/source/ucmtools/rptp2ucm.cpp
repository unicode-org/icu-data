// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*
*   Copyright (C) 2000-2009, International Business Machines
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
*      -I..\..\..\..\icu\source\common
*      -I..\..\..\..\icu\source\tools\toolutil
*      rptp2ucm.c -link /LIBPATH:..\..\..\..\icu\lib icuuc.lib icutu.lib
*/

#include "unicode/utypes.h"
#include "unicode/ustring.h"
#include "rptp_map.h"
#include "cmemory.h"
#include "cstring.h"
#include "ucnv_ext.h"
#include "ucm.h"
#include "uparse.h"
#include "uoptions.h"

#include <stdio.h>
#include <time.h>

#define LENGTHOF(array) (int32_t)(sizeof(array)/sizeof((array)[0]))

typedef const char *TMAParray[4];

typedef struct RMAPtoTMAP {
    const uint16_t ccsid;
    const char *RMAP;
    const TMAParray TMAP;
} RMAPtoTMAP;

/* This table is here because the .package files are not consistently machine parseable. */
/* Also not all package files exist for all combinations. */
/* TODO: I wish there was a less manual process to get the mapping table information. */
static const RMAPtoTMAP
knownRMAPtoTMAP[] = {
    {0x0112, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x011E, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x01A4, "RXMAP110", {"TXMAP110", NULL, NULL, NULL}},
    {0x01A4, "RXMAP120", {"TXMAP110", NULL, NULL, NULL}},
    {0x01A9, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x0360, "RXMAP110", {"TXMAP110", NULL, NULL, NULL}},
    {0x0391, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x039E, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x03A2, "RPMAP120", {"TPMAP110", "TPMAP12A", NULL, NULL}},
    {0x03A2, "RXMAP120", {"TXMAP110", NULL, NULL, NULL}},
    {0x03A3, "RPMAP120", {"TPMAP110", NULL, NULL, NULL}},
    {0x03A3, "RXMAP120", {"TXMAP110", NULL, NULL, NULL}},
    {0x03A4, "RPMAP120", {"TPMAP110", NULL, NULL, NULL}},
    {0x03A4, "RPMAP12A", {"TPMAP11A", "TPMAP12A", NULL, NULL}},
    {0x03A5, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x03A5, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x03A7, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x03A7, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x03A9, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x03A9, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x03AB, "RPMAP120", {"TPMAP110", "TPMAP12A", NULL, NULL}},
    {0x03AB, "RXMAP120", {"TXMAP110", NULL, NULL, NULL}},
    {0x03AD, "RPMAP130", {"TPMAP120", NULL, NULL, NULL}},
    {0x03AD, "RPMAP13A", {"TPMAP12A", NULL, NULL, NULL}},
    {0x03AE, "RPMAP120", {"TPMAP110", NULL, NULL, NULL}},
    {0x03AE, "RPMAP12A", {"TPMAP11A", "TPMAP12A", NULL, NULL}},
    {0x03AF, "RPMAP130", {"TPMAP120", NULL, NULL, NULL}},
    {0x03AF, "RPMAP14A", {"TPMAP13A", NULL, NULL, NULL}},
    {0x03AF, "RPMAP15A", {"TPMAP14A", NULL, NULL, NULL}},
    {0x03B4, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x03B4, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x03B5, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x03B5, "RPMAP11A", {"TPMAP10A", NULL, NULL, NULL}},
    {0x03B5, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x03B6, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x03B6, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x03B9, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x03BA, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x03C0, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x03C4, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x03C4, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x03CA, "RPMAP110", {"TPMAP100", "TPMAP110", NULL, NULL}},
    {0x03FC, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x03FD, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x03FF, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x044C, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x044D, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x044E, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x044F, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0450, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0451, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0452, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0453, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0471, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0471, "RPMAPMOD", {"TPMAP100", NULL, NULL, NULL}},
    {0x048B, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x048D, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x048E, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x048F, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0490, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0561, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x0565, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x0565, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x0567, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x0567, "RXMAP110", {"TXMAP100", NULL, NULL, NULL}},
    {0x056A, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x056A, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x1328, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x1345, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x1350, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x1351, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x135A, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x135B, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x135C, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x135D, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x135E, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x135F, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x1361, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x1362, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x1363, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x13A2, "RPMAP120", {"TPMAP110", NULL, NULL, NULL}},
    {0x13A2, "RXMAP120", {"TXMAP110", NULL, NULL, NULL}},
    {0x13AB, "RPMAP120", {"TPMAP110", "TPMAP12A", NULL, NULL}},// package is missing. Is this correct?
    {0x13AB, "RXMAP120", {"TXMAP110", NULL, NULL, NULL}},// package is missing. Is this correct?
    {0x13BA, "RPMAP120", {"TPMAP110", NULL, NULL, NULL}},// package text is garbled. Is this correct?
    {0x13BA, "RPMAP12A", {"TPMAP11A", NULL, NULL, NULL}},// package text is garbled. Is this correct?
    {0x155F, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x1561, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x21A4, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x21A4, "RXMAP110", {"TXMAP110", NULL, NULL, NULL}},
    {0x2352, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x2368, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x245A, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x256C, "RPMAP110", {"TPMAP100", NULL, NULL, NULL}},
    {0x3344, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x3344, "RPMAP10A", {"TPMAP100", NULL, NULL, NULL}},
    {0x3345, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x3354, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x3357, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x3359, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x3364, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x3365, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x336A, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x4345, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x4358, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x5360, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0x8122, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}},
    {0x83BA, "RPMAP120", {"TPMAP110", NULL, NULL, NULL}},// package is missing. Is this correct?
    {0x83BA, "RPMAP12A", {"TPMAP11A", "TPMAP12A", NULL, NULL}},// package is missing. Is this correct?
    {0xD1B5, "RPMAP101", {"TPMAP101", NULL, NULL, NULL}},
    {0xD3AF, "RPMAP100", {"TPMAP100", NULL, NULL, NULL}}
};

typedef struct UCMSubchar {
    const uint16_t ccsid;
    uint32_t subchar, subchar1;
} UCMSubchar;

/* This is here because the U?MAP??? file goes by a significantly different name from the R?MAP??? file. */
static const UCMSubchar
knownSubchars[]={
    274, 0x3f, 0,
    913, 0x1a, 0,
    1047, 0x3f, 0,
    1114, 0x1A, 0,
    1137, 0x3F, 0,
    1166, 0x3F, 0,
    1167, 0x1A, 0,
    1168, 0x1A, 0,
    8612, 0x3f, 0,
    9444, 0x1A, 0,
    9447, 0x1A, 0,
    9449, 0x1A, 0
};

typedef struct CCSIDStateTable {
    uint16_t ccsid;
    uint16_t unicode; /* 0 means any unicode version. */
    const char *table;
} CCSIDStateTable;

#define Big5DBCSStates \
    "<icu:state>                   81-fe:1\n" \
    "<icu:state>                   40-7e, 80-fe\n"

#define Big5MBCSStates \
    "<icu:state>                   0-7f, 81-fe:1\n" \
    "<icu:state>                   40-7e, 80-fe\n"

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

    301,0,"<icu:state>                   0-80:2, 81-9f:1, a0-df:2, e0-fc:1, fd-ff:2\n"
          "<icu:state>                   40-7e, 80-fc\n"
          "<icu:state>\n",

    367,0,"<icu:state>                   0-7f\n",

    927,0,japanesePCDBCSStates,

    926,0,japanesePCDBCSStates,

    928,0,japanesePCDBCSStates,

    932,0,"<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"
          "<icu:state>                   40-7e, 80-fc\n",

    941,0,japanesePCDBCSStates,

    942,0, "<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"
           "<icu:state>                   40-7e, 80-fc\n",

    943,0, "<icu:state>                   0-7f, 81-9f:1, a0-df, e0-fc:1\n"
           "<icu:state>                   40-7e, 80-fc\n",

    944,0, "<icu:state>                   0-80, 81-bf:1, c0-ff\n"
           "<icu:state>                   40-7e, 80-fe\n",

    946,0, "<icu:state>                   0-80, 81-fb:1,fc:2,fd-ff\n"
           "<icu:state>                   40-7e, 80-fe\n"
           "<icu:state>                   80-fe.u,fc",

    947,0, Big5DBCSStates,

    948,0, "<icu:state>                   0-80, 81-fb:1,fc:2,fd-fe\n"
           "<icu:state>                   40-7e, 80-fe\n"
           "<icu:state>                   80-fe.u,fc\n",

    949,0, "<icu:state>                   0-84, 8f-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    950,0, Big5MBCSStates,

    954,0, "<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-e4\n"
           "<icu:state>                   a1-fe:1, a1:4\n"
           "<icu:state>                   a1-fe.u\n",

    955,0, "<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    963,0, "<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    964,0, "# The fourth <icu:state> line is commented out (and does not count)\n"
           "# because the state table is hand-optimized and does not use what would be\n"
           "# the natural path for the encoding scheme.\n"
           "# The third <icu:state> used to start with \"a1-b0:3\" but overrode every one\n"
           "# of these byte values with a different state transition.\n"
           "\n"
           "# 0: Initial state, single bytes and lead bytes\n"
           "<icu:state>                   0-8d, 8e:2, 90-9f, a1-fe:1, aa-c1:4, c3:4, fe:4\n"
           "# 1: Trail byte state with mappings\n"
           "<icu:state>                   a1-fe\n"
           "# 2: Second of four bytes, follows lead byte 8e\n"
           "<icu:state>                   a1:3, a2:7, a3-ab:3, ac:6, ad:5, ae-b0:3\n"
           "# (unreachable/optimized away)\n"
           "# <icu:state>                   a1-fe:1\n"
           "# 3: Third of four bytes, 8e xx .. .. for most xx in a1-b0; all-unassigned\n"
           "<icu:state>                   a1-fe:4\n"
           "# 4: All-unassigned trail byte state\n"
           "<icu:state>                   a1-fe.u\n"
           "# 5: 8e ad .. .. with some mappings\n"
           "<icu:state>                   a1-a4:1, a5-fe:4\n"
           "# 6: 8e ac .. .. with some mappings\n"
           "<icu:state>                   a1-e2:1, e3-fe:4\n"
           "# 7: 8e a2 .. .. with some mappings\n"
           "<icu:state>                   a1-f2:1, f3-fe:4\n",

    970,0, "<icu:state>                   0-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n",

    1363,0,"<icu:state>                   0-7f, 81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    1350,0,"<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-e4\n"
           "<icu:state>                   a1-fe:1, a1:4, a3-a5:4, a8:4, ac-af:4, ee-f2:4\n"
           "<icu:state>                   a1-fe.u\n",

    1351,0,"<icu:state>                   0-ff:2, 81-9f:1, e0-fc:1\n"
           "<icu:state>                   40-7e, 80-fc\n"
           "<icu:state>\n",

    1370,0,"<icu:state>                   0-80, 81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    1373,0,Big5MBCSStates,

    1374,0,Big5DBCSStates,

    /* 1232 says UTF-32, but it's really post Unicode 4.0 */
    1375,1232,  "<icu:state>                   0-7f, 81-fe:1, 87-a0:2, c8:2, fa-fe:2\n"
                "<icu:state>                   40-7e, a1-fe\n"
                "<icu:state>                   40-7e.p, a1-fe.p\n",

    1375,0,Big5MBCSStates,

    /* 1232 says UTF-32, but it's really post Unicode 4.0 */
    1377,1232,  "# includes mappings for surrogate pairs\n"
                "<icu:state>                   0-ff, e:1.s, f:0.s\n"
                "<icu:state>                   initial, 0-3f:4, e:1.s, f:0.s, 40:3, 41-fe:2, ff:4, 4b:5, e0:5, c2-d6:5, db-df:5\n"
                "<icu:state>                   0-40:1.i, 41-fe:1., ff:1.i\n"
                "<icu:state>                   0-ff:1.i, 40:1.\n"
                "<icu:state>                   0-ff:1.i\n"
                "<icu:state>                   0-40:1.i, 41-fe:1.p, ff:1.i\n",

    1381,0,"<icu:state>                   0-84, 8c-fe:1\n"
           "<icu:state>                   a1-fe\n",

    1383,0,"<icu:state>                   0-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n",

    1385,0,"<icu:state>                   81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    1386,0,"<icu:state>                   0-80, 81-fe:1\n" /* Was 0-7f, 81-fe:1 */
           "<icu:state>                   40-7e, 80-fe\n",

    1390,0,states1390,

    1399,0,states1390,

    5039,0,"<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"
           "<icu:state>                   40-7e, 80-fc\n",

    5050,0,"<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
           "<icu:state>                   a1-fe\n"
           "<icu:state>                   a1-e4\n"
           "<icu:state>                   a1-fe:1, a1:4, a3-af:4, b6:4, d6:4, da-db:4, ed-f2:4\n"
           "<icu:state>                   a1-fe.u\n",

    5067,0,"<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    5470,0,Big5DBCSStates,

    /* 1232 says UTF-32, but it's really post Unicode 4.0 */
    5471,1232,  "<icu:state>                   0-7f, 81-fe:1, 88-a0:2, c8:2, fa-fe:2\n"
                "<icu:state>                   40-7e, a1-fe\n"
                "<icu:state>                   40-7e.p, a1-fe.p\n",

    5471,0,Big5MBCSStates,

    5475,0,Big5MBCSStates,

    5478,0,"<icu:state>                   0-20:2, 21-7e:1, 7f-ff:2\n"
           "<icu:state>                   21-7e\n"
           "<icu:state>\n",

    5487,0,"<icu:state>                   81-fe:1\n"
           "<icu:state>                   30-39:2\n"
           "<icu:state>                   81-fe:3\n"
           "<icu:state>                   30-39\n",

    5488,0,"<icu:state> 0-7f, 81:7, 82:8, 83:9, 84:a, 85-fe:4\n"    /* Modified form of ICU's gb18030 */
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

    9577,0,"<icu:state>                   81-fe:1\n"
           "<icu:state>                   40-7e, 80-fe\n",

    16684,0,states16684,

    21427,0,"<icu:state>                   0-80:2, 81-fe:1, ff:2\n"
            "<icu:state>                   40-7e, 80-fe\n"
            "<icu:state>\n",

    25546,0,"<icu:state>                   0-7f, e:1.s, f:0.s\n"
            "<icu:state>                   initial, 0-20:3, e:1.s, f:0.s, 21-7e:2, 7f-ff:3\n"
            "<icu:state>                   0-20:1.i, 21-7e:1., 7f-ff:1.i\n"
            "<icu:state>                   0-ff:1.i\n",

    33722,0,"<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
            "<icu:state>                   a1-fe\n"
            "<icu:state>                   a1-e4\n"
            "<icu:state>                   a1-fe:1, a1:4, a3-af:4, b6:4, d6:4, da-db:4, ed-f2:4\n"
            "<icu:state>                   a1-fe.u\n",

    54191,0,"<icu:state>                   0-80, 81-9f:1, a0-df, e0-fc:1, fd-ff\n"
            "<icu:state>                   40-7e, 80-fc\n",

    62383,0,"<icu:state>                   0-7f, 81-9f:1, a0-df, e0-fc:1\n" // Same as CCSID 943
            "<icu:state>                   40-7e, 80-fc\n"

};

#define MAX_YEAR 2900
#define MIN_YEAR 1940

static FilenameMappingHistory* filenameHistory;

static UCMFile *fromUFile, *toUFile;

static uint32_t subchar, subchar1;
static uint16_t ccsid, unicodeCCSID;

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
    is_0xe_0xf_Stateful;

static void
init() {
    fromUFile=ucm_open();
    toUFile=ucm_open();

    subchar=subchar1=0;
    ccsid=0;
    unicodeCCSID=0;
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
}

static void
cleanup() {
    ucm_close(fromUFile);
    ucm_close(toUFile);
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

static uint16_t
parseYear(const char *yearToParse, const char *line) {
    char *end;
    uint16_t localYear=(uint16_t)uprv_strtoul(yearToParse, &end, 10);
    if(end!=yearToParse+4 || localYear < MIN_YEAR || MAX_YEAR < localYear) {
        fprintf(stderr, "error parsing year from \"%s\"; year is %d\n", line, localYear);
        exit(2);
    }
    if (localYear > year) {
        year = localYear;
    }
    return localYear;
}

static void
parseMappings(FILE *f, UCMFile *ucm) {
    char line[200];
    char *s, *end;
    int32_t lineNum=0;
    int32_t startSkipLineNum=0, endSkipLineNum = 0;
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
            UBool nonCommentFound = FALSE;
            startSkipLineNum = lineNum;
            /* Ignore the next few mappings. They have no value */
            while(fgets(line, sizeof(line), f)!=NULL) {
                s=(char *)u_skipWhitespace(line);
                lineNum++;
                if(uprv_strstr(s, "* The official table starts here:")!=NULL) {
                    break;  /* continue with outer loop */
                }
                if (s[0] != '*') {
                    nonCommentFound = TRUE;
                }
            }
            endSkipLineNum = lineNum-1;
            if (nonCommentFound) {
                fprintf(stderr, "Warning: skipped lines %d-%d, since it doesn't seem to be real data\n", startSkipLineNum, endSkipLineNum);
            }
        }

        /* explicit end of table */
        if(uprv_memcmp(s, "END CHARMAP", 11)==0) {
            break;
        }

        /* comment lines, parse substitution characters, otherwise skip them */
        if(*s=='#' || *s=='*') {
            /* get subchar1 */
            s=uprv_strstr(line, "for U+00xx");
            if(s==NULL) {
                s=uprv_strstr(line, "for U+000000xx");
            }
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
            if(s==NULL) {
                s=uprv_strstr(line, "for U+000xxxxx");
            }
            if(s==NULL) {
                s=uprv_strstr(line, "for U+0000xxxx");
            }
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
                int len = (int)uprv_strlen(s);
                while (!isdigit(s[len-1])) {
                    len--;
                }
                parseYear(s+len-4, line);
                continue;
            }

            /* Handle "File updated on:" or "update:" */
            s=uprv_strstr(line, "update");
            if(s!=NULL && uprv_strstr(s, ":") != NULL) {
                int len = (int)uprv_strlen(s);
                while (!isdigit(s[len-1])) {
                    len--;
                }
                parseYear(s+len-4, line);
                continue;
            }

            s=uprv_strstr(line, "Update");
            if(s!=NULL && uprv_strstr(s, ":") != NULL) {
                int len = (int)uprv_strlen(s);
                if (uprv_strstr(s, "(")) {
                    while (s[len] != '(') {
                        len--;
                    }
                }
                while (!isdigit(s[len-1])) {
                    len--;
                }
                parseYear(s+len-4, line);
                continue;
            }

            /* get creation date */
            s=uprv_strstr(line, "Creation date:");
            if(s!=NULL) {
                int len = (int)uprv_strlen(s);
                while (!isdigit(s[len-1])) {
                    len--;
                }
                parseYear(s+len-4, line);
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
getSubchar(uint16_t ccsidToMatch) {
    int i;

    for(i=0; i<sizeof(knownSubchars)/sizeof(knownSubchars[0]); ++i) {
        if(knownSubchars[i].ccsid == ccsidToMatch) {
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
        if(knownStateTables[i].ccsid == ccsid && (knownStateTables[i].unicode == 0 || knownStateTables[i].unicode == unicodeCCSID)) {
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
        "# *   Copyright (C) 1995-2007, International Business Machines\n"
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
        else if (maxCharLength==1) {
            if (subchar!=subchar1) {
                fprintf(stderr, "warning: <subchar1> \\x%02X is ignored for SBCS charsets.\n", subchar1);
            }
            /* else we got a duplicate subchar and subchar1. */
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

    ucm_processStates(states, false);

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

static const TMAParray *
findTPMAPs(const char *rmapExtention) {
    int32_t idx;
    for (idx = 0; idx < (int32_t)(sizeof(knownRMAPtoTMAP)/sizeof(knownRMAPtoTMAP[0])); idx++) {
        if (knownRMAPtoTMAP[idx].ccsid == ccsid && strcmp(rmapExtention, knownRMAPtoTMAP[idx].RMAP) == 0) {
            return &(knownRMAPtoTMAP[idx].TMAP);
        }
    }
    return NULL;
}

static char **
createTPMAPNames(const char *origRpmapFilename, int32_t *numFiles, UBool *multiplePossible) {
    char *rpmapFilename = strdup(origRpmapFilename);
    char *packageFilename;
    char *extension;
    const char *rpmapExtension;
    FILE *packageFile = NULL;
    char **tpmapFiles = NULL;
    int32_t length;
    const TMAParray *TMAPs;

    *numFiles = 0;
    *multiplePossible = FALSE;
    packageFilename = (char *)malloc(strlen(origRpmapFilename) + 8);
    length = (int32_t)strlen(rpmapFilename);

    rpmapExtension = strrchr(origRpmapFilename, '.') + 1;
    uprv_memmove(rpmapFilename+length-17, origRpmapFilename+length-13, 4);
    uprv_memmove(rpmapFilename+length-13, origRpmapFilename+length-17, 4);
    strcpy(packageFilename, origRpmapFilename);
    uprv_memmove(packageFilename+length-17, origRpmapFilename+length-13, 4);
    uprv_memmove(packageFilename+length-13, origRpmapFilename+length-17, 4);
    packageFilename[length-9] = 0;
    strcat(packageFilename, ".PACKAGE");
    extension = strrchr(packageFilename, '.');
    packageFile = fopen(packageFilename, "r");
    TMAPs = findTPMAPs(rpmapExtension);
    if (TMAPs != NULL || packageFile != NULL) {
        int32_t idx;
        TMAPs = findTPMAPs(rpmapExtension);
        if (TMAPs == NULL) {
            fprintf(stderr, "error: \"%s\" has a package, but has no recognized TPMAP table\n", rpmapFilename);
            exit(1);
        }
        if (packageFile != NULL) {
            fprintf(stderr, "warning: This tool doesn't read package files yet. So the correct list of alternate mapping files may be out of date.\n");
        }
        while ((*TMAPs)[*numFiles] != NULL) {
            (*numFiles)++;
        }
        tpmapFiles = (char **)malloc(sizeof(FILE*)*(*numFiles));
        for (idx = 0; idx < *numFiles; idx++) {
            tpmapFiles[idx] = strdup(rpmapFilename);
            strcpy(tpmapFiles[idx]+(length-8), (*TMAPs)[idx]);
        }
        *multiplePossible = TRUE;
        if (packageFile) {
            fclose(packageFile);
        }
    }
    else {
        /* No Package information. Use the default name. */
        tpmapFiles = (char **)malloc(sizeof(FILE*));
        tpmapFiles[0] = strdup(rpmapFilename);
        if(tpmapFiles[0][length-8]=='R') {
            tpmapFiles[0][length-8]='T';
        } else {
            tpmapFiles[0][length-8]='t';
        }
        *numFiles = 1;
    }

    free(rpmapFilename);
    free(packageFilename);
    return tpmapFiles;
}

static void
freeTPMAPNames(char **tpmapFiles, int32_t numFiles) {
    int32_t idx;
    for (idx = 0; idx < numFiles; idx++) {
        free(tpmapFiles[idx]);
    }
    free(tpmapFiles);
}

static void
setCCSID(uint32_t value) {
    if (!getCCSIDValues(value, &unicodeCCSID, &ccsid)) {
        fprintf(stderr, "error: %X is not a Unicode conversion table\n", value);
        exit(1);
    }
}

static void
processTable(const char *arg) {
    char filename[1024], tpname[32];
    const char *basename, *s;
    const char *ucmFilename, *tmapFilename;
    FILE *rpmap, *tpmap, *ucm;
    uint32_t value;
    int length, idx;
    char **tpmapFileStrings;
    int32_t tpmapFileStringsNum;
    UBool multipleTablesPossible;
    UErrorCode errorCode = U_ZERO_ERROR;

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

    setCCSID(value);

    /* try to find all the TPMAP files for this RPMAP */
    tpmapFileStrings = createTPMAPNames(arg, &tpmapFileStringsNum, &multipleTablesPossible);

    cleanup();

    for (idx = 0; idx < tpmapFileStringsNum; idx++) {
        init();
        setCCSID(value);

        /* try to open the RPMAP file */
        rpmap=fopen(arg, "r");
        if(rpmap==NULL) {
            fprintf(stderr, "error: unable to open \"%s\"\n", arg);
            exit(1);
        }

        tpmap=fopen(tpmapFileStrings[idx], "r");
        if (tpmap == NULL) {
            /* there is no TPMAP */
            fprintf(stderr, "error: unable to find the TPMAP file \"%s\" for \"%s\"\n", tpmapFileStrings[idx], arg);
            exit(1);
        }
        puts(tpmapFileStrings[idx]);
        length=(int)uprv_strlen(tpmapFileStrings[idx]);
        uprv_strcpy(tpname, tpmapFileStrings[idx]+length-17);

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
        if(subchar==0 && !getSubchar(ccsid)) {
            fprintf(stderr, "warning: missing subchar in \"%s\" (CCSID=0x%04X)\n", filename, ccsid);
        }

        /* generate the .ucm filename */
        tmapFilename = strrchr(tpmapFileStrings[idx], '/');
        if (tmapFilename == NULL) {
            tmapFilename = strrchr(tpmapFileStrings[idx], '\\');
        }
        if (tmapFilename == NULL) {
            tmapFilename = tpmapFileStrings[idx];
        }
        else {
            tmapFilename++; /* Skip the file separator */
        }

        ucmFilename = filenameHistory->getFilename(basename, tmapFilename, year, &errorCode);
        if (U_FAILURE(errorCode)) {
            fprintf(stderr, "error: Can't generate filename from %s - %s\n", basename, u_errorName(errorCode));
            exit(1);
        }

        /* merge the mappings */
        mergeMappings();

        /* analyze the conversion table */
        analyzeTable();

        /* open the .ucm file */
        ucm=fopen(ucmFilename, "w");
        if(ucm==NULL) {
            fprintf(stderr, "error: unable to open output file \"%s\"\n", filename);
            exit(4);
        }

        /* remove the .ucm from the filename for the following processing */
        strcpy(filename, ucmFilename);
        filename[uprv_strlen(filename)-4]=0;

        /* write the .ucm file */
        writeUCM(ucm, filename, basename, tpname);
        fclose(ucm);
        cleanup();
    }
    freeTPMAPNames(tpmapFileStrings, tpmapFileStringsNum);
}


enum
{
    HISTORY_FILE
};

UOption options[]={
    UOPTION_DEF( "historyFile", 'f', UOPT_REQUIRES_ARG),
};

int main(int argc, char* argv[])
{
    UErrorCode status = U_ZERO_ERROR;
    argc = u_parseArgs(argc, argv, (int32_t)(sizeof(options)/sizeof(options[0])), options);
    if(argc<2 || !options[HISTORY_FILE].doesOccur) {
        fprintf(stderr,
                "usage: %s -f historyFile.txt { rpmap/rxmap-filename }+\n",
                argv[0]);
        exit(1);
    }

    filenameHistory = FilenameMappingHistory::create(options[HISTORY_FILE].value, &status);
    if (U_FAILURE(status)) {
        fprintf(stderr,
                "usage: %s could not use \"%s\". error=%s\n",
                argv[0], filenameHistory, u_errorName(status));
        exit(1);
    }

    while(--argc>0) {
        puts(*++argv);
        processTable(*argv);
    }

    filenameHistory->writeHistoryFile(&status);

    return 0;
}
