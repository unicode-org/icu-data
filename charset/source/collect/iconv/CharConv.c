/*
*******************************************************************************
*
*   Copyright (C) 2000-2001, International Business Machines
*   Corporation and others.  All Rights Reserved.
*
*******************************************************************************
*
*  Modification History:
*
*   Date        Name        Description
*   11/03/2000  Jason M.    Created.
*   05/18/2001  grhoten     Collect more accurate infomation from iconv,
*                           clean code
*
*/

#include <time.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <iconv.h>
#include <stdlib.h>
#include <unistd.h>
#include <limits.h>
/*#include <sys/debug.h>*/

/*#define DEBUG /* for assert macro */
#define BUFFER 16

enum boolean { FALSE_, TRUE_ };

/* changing this to 1 will shift the tool into debuging mode.
 * All output will then be directed to standard out (no files written).
 */
#define DEBUGING 0

/* Global error flag.
 * When TRUE_ indicates iconv(..) raised an EINVAL (illegal input value)
 * flag when trying to make a conversion from a VALID UTF-8 byte sequence to
 * the current code page.
 */
enum boolean iconv_error_fromUTF8 = FALSE_;

/* Global error flag.
 * When TRUE_ indicates iconv(..) raised an EINVAL (illegal input value)
 * flag when trying to make a conversion from a VALID code page byte sequence to UTF-8.
 */
enum boolean iconv_error_fromCP = FALSE_;




static void
writeByteSeqInHex(/* IN */ FILE *fp,
                  /* IN */ const unsigned char* byteSeq,
                  /* IN */ size_t lenByteSeq,
                  /* IN */ int lenToWrite)
{
    int j;

    for (j = 0; j < lenToWrite; j++) {
        if (j < lenByteSeq) {
            fprintf(fp, "\\x%02X", byteSeq[j]);
        }
    }
}

void checkForConversionErrors(/* IN */ const char* UTF8name,
                              /* IN */ size_t errFlag,
                              /* IN */ const char* fromCodePageName,
                              /* IN */ const unsigned char* fromByteArray,
                              /* IN */ size_t lenFromByteArray,
                              /* IN */ const char* toCodePageName)
{
    int j;

    if (errFlag == (size_t)-1) {

        /*fprintf(stdout, "%s%s%s%s\n", "iconv conversion error was between ", 
                         fromCodePageName, " and ", toCodePageName);
        */

        if (errno == EINVAL) {
            /*puts("EINVAL error: incomplete character or shift sequence "
                "at the end of the input buffer!\n");
            fprintf(stdout, "The incomplete character was:  ");
            writeByteSeqInHex(stdout, fromByteArray, lenFromByteArray, lenFromByteArray);
            fprintf(stdout, "\n");
            */
            if (!strcmp(fromCodePageName, UTF8name)) {
                iconv_error_fromUTF8 = TRUE_;
            } else {
                iconv_error_fromCP = TRUE_;
            }
        } else if (errno == E2BIG) {
            fprintf(stdout, "E2BIG error:  lack of space in output buffer!\n");
            exit(1);
        } else if (errno == EILSEQ) {
            /*fprintf(stdout, "EILSEQ error:  byte sequence does not belong to input code set!\n");
            fprintf(stdout, "input byte sequence that failed to convert:  ");
            writeByteSeqInHex(stdout, fromByteArray, lenFromByteArray, lenFromByteArray);
            fprintf(stdout, "\n");
            */
            if (!strcmp(fromCodePageName, UTF8name)) {
                iconv_error_fromUTF8 = TRUE_;
            } else {
                iconv_error_fromCP = TRUE_;
            }
        } else if (errno == EBADF) {
            fprintf(stdout, "EBADF error:  invalid conversion descriptor!\n");
            exit(1);
        } else {
            fprintf(stdout, "undefined error\n");
            exit(1);
        }
    }
}

enum boolean errorOnOpen(/* IN */ iconv_t errFlag,
                         /* IN */ const char* fromCodePageName,
                         /* IN */ const char* toCodePageName)
{
    if (errFlag == (iconv_t)-1) {
        fprintf(stdout, "\n%s%s%s%s\n", "iconv_open error between ", 
            fromCodePageName, " and ", toCodePageName);
        if (errno == EMFILE) {
            fprintf(stdout, "too many system file descriptors open!\n");
        } else if (errno == ENFILE) {
            fprintf(stdout, "too many system files currently open!\n");
        } else if (errno == ENOMEM) {
            fprintf(stdout, "not enough system storage space available!\n");
        } else if (errno == EINVAL) {
            fprintf(stdout, "code conversion specified not supported by this system!\n");
            return TRUE_;
        } else {
            fprintf(stdout, "undefined error\n");
        }
        exit(1);
    }
    return FALSE_;
}

enum boolean UTF16toUTF8(/* IN  */ unsigned int UTF16,
                         /* OUT */ unsigned char* UTF8,
                         /* OUT */ size_t* lenUTF8)
{
    if (UTF16 < 0x00) {
        *lenUTF8 = (size_t)0;
        return FALSE_;
    } else if (UTF16 < 0x0080) {
        UTF8[0] = (unsigned char)UTF16;
        UTF8[1] = '\0';
        *lenUTF8 = (size_t)1;
        return TRUE_;
    } else if (UTF16 < 0x0800) {
        UTF8[0] = (unsigned char) ((UTF16 >> 6) | 0x00C0);
        UTF8[1] = (unsigned char) ((UTF16 & 0x003F) | 0x0080);
        UTF8[2] = '\0';
        *lenUTF8 = (size_t)2;
        return TRUE_;
    } else if ( (UTF16 < 0xD800) || (UTF16 >= 0xE000 && UTF16 < 0xFFFE) ) {
        UTF8[0] = (unsigned char) ((UTF16 >> 12) | 0x00E0);
        UTF8[1] = (unsigned char) (((UTF16 & 0x0FC0) >> 6) | 0x0080);
        UTF8[2] = (unsigned char) ((UTF16 & 0x003F) | 0x0080);
        UTF8[3] = '\0';
        *lenUTF8 = (size_t)3;
        return TRUE_;
    } else {          /* surrogates not supported */
        *lenUTF8 = (size_t)0;
        return FALSE_;
    }
}

void convertChar(/* IN  */  const char* UTF8name,
                 /* IN  */  iconv_t conversionDescripter,
                 /* IN  */  const char *fromCodePageName,
                 /* IN  */  const unsigned char *fromByteArray,
                 /* IN  */  size_t lenFromByteArray,
                 /* IN  */  const char *toCodePageName,
                 /* OUT */  unsigned char *toByteArray,
                 /* OUT */  size_t *lenToByteArray)
{
    char *tptr = (char *)toByteArray;
    const char **fptr = NULL;
    size_t ileft = 0;
    size_t oleft = BUFFER;
    size_t retValue;

    /* reset to initial state, which is needed by Solaris */
    retValue = iconv(conversionDescripter, fptr, &ileft,
        &tptr, &oleft);

    tptr = (char *)toByteArray;
    fptr = (const char **)&fromByteArray;
    ileft = lenFromByteArray;
    oleft = BUFFER;

    /* do the actual conversion here */
    retValue = iconv(conversionDescripter, fptr, &ileft,
        &tptr, &oleft);
    /* ASSERT(ileft == 0); */

    checkForConversionErrors(UTF8name, retValue, fromCodePageName,fromByteArray,
        lenFromByteArray, toCodePageName);
    *lenToByteArray = BUFFER - oleft;

    /* append a null to the end of each char so we can use
     * standard string functions to compare multibyte chars
     */
    *tptr = '\0';
}

void
analyzeCharMappingAndWriteOneLine(/* IN */ unsigned int uniScalar,
                                  /* IN */ const unsigned char *galleyChar2,
                                  /* IN */ const unsigned char *galleyChar3,
                                  /* IN */ const unsigned char *startUTF8char,
                                  /* IN */ size_t lenStartUTF8char,
                                  /* IN */ const unsigned char *CPchar,
                                  /* IN */ size_t lenCPchar,
                                  /* IN */ const unsigned char *CPcharWithShiftState,
                                  /* IN */ size_t lenCPcharWithShiftState,
                                  /* IN */ const unsigned char *endUTF8char,
                                  /* IN */ size_t lenEndUTF8char,
                                  /* IN */ FILE *fp)
{
    enum boolean isFallback;
    unsigned int j;

    if (iconv_error_fromUTF8) {
      /*fprintf(stdout, "#U%04x>\t\t%s\n", uniScalar, "????  iconv error\t?");*/
        iconv_error_fromUTF8 = FALSE_;
        return;
    } else if (iconv_error_fromCP) {
      /*fprintf(stdout, "#U%04x>\t\t%s\n", uniScalar, "????  iconv error\t?");*/
        iconv_error_fromCP = FALSE_;
        if (!strcmp((char*)galleyChar3, (char*)CPchar) ||
            !strcmp((char*)galleyChar2, (char*)CPchar)) {
            return;
        } else {
            isFallback = TRUE_;
        }
    } else if (!strcmp((char*)startUTF8char, (char*)endUTF8char)) { /* roundtrip */
        isFallback = FALSE_;
    } else if (!strcmp((char*)galleyChar3, (char*)CPchar) ||        /* mapping to galleyChar */
        !strcmp((char*)galleyChar2, (char*)CPchar)) {
        return;
    } else {                                                        /* fallback */
        isFallback = TRUE_;
    }

    /* write unicode scalar value */
    fprintf(DEBUGING ? stdout : fp, "<U%04X>  ", uniScalar);

    /* write start UTF8 byte sequence */
#if DEBUGING
    writeByteSeqInHex(stdout, startUTF8char, lenStartUTF8char, 4);
    fprintf(stdout, "CP: ");
#endif

    /* write codepage byte sequence */
    writeByteSeqInHex(DEBUGING ? stdout : fp, CPchar, lenCPchar, 5);

    /* write codepage byte sequence with leading shift state */
#if DEBUGING
    fprintf(stdout, "CP+S: ");
    writeByteSeqInHex(stdout, CPcharWithShiftState, lenCPcharWithShiftState,10);

    /* write end UTF8 byte sequence */
    fprintf(stdout, "8: ");
    writeByteSeqInHex(stdout, endUTF8char, lenEndUTF8char, 4);
#endif

    /* write fallback code (0 => 1-1 roundtrip mapping, 1 => fallback mapping)*/
    fprintf(DEBUGING ? stdout : fp, " |%i\n", isFallback);
}

char* extractCodePageName_SUN(/* IN  */ char *conversionFilename)
{
/*
On Sun machines, iconv conversion files are stored in /usr/lib/iconv in this
format: "fromCodepageName%toCodepageName.so".  This function returns a char
pointer to the string "toCodepageName" which is latter used as an argument to
the iconv_open function.  Currently, the conversion file names are obtained
using a script which pipes them as command line args to this program.
Here is the script:
     find /usr/lib/iconv -name "UTF-8%*.so" | xargs a.out
As you can see we are only interested in conversion files that begin with
"UTF-8" since these make up the bulk of unicode conversions available on SUN OS.
*/
    char *cf = strrchr(conversionFilename, '%');
    char *endName = strrchr(conversionFilename, '.');
    *endName = '\0';
    cf++;
    return cf;
}

char* extractCodePageName_HP(/* IN */ char *conversionFilename)
{
/*
On HP machines, iconv conversion files are stored in /usr/lib/nls/iconv/tables
in this format: "fromCodepageName=toCodepageName".  The tables directory
contains no UTF-8 conversion files but does contain UCS-2 conversion files.
Inspection of the document /usr/lib/nls/iconv/config.iconv shows that these
same conversions are also supported in UTF-8.  We will use conversion filenames
of the form "UCS2=toCodepageName" to determine the appropriate codepage names,
but then do the actual round trip conversions using UTF-8.
*/
    char *cf = strrchr(conversionFilename, '=');
    return ++cf;
}

char* extractCodePageName_IBM(/* IN */ char *conversionFilename)
{
/*
On IBM machines, supported UCS-2 (and UTF-8) conversion filenames are stored in
several directories.  The easiest such list to use is at:
/usr/lib/nls/loc/uconvTable.  This list can be read directly and used as
codepage names for the iconv function.  Unfortunately, the find command used
in the CharConv.sh script returns the absolute path to the filenames so we
need to strip everything away before the last "/".
*/
    char *cf = strrchr(conversionFilename, '/');
    return ++cf;
}

char* extractCodePageName_LINUX(/* IN  */ char *conversionFilename,
                                /* OUT */ char *codePageName)
{
/*
On Redhat Linux machines, supported UTF-8 conversion filenames are located
at /usr/lib/gconv in the format "codePageName.so".  The absolute path is
returned by the ls command so we need to strip everything away before the
last "/" plus remove the trailing ".so".
*/
    char cf[32];
    char *libName;

    libName = strrchr(conversionFilename, '/');
    if (libName == NULL) {
        fprintf(stderr, "Can't find expected '/' in conversionFilename!\n");
        exit(1);
    }
    libName++;
    strcpy(cf, libName);
    if (!strcmp(&cf[strlen(cf)-3], ".so")) {
        cf[strlen(cf)-3] = '\0';
    }
    if (strncmp(cf, "ISO8859", 7) == 0) {
        fprintf(stderr, "Alias %s -> ", cf);
        memmove(&cf[4], &cf[3], strlen(&cf[3]) + 1);
        cf[3] = '-';
        fprintf(stderr, "%s\n", cf);
        strcpy(codePageName, cf);
    }
    else if (strcmp(cf, "ISO646") == 0) {
        strcpy(codePageName, "ISO646-US");
        fprintf(stderr, "Alias %s -> %s\n", cf, codePageName);
    }
    else {
        strcpy(codePageName, cf);
    }
}

void writeHeader(/* IN */ FILE *fp,
                 /* IN */ const char *OSinfo,
                 /* IN */ const char *codePageName,
                 /* IN */ const unsigned char *galleyChar2,
                 /* IN */ size_t lenGalleyChar2,
                 /* IN */ const unsigned char *galleyChar3,
                 /* IN */ size_t lenGalleyChar3,
                 /* OUT */ unsigned int minNumChars,
                 /* OUT */ unsigned int maxNumChars)
{
    time_t currTime;
    char timeBuf[64];

    time(&currTime);
    strftime(timeBuf, sizeof(timeBuf), "%a %b %d %H:%M:%S %Z %Y",
        localtime(&currTime));

    fputs("#________________________________________________________________________\n", fp);
    fputs("#\n", fp);
    fputs("# (C) COPYRIGHT International Business Machines Corp. 2001\n", fp);
    fputs("#     All Rights Reserved\n", fp);
    fputs("#\n", fp);
    fputs("#________________________________________________________________________\n", fp);
    fputs("#\n", fp);
    fprintf(fp, "# File created on %s\n", timeBuf);
    fputs("#\n", fp);
    fputs("# File created by CharConv tool.\n", fp);
    fprintf(fp, "# Platform: %s\n", OSinfo);
    fputs("#\n", fp);
    fputs("# Table Version : 1.0\n", fp);
    fputs("# The 1st column is the Unicode scalar value.\n", fp);
    fputs("# The 2nd column is the codepage byte sequence.\n", fp);
    fputs("# The 3rd column is the fallback indicator.\n", fp);
    fputs("# The fallback indicator can have one of the following values:\n", fp);
    fputs("#   |0 for exact 1-1 roundtrip mapping\n", fp);
    fputs("#   |1 for the best fallback codepage byte sequence.\n", fp);
    fputs("#   |2 for the substitution character\n", fp);
    fputs("#   |3 for the best reverse fallback Unicode scaler value\n", fp);
    fputs("#\n", fp);
    fprintf(fp, "<code_set_name>               \"%s\"\n", codePageName);
    if (minNumChars == 1 && maxNumChars == 1) {
        fputs("<mb_cur_max>                  1\n", fp);
        fputs("<mb_cur_min>                  1\n", fp);
        fputs("<uconv_class>                 \"SBCS\"\n", fp);
    }
    /* else it may be DBCS, MBCS or EBCDIC_STATEFUL
     * We don't know if the converter works with DBCS yet
     */
    if (lenGalleyChar3 > 0) {
        fputs("<subchar>                     ", fp);
        writeByteSeqInHex(fp, galleyChar3, lenGalleyChar3, lenGalleyChar3);
    }
    else {
        puts("\nWarning: No <subchar> found!");
    }
    if (lenGalleyChar2 > 0 && strcmp((const char *)galleyChar2, (const char *)galleyChar3)) {
        fputs("\n#<subchar1>                   ", fp);
        writeByteSeqInHex(fp, galleyChar2, lenGalleyChar2, lenGalleyChar2);
    }
    fputs("\n#\n", fp);
    fputs("CHARMAP\n", fp);
    fputs("#\n", fp);
    fputs("#\n", fp);
    fprintf(fp, "#UNICODE %s\n", codePageName);
    fputs("#_______ _________\n", fp);
}

void writeFooter(/* IN */ FILE *fp) {
    fputs("#\n", fp);
    fputs("END CHARMAP\n", fp);
    fputs("#\n", fp);
}

/*
  The original person that wrote this program thought substitution characters
  were really called galley characters.
*/
void getGalleyChars(/* IN  */  const char *UTF8name,
                    /* IN  */  iconv_t cd,
                    /* IN  */  const char *codePageName,
                    /* OUT */  unsigned char *galleyChar2,
                    /* OUT */  size_t *lenGalleyChar2,
                    /* OUT */  unsigned char *galleyChar3,
                    /* OUT */  size_t *lenGalleyChar3)
{
    unsigned char startUTF8char[BUFFER];
    size_t lenStartUTF8char;

    /* determine galley character for 2 byte UTF-8 values */
    if (!UTF16toUTF8(0x04FF, startUTF8char, &lenStartUTF8char)) {
        fprintf(stdout, "Error converting U+04FF to UTF-8 form!");
        exit(1);
    }
    /* set initial shift state then convert twice to avoid shift characters
       in output
     */
    /*convertChar(UTF8name, cd, UTF8name, NULL, lenStartUTF8char, 
                codePageName, galleyChar2, lenGalleyChar2);*/
    convertChar(UTF8name, cd, UTF8name, startUTF8char, lenStartUTF8char, 
        codePageName, galleyChar2, lenGalleyChar2);
    convertChar(UTF8name, cd, UTF8name, startUTF8char, lenStartUTF8char, 
        codePageName, galleyChar2, lenGalleyChar2);

    /* determine galley character for 3 byte UTF-8 values */
    if (!UTF16toUTF8(0xFFFD, startUTF8char, &lenStartUTF8char)) {
        fprintf(stdout, "Error converting U+FFFD to UTF-8 form!");
        exit(1);
    }

    /* set initial shift state then convert twice to avoid shift characters
       in output
     */
    /*convertChar(UTF8name, cd, UTF8name, NULL, lenStartUTF8char, 
                codePageName, galleyChar3, lenGalleyChar3);*/
    convertChar(UTF8name, cd, UTF8name, startUTF8char, lenStartUTF8char, 
        codePageName, galleyChar3, lenGalleyChar3);
    convertChar(UTF8name, cd, UTF8name, startUTF8char, lenStartUTF8char, 
        codePageName, galleyChar3, lenGalleyChar3);

    /* Does the silly converter know about 0xFFFD being a subchar? */
    if (*lenGalleyChar3 <= 0) {
        if (!UTF16toUTF8(0x001A, startUTF8char, &lenStartUTF8char)) {
            fprintf(stdout, "Error converting U+001A to UTF-8 form!");
            exit(1);
        }
        convertChar(UTF8name, cd, UTF8name, startUTF8char, lenStartUTF8char, 
            codePageName, galleyChar3, lenGalleyChar3);
    }
}

static void
getMinMaxNumChars(/* IN  */  const char *UTF8name,
                  /* IN  */  const char *codePageName,
                  /* OUT */  unsigned int *min,
                  /* OUT */  unsigned int *max)
{
    unsigned char startUTF8char[BUFFER], CPcharWithShiftState[BUFFER];
    size_t lenStartUTF8char = 0, lenCPcharWithShiftState = 0;
    unsigned int uniScalar;

    /* get a UTF-8 --> codePageName conversion descriptor */
    iconv_t toCP = iconv_open(codePageName, UTF8name);
    if (errorOnOpen(toCP, UTF8name, codePageName)) {
        return;
    }
    *min = UINT_MAX;
    *max = 0;

    /* transcode all non-surrogate unicode scalar values */
    for (uniScalar = 0x0000; uniScalar < 0xFFFE; uniScalar++) {
        if (uniScalar == 0xD800) { /* skip surrogates */
            uniScalar = 0xE000;
        }

        iconv_error_fromUTF8 = FALSE_;
        iconv_error_fromCP = FALSE_;

        /* UTF-16 --> UTF-8 */
        if (!UTF16toUTF8(uniScalar, startUTF8char, &lenStartUTF8char)) {
            fprintf(stdout, "Error converting from UTF-16 to UTF-8");
            exit(1);
        }

        /* UTF-8 --> codePageName */
        convertChar(UTF8name, toCP, UTF8name, startUTF8char, lenStartUTF8char, 
            codePageName, CPcharWithShiftState, &lenCPcharWithShiftState);

        if (lenCPcharWithShiftState > 0 && !iconv_error_fromUTF8)
        {
            if (lenCPcharWithShiftState < *min)
            {
                *min = lenCPcharWithShiftState;
            }
            if (lenCPcharWithShiftState > *max)
            {
                *max = lenCPcharWithShiftState;
            }
        }
    }
    iconv_close(toCP);
}

void openFile(/* OUT    */ FILE **fp,
              /* IN/OUT */ char *codePageName,
              /* IN     */ const char *OSname,
              /* IN/OUT */ char *OSversion)
{
    int j = 0;
    char filename[32];

    strcpy(filename, OSname);

    /* -  ==> _ in codePageName */
    while (codePageName[j] != '\0') {
        if (codePageName[j] == '-') {
            codePageName[j] = '_';
        }
        if (++j > 30) {
            fprintf(stdout, "Problem with codepage name.  Aborting...\n");
            exit(1);
        }
    }

    strcat(filename, codePageName);
    if (!strcmp(OSname, "solaris-")) {    /* uname reports SunOS ver 5.7 */
        strcpy(OSversion, "2.7");         /*   we want solaris ver 2.7 */
    } else if (!strcmp(OSname, "hpux-")) {
        strcpy(OSversion, "11.0");
    } else if (!strcmp(OSname, "aix-")) {
        strcpy(OSversion, "4.3.6");
    } else if (!strcmp(OSname, "glibc-")) {
        strcpy(OSversion, "2.1.2");
    }
    strcat(filename, "-");
    strcat(filename, OSversion);
    strcat(filename, ".ucm");
    if (!(*fp = fopen(filename, "w"))) {
        fprintf(stdout, "%s%s%s", "Unable to open file", filename, "!\n");
        exit(1);
    }
}

int main(int argc, char **argv)
{
    iconv_t toCP, toUTF8;             /* conversion descriptors */
    FILE *fp;

    unsigned char startUTF8char[BUFFER], endUTF8char[BUFFER], CPchar[BUFFER];
    unsigned char galleyChar2[BUFFER], galleyChar3[BUFFER], CPcharWithShiftState[BUFFER];

    size_t lenStartUTF8char = 0, lenEndUTF8char = 0, lenCPchar = 0;
    size_t lenGalleyChar2 = 0, lenGalleyChar3 = 0, lenCPcharWithShiftState = 0;

    unsigned int uniScalar, i, minChars, maxChars;

    char OSname[16], *OSinfo, *OSversion, UTF8name[16];
    char codePageNameBuf[32];
    char *codePageName;

    int numWarnings;

    OSinfo = getenv("OS_INFO");
    OSversion = getenv("OS_VERSION");

    if (!OSinfo) {
        fputs("OS_INFO was not set!\n", stderr);
    }
    if (!OSversion) {
        fputs("OS_VERSION was not set!\n", stderr);
    }

    if (argc < 2) {
        fprintf(stdout, "Did not receive conversion filenames from 'CharConv' script!\n");
        return 1;
    }

    if (strstr(OSinfo, "SUN") || strstr(OSinfo, "Sun") || strstr(OSinfo, "sun")) {
        strcpy(OSname, "solaris-");
        strcpy(UTF8name, "UTF-8");
    } else if (strstr(OSinfo, "HP") || strstr(OSinfo, "Hp") || strstr(OSinfo, "hp")) {
        strcpy(OSname, "hpux-");
        strcpy(UTF8name, "utf8");
    } else if (strstr(OSinfo, "AIX") || strstr(OSinfo, "aix")) {
        strcpy(OSname, "aix-");
        strcpy(UTF8name, "UTF-8");
    } else if (strstr(OSinfo, "Linux") || strstr(OSinfo, "linux") || strstr(OSinfo, "LINUX")) {
        strcpy(OSname, "glibc-");
        strcpy(UTF8name, "UTF8");
    } else {
        fprintf(stdout, "Don't know how to extract codepage names for this OSinfo!\n");
        return 1;
    }

    fprintf(stdout, "argc: %i\n", argc);

    for (i = 1; i < argc; i++) {
        if (!strcmp(OSname, "solaris-")) {
            codePageName = extractCodePageName_SUN(argv[i]);
            if (strstr(codePageName, "UCS-") || strstr(codePageName, "UTF-")) {
                fprintf(stdout, "  skipping %s...\n", codePageName);
                continue;
            }
/*            if (!strncmp(codePageName, "8859-", 5)
             || !strcmp(codePageName, "tis620.2533")
             || !strcmp(codePageName, "iso8859-11")) {
                fprintf(stdout, "  skipping duplicate %s...\n", codePageName);
                continue;
            }
*/
        } else if (!strcmp(OSname, "hpux-")) {
            codePageName = extractCodePageName_HP(argv[i]);
        } else if (!strcmp(OSname, "aix-")) {
            codePageName = extractCodePageName_IBM(argv[i]);
            if (!strcmp(codePageName, "README") || !strcmp(codePageName, "uconvTable")) {
                continue;
            }
        } else if (!strcmp(OSname, "glibc-")) {
            extractCodePageName_LINUX(argv[i], codePageNameBuf);
            codePageName = codePageNameBuf;
            if (strstr(codePageName, "lib")) {
                fprintf(stdout, "  skipping %s...\n", codePageName);
                continue;
            }
        } else {
            fprintf(stdout, "Problem matching a known OS name!\n");
            return 1;
        }

        fprintf(stdout, "processing argv[%i] (%s)", i, codePageName);

        /* get a UTF-8 --> codePageName conversion descriptor */
        toCP = iconv_open(codePageName, UTF8name);
        if (errorOnOpen(toCP, UTF8name, codePageName)) {
            continue;
        }

        /* get a codePageName --> UTF-8 conversion descriptor */
        toUTF8 = iconv_open(UTF8name, codePageName);
        if (errorOnOpen(toUTF8, codePageName, UTF8name)) {
            iconv_close(toCP);
            continue;
        }

        getMinMaxNumChars(UTF8name, codePageName, &minChars, &maxChars);

        openFile(&fp, codePageName, OSname, OSversion);

        /* get "galley" characters for codepage */
        getGalleyChars(UTF8name, toCP, codePageName, galleyChar2, &lenGalleyChar2,
            galleyChar3, &lenGalleyChar3);

        writeHeader(fp, OSinfo, codePageName, galleyChar2, lenGalleyChar2,
            galleyChar3, lenGalleyChar3, minChars, maxChars);

        numWarnings = 0;

        /* transcode all non-surrogate unicode scalar values */
        for (uniScalar = 0x0000; uniScalar < 0xFFFE; uniScalar++) {
            if (uniScalar == 0xD800) { /* skip surrogates */
                uniScalar = 0xE000;
            }

            iconv_error_fromUTF8 = FALSE_;
            iconv_error_fromCP = FALSE_;

            /* UTF-16 --> UTF-8 */
            if (!UTF16toUTF8(uniScalar, startUTF8char, &lenStartUTF8char)) {
                fprintf(stdout, "Error converting from UTF-16 to UTF-8");
                return 1;
            }

            /* UTF-8 --> codePageName */
            /*convertChar(UTF8name, toCP, UTF8name, NULL, lenStartUTF8char, 
                        codePageName, CPchar, &lenCPchar);*/
            convertChar(UTF8name, toCP, UTF8name, startUTF8char, lenStartUTF8char, 
                codePageName, CPcharWithShiftState, &lenCPcharWithShiftState);
            convertChar(UTF8name, toCP, UTF8name, startUTF8char, lenStartUTF8char, 
                codePageName, CPchar, &lenCPchar);

            if (numWarnings < 40 && lenCPcharWithShiftState != lenCPchar) {
                printf("\nCodepage shift occured at %04X", uniScalar);
                if (++numWarnings == 40) {
                    puts("\nNo further warnings of this type will be mentioned");
                }
            }

            if (lenCPchar == 0) {
                if (numWarnings < 40 && !iconv_error_fromUTF8) {
                    printf("\nNo conversion done for %04X", uniScalar);
                    if (++numWarnings == 40) {
                        puts("\nNo further warnings of this type will be mentioned");
                    }
                }
            }
            else {
                /* codePageName --> UTF-8 */
                if (!iconv_error_fromUTF8) {
                    convertChar(UTF8name, toUTF8, codePageName, CPcharWithShiftState, 
                        lenCPcharWithShiftState, UTF8name, endUTF8char, &lenEndUTF8char);
                }
                else if (uniScalar < 0xFF) {
                    printf("\nerror for %04X", uniScalar);
                }

                analyzeCharMappingAndWriteOneLine(uniScalar, galleyChar2, galleyChar3, 
                    startUTF8char, lenStartUTF8char, CPchar, lenCPchar, CPcharWithShiftState, 
                    lenCPcharWithShiftState, endUTF8char, lenEndUTF8char, fp);
            }
        }
        iconv_close(toCP);
        iconv_close(toUTF8);
        writeFooter(fp);
        fclose(fp);

        fprintf(stdout, "\tfinished argv[%i]\n", i);
    }
    return (0);
}

