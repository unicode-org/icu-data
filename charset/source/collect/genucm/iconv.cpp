/*
*******************************************************************************
*
*   Copyright (C) 2000-2005, International Business Machines
*   Corporation and others.  All Rights Reserved.
*
*******************************************************************************
* Oddities that you will find while using the iconv interface.
*
* 
*/
#include "convert.h"
#include "unicode/ustring.h"
#include <stdio.h>
#include <stdlib.h>

/* The definition of iconv varies by platform, and this helps fix the cast */
#if defined(U_SOLARIS)
#define ICONV_SRC_PARAM_CAST (const char **)
#else
#define ICONV_SRC_PARAM_CAST (char **)
#endif

static void
writeByteSeqInHex(/* IN */ FILE *fp,
                  /* IN */ const char* byteSeq,
                  /* IN */ int lenToWrite)
{
    int j;

    for (j = 0; j < lenToWrite; j++) {
        fprintf(fp, "\\x%02X", (uint8_t)byteSeq[j]);
    }
}

UBool errorOnOpen(/* IN */ iconv_t errFlag)
{
    if (errFlag == (iconv_t)-1) {
        if (errno == EMFILE) {
            fprintf(stdout, "too many system file descriptors open!\n");
        } else if (errno == ENFILE) {
            fprintf(stdout, "too many system files currently open!\n");
        } else if (errno == ENOMEM) {
            fprintf(stdout, "not enough system storage space available!\n");
        } else if (errno == EINVAL) {
            fprintf(stdout, "code conversion specified not supported by this system!\n");
        } else {
            fprintf(stdout, "undefined error\n");
        }
        return TRUE;
    }
    return FALSE;
}


#define UTF8_NAME "UTF-8"

int
converter::get_supported_encodings(UVector *p_encodings,
                                   UHashtable *p_map_encoding_info,
                                   int argc, const char* const argv[])
{
#if defined(U_AIX)
    static const char cmd[] = "/bin/ls /usr/lib/nls/loc/uconvTable/";
#elif defined(U_LINUX)
    //static const char cmd[] = "locale -m";
    static const char cmd[] = "grep 'module.*INTERNAL.*//' /usr/lib/gconv/gconv-modules | grep -o '[^[:space:]]*//' | cut -d / -f 1 | sort";
#elif defined(U_SOLARIS)
    static const char cmd[] = "/bin/ls -F /usr/lib/iconv/UTF-8%*.so | fgrep -v '@' | cut -d '%' -f 2 | sed 's/\\.so\\*//'";
#elif defined(U_HPUX)
    static const char cmd[] = "/bin/ls /usr/lib/nls/iconv/tables.1/ucs2=* | cut -d = -f 2";
#endif
    static char buf[4096] = "UTF-8";
    size_t n;
    FILE *p;

    p = popen(cmd, "r");

    if (p == NULL)
    {
        fprintf(stderr, "error using popen to get the encodings\n");
        return -1;
    }

    n = fread(buf, sizeof(char), sizeof(buf), p);
    pclose(p);

    if (n >= sizeof(buf) - 1)
    {
        fprintf(stderr, "Found too many encodings\n");
        return -1;
    }

    char *currTok = strtok(buf, "\n");
    char *nextTok;
    int32_t tokLen;
    while (currTok != NULL)
    {
        UErrorCode status = U_ZERO_ERROR;

        nextTok = strtok(NULL, "\n");
        if (nextTok != NULL) {
            tokLen = (int32_t)(nextTok - currTok) - 1;
        }
        else {
            tokLen = strlen(currTok);
        }
        encoding_info *pEncoding = new encoding_info;
        memset(pEncoding, 0, sizeof(encoding_info));
        strncpy(pEncoding->web_charset_name, currTok, tokLen);

        p_encodings->addElement(pEncoding->web_charset_name, status);
        uhash_put(p_map_encoding_info, pEncoding->web_charset_name, pEncoding, &status);
        if (U_FAILURE(status)) {
            printf("Error %s:%d %s\n", __FILE__, __LINE__, u_errorName(status));
            return -1;
        }

        currTok = nextTok;
    }
    return 0;
}

converter::converter(cp_id cp, encoding_info *enc_info)
:   m_err(0),
    m_enc_num(cp),
    m_enc_info(enc_info),
    toCP((iconv_t)-1),
    toUTF8((iconv_t)-1)
{
    strcpy(m_name, enc_info->web_charset_name);

    toCP = iconv_open(cp, UTF8_NAME);
    if (errorOnOpen(toCP)) {
        printf("Error %s:%d %s\n", __FILE__, __LINE__, cp);
        m_err = -1;
    }
    else
    {
        /* get a codePageName --> UTF-8 conversion descriptor */
        toUTF8 = iconv_open(UTF8_NAME, cp);
        if (errorOnOpen(toUTF8)) {
            printf("Error %s:%d %s\n", __FILE__, __LINE__, cp);
            m_err = -1;
            // toCP will be cleaned up in destructor
        }
    }
}

converter::~converter()
{
    if (toCP != (iconv_t)-1)
    {
        iconv_close(toCP);
    }
    if (toUTF8 != (iconv_t)-1)
    {
        iconv_close(toUTF8);
    }
}

size_t converter::from_unicode(char* target, char* target_limit, const UChar*
source, const UChar* source_limit, unsigned int flags)
{
    char buff_UTF8[80];
    char *targ_ptr = buff_UTF8;
    char *src_ptr;
    size_t src_left = 0;
    size_t targ_left = sizeof(buff_UTF8);
    int32_t targ_len = 0;
    size_t targ_size;
    size_t ret_val;
    UErrorCode status = U_ZERO_ERROR;

    /* reset to initial state, which is needed by Solaris */
    iconv(toCP, NULL, &src_left, &targ_ptr, &targ_left);

    /* Convert from UTF-16 -> UTF-8 */
    u_strToUTF8(buff_UTF8, sizeof(buff_UTF8), &targ_len,
                source, source_limit - source, &status);
    if (U_FAILURE(status)) {
        printf("Error %s:%d %s\n", __FILE__, __LINE__, u_errorName(status));
        return 0;
    }
    buff_UTF8[targ_len] = 0;

    targ_ptr = target;
    targ_left = target_limit - target;
    src_ptr = buff_UTF8;
    src_left = (size_t)targ_len;

    /* do the actual conversion here */
    ret_val = iconv(toCP, ICONV_SRC_PARAM_CAST &src_ptr, &src_left, &targ_ptr, &targ_left);

    targ_size = sizeof(buff_UTF8) - targ_left;
    if ((size_t)-1 == ret_val)
    {
        if (!((flags & CONVERTER_USE_SUBST_CHAR) && errno == EILSEQ)) {
            targ_size = 0;
        }
        if (errno == EINVAL) {
            printf("EINVAL error: incomplete character or shift sequence "
                "at the end of the input buffer!\n");
        }
        else if (errno == E2BIG) {
            printf("E2BIG error:  lack of space in output buffer!\n");
        }
        else if (errno == EILSEQ) {
            //printf("EILSEQ error:  byte sequence does not belong to input code set!\n");
        }
        else if (errno == EBADF) {
            printf("EBADF error:  invalid conversion descriptor!\n");
        }
        else {
            printf("undefined error %s\n", strerror(errno));
        }
    }
/*    else {
        printf("targ_len=%d targ_size=%d src=%X buff_UTF8[0]=%X targ[0]=%X targ_left=%d\n",
                targ_len, targ_size, (uint16_t)source[0],
                (uint8_t)buff_UTF8[0], (uint8_t)*targ_ptr, targ_left);
    }*/
    target[targ_size] = 0;

    return targ_size;
}

size_t converter::to_unicode(UChar* target, UChar* target_limit, const char*
source, const char* source_limit)
{
    char buff_UTF8[80];
    char *targ_ptr = buff_UTF8;
    char *src_ptr;
    size_t src_left = 0;
    size_t targ_left = sizeof(buff_UTF8);
    int32_t targ_len = 0;
    size_t targ_size;
    size_t ret_val;
    UErrorCode status = U_ZERO_ERROR;

    /* reset to initial state, which is needed by Solaris */
    iconv(toUTF8, NULL, &src_left, &targ_ptr, &targ_left);

    targ_ptr = buff_UTF8;
    targ_left = sizeof(buff_UTF8);
    src_ptr = (char *)source;
    src_left = strlen(source) + (0 == source[0]);

    /* do the actual conversion here */
    ret_val = iconv(toUTF8, ICONV_SRC_PARAM_CAST &src_ptr, &src_left, &targ_ptr, &targ_left);

    targ_size = sizeof(buff_UTF8) - targ_left;
    if ((size_t)-1 == ret_val)
    {
//        writeByteSeqInHex(stdout, source, strlen(source));
//        printf(" failed\n");
        targ_size = 0;
        if (errno == EINVAL)
        {
//            printf("EINVAL error: incomplete character or shift sequence "
//                "at the end of the input buffer!");
        }
        else if (errno == E2BIG)
        {
            printf("E2BIG error:  lack of space in output buffer!\n");
        }
        else if (errno == EILSEQ)
        {
//            printf("EILSEQ error:  byte sequence does not belong to input code set!\n");
        }
        else if (errno == EBADF) {
            printf("EBADF error:  invalid conversion descriptor!\n");
        }
        else
        {
            printf("undefined error\n");
        }
    }
/*    else {
        printf("targ_len=%d targ_size=%d src=%X buff_UTF8[0]=%X targ[0]=%X targ_left=%d\n",
                targ_len, targ_size, (uint16_t)source[0],
                (uint8_t)buff_UTF8[0], (uint8_t)*targ_ptr, targ_left);
    }*/
    buff_UTF8[targ_size] = 0;

    /* Convert from UTF-8 -> UTF-16 */
    u_strFromUTF8(target, target_limit - target, &targ_len,
                buff_UTF8, targ_size, &status);
    if (U_FAILURE(status)) {
        writeByteSeqInHex(stdout, buff_UTF8, targ_size);
        printf(" Error %s:%d %s\n", __FILE__, __LINE__, u_errorName(status));
        return 0;
    }
    target[targ_len] = 0;

    return targ_len;
}

char *
converter::get_default_char(UChar *default_uchar)
{
    static const UChar primarySubst[1] = {0xFFFD};
    static const UChar secondarySubst[1] = {0x1A};
    static char buff1[80];
    size_t num_bytes1, num_bytes2;

    *default_uchar = 0;
    buff1[0] = 0;

    // use converter's own default char
    num_bytes1 = from_unicode(buff1, buff1+80, primarySubst, primarySubst+1, CONVERTER_USE_SUBST_CHAR);

    if (num_bytes1 == 0) {
        // do not return a default char - string unconvertable characters
        buff1[0] = 0;
        num_bytes2 = from_unicode(buff1, buff1+80, secondarySubst, secondarySubst+1, CONVERTER_USE_SUBST_CHAR);
        if (num_bytes2 != 0) {
            *default_uchar = secondarySubst[0];
        }
        else {
            puts("substitution not found");
        }
    }
    else {
        *default_uchar = primarySubst[0];
    }

    return buff1;
}

int
converter::get_cp_info(cp_id cp, cp_info& cp_inf)
{
    strcpy(cp_inf.default_char, get_default_char(&cp_inf.default_uchar));
    return 0;
}

UBool 
converter::is_lead_byte_probeable() const
{
    return TRUE;
}

UBool 
converter::is_ignorable() const
{
    return strcmp(m_enc_info->web_charset_name, "GB18030") == 0
        || strcmp(m_enc_info->web_charset_name, "gb18030") == 0
        || strcmp(m_enc_info->web_charset_name, "ISO_10646") == 0
        || strcmp(m_enc_info->web_charset_name, "UNICODE") == 0
        || strncmp(m_enc_info->web_charset_name, "UTF-", 4) == 0
        || strncmp(m_enc_info->web_charset_name, "UCS-", 4) == 0
        // TODO Collect stateful encodings.
        || strncmp(m_enc_info->web_charset_name, "ISO-2022", 8) == 0
        || strncmp(m_enc_info->web_charset_name, "iso2022", 7) == 0
        || strcmp(m_enc_info->web_charset_name, "HZ-GB-2312") == 0
        || strcmp(m_enc_info->web_charset_name, "ko_KR.iso2022-7") == 0
        || strcmp(m_enc_info->web_charset_name, "zh_CN.iso2022-7") == 0
        || strcmp(m_enc_info->web_charset_name, "zh_CN.iso2022-CN") == 0
        || strcmp(m_enc_info->web_charset_name, "zh_TW-iso2022-7") == 0
        || strcmp(m_enc_info->web_charset_name, "zh_TW.iso2022-7") == 0
        || strcmp(m_enc_info->web_charset_name, "IBM930") == 0
        || strcmp(m_enc_info->web_charset_name, "IBM933") == 0
        || strcmp(m_enc_info->web_charset_name, "IBM935") == 0
        || strcmp(m_enc_info->web_charset_name, "IBM937") == 0
#if defined(U_LINUX)
        // TODO glibc 2.3.3 has a bug with IBM939 and converting \uFFFF
        || strstr(m_enc_info->web_charset_name, "IBM939") != 0
#endif
        || strcmp(m_enc_info->web_charset_name, UTF8_NAME) == 0;
}

const char *
converter::get_premade_state_table() const
{
    if (strcmp(m_enc_num, "PCK") == 0
        || strcmp(m_enc_num, "CP932") == 0
        || strcmp(m_enc_num, "IBM943") == 0) {
        return /* Shift-JIS */
        // TODO Is a0 a valid single byte?
        "<icu:state>                   0-7f, 81-9f:1, a0-df, e0-fc:1\n"
        "<icu:state>                   40-7e, 80-fc\n";
    }
    else if (strcmp(m_enc_num, "eucJP") == 0) {
        return
        "<icu:state>                   0-8d, 8e:2, 8f:3, 90-9f, a1-fe:1\n"
        "<icu:state>                   a1-fe\n"
        "<icu:state>                   a1-e4\n"
        "<icu:state>                   a1:4, a2-fe:1\n"
        "<icu:state>                   a1-fe.u\n";
    }
    else if (strcmp(m_enc_num, "zh_TW-big5") == 0) {
        return
        "<icu:state>                   0-7f, 81-fe:1\n"
        "<icu:state>                   40-7e, 80-fe\n";
    }
    return NULL;
}

const char *
converter::get_OS_vendor()
{
#ifdef U_LINUX
    return "glibc";
#elif defined(U_AIX)
    return "aix";
#elif defined(U_SOLARIS)
    return "solaris";
#elif defined(U_HPUX)
    return "hpux";
#endif
}

const char *
converter::get_OS_variant()
{
#ifdef U_LINUX
    static const char cmd[] = "rpm -q glibc";
    static char buf[80] = "";
    static char *ptr = NULL;
    size_t n;
    FILE *p;

    if (ptr == NULL)
    {
        memset(buf, 0, sizeof(buf));
        p = popen(cmd, "r");

        if (p == NULL)
        {
            fprintf(stderr, "error using popen to get the OS_variant\n");
            return "";
        }

        n = fread(buf, sizeof(char), sizeof(buf), p);
        pclose(p);

        if (n >= sizeof(buf) - 1)
        {
            fprintf(stderr, "Line too long to get the variant\n");
            return "";
        }
        ptr = strchr(buf, '-')+1;
        char *dash = strchr(ptr, '\n');
        if(dash)
        {
            *dash=0;
        }
        dash = strchr(ptr, '-');
        if (dash)
        {
            *dash = 0;
        }
    }
    return ptr;
#elif defined(U_AIX)
    static const char v[] = "uname -v";
    static const char r[] = "uname -r";
    static char ver[80] = "";
    static char rel[80] = "";
    static char buf[80] = "";
    size_t n;
    FILE *p;

    memset(ver, 0, sizeof(ver));
    memset(rel, 0, sizeof(rel));
    p = popen(v, "r");

    if (p == NULL)
    {
        fprintf(stderr, "error using popen to get the AIX version\n");
        return "";
    }

    n = fread(ver, sizeof(char), sizeof(ver), p);
    pclose(p);

    p = popen(r, "r");

    if (p == NULL)
    {
        fprintf(stderr, "error using popen to get the AIX release\n");
        return "";
    }

    n = fread(rel, sizeof(char), sizeof(rel), p);
    pclose(p);

    strcpy( buf, ver );
    if(buf[strlen(buf)-1]=='\n')
        buf[strlen(buf)-1]=0;

    strcat( buf, "." );
    strcat( buf, rel );
    if(buf[strlen(buf)-1]=='\n')
        buf[strlen(buf)-1]=0;

    return buf;
#elif defined(U_HPUX) || defined(U_SOLARIS)
    static const char r[] = "uname -r";
    static char ver[80] = "";
    char *ptr = ver;
    size_t n;
    FILE *p;

    memset(ver, 0, sizeof(ver));
    p = popen(r, "r");

    if (p == NULL)
    {
        fprintf(stderr, "error using popen to get the HP-UX release\n");
        return "";
    }

    n = fread(ver, sizeof(char), sizeof(ver), p);
    pclose(p);

    if(ver[strlen(ver)-1]=='\n')
        ver[strlen(ver)-1]=0;

    if(strncmp(ver, "B.", 2) == 0) {
        ptr = ver + 2;
    }

    return ptr;
#endif
}

const char *
converter::get_OS_interface()
{
#ifdef U_LINUX
    static char buf[80];
    strcpy(buf, "Linux with glibc ");
    strcat(buf, get_OS_variant());
    return buf;
#elif defined(U_AIX)
    return "AIX with iconv";
#elif defined(U_SOLARIS)
    return "Solaris with iconv";
#elif defined(U_HPUX)
    return "HP-UX with iconv";
#endif
}

