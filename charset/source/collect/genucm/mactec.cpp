#include <stdlib.h>
#include <stdio.h>
#include <time.h>
#include "convert.h"


#define MAX_UNICODE 0x10FFFF
#define MACOS_VERSION "10.1"

#define GET_VARIANT_NUM(num) ((num & 0xffff0000) >> 16)

static const char *OSStatus2Str(OSStatus status) {
    static char unknownName[80];
    switch (status) {
    case kTextUnsupportedEncodingErr: return "kTextUnsupportedEncodingErr";
    case kTextMalformedInputErr: return "kTextMalformedInputErr";
    case kTextUndefinedElementErr: return "kTextUndefinedElementErr";
    case kTECMissingTableErr: return "kTECMissingTableErr";
    case kTECTableChecksumErr: return "kTECTableChecksumErr";
    case kTECTableFormatErr: return "kTECTableFormatErr";
    case kTECCorruptConverterErr: return "kTECCorruptConverterErr";
    case kTECNoConversionPathErr: return "kTECNoConversionPathErr";
    case kTECBufferBelowMinimumSizeErr: return "kTECBufferBelowMinimumSizeErr";
    case kTECArrayFullErr: return "kTECArrayFullErr";
    case kTECPartialCharErr: return "kTECPartialCharErr";
    case kTECUnmappableElementErr: return "kTECUnmappableElementErr";
    case kTECIncompleteElementErr: return "kTECIncompleteElementErr";
    case kTECDirectionErr: return "kTECDirectionErr";
    case kTECGlobalsUnavailableErr: return "kTECGlobalsUnavailableErr";
    case kTECItemUnavailableErr: return "kTECItemUnavailableErr";
    case kTECUsedFallbacksStatus: return "kTECUsedFallbacksStatus";
    case kTECNeedFlushStatus: return "kTECNeedFlushStatus";
    case kTECOutputBufferFullStatus: return "kTECOutputBufferFullStatus";
    }
    sprintf(unknownName, "%d", status);
    return unknownName;
}

static char *p2c(Str255 ps)
{
    char *s;
    s = (char*)ps;
    s[s[0]+1]=0;
    return(s+1);
}

char *getEncodingName(char *buffer, TextEncoding encoding) {
    Str255 encodingName;
//    OSStatus status;
    char numBuffer[255];
    char *name = numBuffer;
    int nameIdx;

//    status = TECGetTextEncodingInternetName(encoding, encodingName);
//    name = p2c(encodingName);
//    if (name[0] == 0) {
        // We have no name
    sprintf(name, "%d", encoding & 0xFFFF);
    if (GET_VARIANT_NUM(encoding) != 0) {
        sprintf(name+strlen(name), "_%X", GET_VARIANT_NUM(encoding));
    }
    // Make the name UTR-22 compatible
//    for (nameIdx = strlen(name); nameIdx >= 0; nameIdx--) {
//        if (name[nameIdx] == '-') {
//            name[nameIdx] = '_';
//        }
//    }
    sprintf(buffer, "macos-%s-%s", name, MACOS_VERSION);
    return buffer;
}

converter::converter(cp_id cp, encoding_info *enc_info)
{
    OSStatus macstatus;
    TextEncoding utf16Encoding = CreateTextEncoding(kTextEncodingUnicodeDefault, kTextEncodingDefaultVariant, kUnicode16BitFormat);
    m_err = 0;
    m_enc_num = cp;
    m_enc_info = enc_info;
    m_tecFromUnicode = NULL;
    m_tecToUnicode = NULL;
    sprintf(m_name, "%d", cp & 0xFFFF);
    if (GET_VARIANT_NUM(cp) != 0) {
        sprintf(m_name+strlen(m_name), "_%X", GET_VARIANT_NUM(cp));
    }
    macstatus = TECCreateConverter(&m_tecFromUnicode, utf16Encoding, cp);
    if (macstatus != noErr) {
        printf("Error %s:%d %d\n", __FILE__, __LINE__, macstatus);
        m_err = 1;
    }
//  TODO move flags to from_unicode/to_unicode
//    macstatus = TECSetBasicOptions(m_tecFromUnicode, kUnicodeUseFallbacksBit);
    if (macstatus != noErr) {
        printf("Error %s:%d %d\n", __FILE__, __LINE__, macstatus);
        m_err = 1;
    }

    macstatus = TECCreateConverter(&m_tecToUnicode, cp, utf16Encoding);
    if (macstatus != noErr) {
        printf("Error %s:%d %d\n", __FILE__, __LINE__, macstatus);
        m_err = 1;
    }
//  TODO move flags to from_unicode/to_unicode
//    macstatus = TECSetBasicOptions(m_tecToUnicode, kUnicodeUseFallbacksBit);
    if (macstatus != noErr) {
        printf("Error %s:%d %d\n", __FILE__, __LINE__, macstatus);
        m_err = 1;
    }
    strcpy(m_cp_inf.default_char, get_default_char(&m_cp_inf.default_uchar));
}

converter::~converter()
{
    OSStatus macstatus;
    if (m_tecFromUnicode != NULL) {
        macstatus = TECDisposeConverter(m_tecFromUnicode);
        if (macstatus != noErr) {
            printf("Error %s:%d %d\n", __FILE__, __LINE__, OSStatus2Str(macstatus));
        }
    }
    if (m_tecToUnicode != NULL) {
        macstatus = TECDisposeConverter(m_tecToUnicode);
        if (macstatus != noErr) {
            printf("Error %s:%d %d\n", __FILE__, __LINE__, OSStatus2Str(macstatus));
        }
    }
    m_tecFromUnicode = NULL;
    m_tecToUnicode = NULL;
}

int converter::get_supported_encodings(UVector *p_encodings, UHashtable *p_map_encoding_info,
                                   int argc, const char* const argv[])

{
    OSStatus macstatus;
    ItemCount numberEncodings = 0;
    TextEncoding *availableEncodings;
    Str255 encodingName;
    char description[100];
    char *web_charset_name;


    macstatus = TECCountAvailableTextEncodings(&numberEncodings);
    availableEncodings = (TextEncoding *)malloc(numberEncodings * sizeof(TextEncoding *));
    macstatus = TECGetAvailableTextEncodings(availableEncodings, numberEncodings, &numberEncodings);

    while (numberEncodings-- > 0)
    {
        UErrorCode status = U_ZERO_ERROR;
        UBool collectEncoding = TRUE;

        TextEncoding currEncoding = availableEncodings[numberEncodings];

        macstatus = TECGetTextEncodingInternetName(currEncoding, encodingName);
        web_charset_name = p2c(encodingName);
        if (web_charset_name[0] == 0) {
            sprintf(web_charset_name, "%X", currEncoding);
        }
        encoding_info *pEncoding = new encoding_info;
        strcpy(pEncoding->web_charset_name, web_charset_name);
        strcpy(pEncoding->charset_description, "");
        printf("%X -> %s\n", currEncoding, web_charset_name);

        //look for match for converter name/code page number on the command line
        for(int32_t j = 0; j < argc; j++)
        {
            collectEncoding = FALSE;
            if (0 != strstr(web_charset_name, argv[j]))
            {
                collectEncoding = TRUE;
            }
            else
            {
                cp_id converter_match = 0;
                sscanf(argv[j], "%d", &converter_match);
                if (converter_match == currEncoding)
                {
                    collectEncoding = TRUE;
                }
            }
        }
        if (!collectEncoding)
        {
            continue;
        }

        p_encodings->addElement(currEncoding, status);
        uhash_iput(p_map_encoding_info, currEncoding, pEncoding, &status);
        if (U_FAILURE(status)) {
            printf("Error %s:%d %s", __FILE__, __LINE__, u_errorName(status));
        }

    }
    free(availableEncodings);
    return 0;
}

size_t converter::from_unicode(char* target, char* target_limit, const UChar* source, const UChar* source_limit, unsigned int flags)
{
#ifdef U_DARWIN
#if 0
    CFStringRef strRef;
    Boolean fromUniResult;
    size_t srcSize, targ_size_bytes;

    srcSize = source_limit - source;
    targ_size_bytes = target_limit - target;

    strRef = CFStringCreateWithCharacters(kCFAllocatorDefault, source, source_limit - source);
    fromUniResult = CFStringGetCString(strRef, target, targ_size_bytes, m_enc_num);
    if (fromUniResult && strRef)
    {
        targ_size_bytes = strlen(target);
    }
    else
    {
        targ_size_bytes = 0;
    }

    return (size_t)targ_size_bytes;
#endif
    OSStatus macstatus;
    size_t srcSize, targSize;
    size_t targFlushSize = 0;

//    srcSize = (source_limit - source)/sizeof(UChar);
//    srcSize = (u_strlen(source)+1)/sizeof(UChar);
//    srcSize = 4;
    srcSize = (u_strlen(source)+(*source == 0))*sizeof(UChar);
    targSize = target_limit - target;

//    if (*source < 0xFF) {
//        printf("<U%X><U%X> s=%d t=%d\n", source[0], source[1], srcSize, targSize);
//    }
    macstatus = TECConvertText(m_tecFromUnicode,
                               (const UInt8*)source, srcSize, &srcSize,
                               (UInt8*)target, targSize, &targSize);
//    if (*source < 0xFF) {
//        printf("\\x%X\\x%X s=%d t=%d\n", (uint8_t)target[0], (uint8_t)target[1], srcSize, targSize);
//    }
    if (macstatus != noErr) {
        TECFlushText(m_tecFromUnicode, (UInt8*)target, target_limit - target, &targFlushSize);
        targSize = 0;
        /* TODO Is this correct for SBCS */
        if (macstatus != kTECPartialCharErr && macstatus != kTextMalformedInputErr) {
            printf("Error %s:%d %s\n", __FILE__, __LINE__, OSStatus2Str(macstatus));
        }
    }
    else {
        targFlushSize = ((target_limit - target) * sizeof(target[0])) - targSize;
        TECFlushText(m_tecFromUnicode, (UInt8*)target+targSize, targFlushSize, &targFlushSize);
        if (macstatus != noErr) {
            targSize = 0;
            /* TODO Is kTECPartialCharErr correct for SBCS */
//            if (macstatus != kTECPartialCharErr) {
                printf("Error %s:%d %s\n", __FILE__, __LINE__, OSStatus2Str(macstatus));
//            }
        }
        else {
             targSize += targFlushSize;
             if (targFlushSize) {
                   printf("flush cp_id=%X <U%X><U%X> \\x%X\\x%X s=%d t=%d\n",
                           m_enc_num, (uint16_t)source[0], (uint16_t)source[1],
                           (uint8_t)target[0], (uint8_t)target[1],
                           srcSize, targSize);
             }
        }
    }
//    if (*source < 0xFF) {
//        printf("\\x%X\\x%X s=%d t=%d\n", (uint8_t)target[0], (uint8_t)target[1], srcSize, targSize);
//    }
//    printf("target = %X subchar = %X\n", target[0], (uint8_t)m_cp_inf.default_char[0]);
    if (targSize > 0
        && (flags & CONVERTER_USE_DEF_CHAR) != CONVERTER_USE_DEF_CHAR
        && target[0] == m_cp_inf.default_char[0])
    {
        // We got a substitution character and we didn't ask for it.
        printf("skip\n");
        targSize = 0;
    }
    return (size_t)targSize;
#endif
}

size_t converter::to_unicode(UChar* target, UChar* target_limit, const char* source, const char* source_limit)
{
#ifdef U_DARWIN
#if 0
    CFStringRef strRef;
    size_t src_size, targ_size;
    CFRange range;

    src_size = source_limit - source;
    targ_size = 0;
    range.location = 0;
    range.length = target_limit - target;

    strRef = CFStringCreateWithBytes(NULL, (const UInt8 *)source, src_size, m_enc_num, TRUE);

    if (strRef)
    {
        CFStringGetCharacters(strRef, range, target);
        targ_size = CFStringGetLength(strRef) + (source == 0);
    }
    return targ_size;
#endif
    OSStatus macstatus;
    size_t srcSize, targSize;
    size_t targFlushSize = 0;

    srcSize = strlen(source)+1;
    targSize = (target_limit - target)*sizeof(UChar);

    macstatus = TECConvertText(m_tecToUnicode,
                               (const UInt8*)source, srcSize, &srcSize,
                               (UInt8*)target, targSize, &targSize);
    if (macstatus != noErr) {
        targSize = 0;
        if (macstatus != kTextMalformedInputErr && target[0] != 0xFFFD) {
            /* kTextMalformedInputErr means bad input. Probably from probing */
            printf("Error %s:%d %s\n", __FILE__, __LINE__, OSStatus2Str(macstatus));
//            printf("to <U%X><U%X> \\x%X\\x%X s=%d t=%d\n", (uint16_t)target[0], (uint16_t)target[1], (uint8_t)source[0], (uint8_t)source[1], srcSize, targSize);
        }
    }
    else {
        targFlushSize = ((target_limit - target) * sizeof(target[0])) - targSize;
        TECFlushText(m_tecToUnicode, (UInt8*)target+targSize, targFlushSize, &targFlushSize);
        if (macstatus != noErr) {
            targSize = 0;
            /* TODO Is kTECPartialCharErr correct for SBCS */
//            if (macstatus != kTECPartialCharErr) {
                printf("Error %s:%d %s\n", __FILE__, __LINE__, OSStatus2Str(macstatus));
//            }
        }
        else {
             targSize += targFlushSize;
             if (targFlushSize) {
                   printf("flush cp_id=%X <U%X><U%X> \\x%X\\x%X s=%d t=%d\n",
                           m_enc_num, (uint16_t)source[0], (uint16_t)source[1],
                           (uint8_t)target[0], (uint8_t)target[1],
                           srcSize, targSize);
             }
        }
    }
//    if (*source < 0x7F) {
//        printf("to <U%X><U%X> \\x%X\\x%X s=%d t=%d\n", (uint16_t)target[0], (uint16_t)target[1], (uint8_t)source[0], (uint8_t)source[1], srcSize, targSize);
//    }
    return (size_t)targSize;
#endif
}

char *
converter::get_default_char(UChar *default_uchar)
{
    static char buff1[80];
    UChar ubuff[8];
    UChar* source;
    size_t num_bytes1;

    ubuff[0] = 0xfffd;
    source = ubuff;

    // Is there a mapping from 0xfffd (Unicode subchar)?
    num_bytes1 = from_unicode(buff1, buff1+sizeof(buff1), source, source+1, CONVERTER_USE_SUBST_CHAR);

    buff1[num_bytes1] = 0;
    if (num_bytes1 > 0)
    {
        *default_uchar = ubuff[0];
    }
    else
    {
        ubuff[0] = 0x001a;

        // Is there at least a mapping from 0x001A (ASCII subchar)?
        num_bytes1 = from_unicode(buff1, buff1+sizeof(buff1), source, source+1, CONVERTER_USE_SUBST_CHAR);

        buff1[num_bytes1] = 0;
        if (num_bytes1 > 0)
        {
            *default_uchar = ubuff[0];
        }
        else
        {
            fprintf(stderr, "warning: <subchar> not found!\n");
            *default_uchar = 0;
        }
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
    if (m_enc_num == 0x101 /* UNICODE-1-1 */
      || m_enc_num == 0x4000101/*UNICODE-1-1-UTF-7*/
      || m_enc_num == 0x8000101/*UNICODE-1-1-UTF-8*/
      || m_enc_num == 0x103 /*UNICODE-2-0*/
      || m_enc_num == 0x20103/*A form of UNICODE-2-0*/
      || m_enc_num == 0x4000103/*UNICODE-2-0-UTF-7*/
      || m_enc_num == 0x8000103/*UNICODE-2-0-UTF-8*/
      || m_enc_num == 0x104/*UNICODE-3-0*/
      || m_enc_num == 0x20104/*A form of UNICODE-3-0*/
      || m_enc_num == 0x30104
      || m_enc_num == 0x80104
      || m_enc_num == 0x90104
      || m_enc_num == 0x4000104/*UNICODE-3-0-UTF-7*/
      || m_enc_num == 0x8000104/*UNICODE-3-0-UTF-8*/
      || m_enc_num == 0x106/*UNICODE-3-2*/
      || m_enc_num == 0x20106/*A form of UNICODE-3-2*/
      || m_enc_num == 0x30106
      || m_enc_num == 0x80106
      || m_enc_num == 0x90106
      || m_enc_num == 0x4000106/*UNICODE-3-2-UTF-7*/
      || m_enc_num == 0x8000106/*UNICODE-3-2-UTF-8*/
      || m_enc_num == 1586/*gb18030*/
      || m_enc_num == 0xFFF)
    {
        return TRUE;
    }
    return strstr(m_enc_info->web_charset_name, "ISO-2022") != NULL;

}

const char *
converter::get_premade_state_table() const
{
    static const struct encoding_state_table_entry {
        int32_t cp;
        const char *state_table;
    } encoding_state_table[] = {
        {1057,   // cp936
        "<icu:state>                   0-7f, 81-fe:1\n"
        "<icu:state>                   40-7e, 80-fe\n"
        },
        {1059,   // cp950
        "<icu:state>                   0-7f, 81-fe:1\n"
        "<icu:state>                   40-7e, 80-fe\n"
        },
        {2563,   // Big5
        "<icu:state>                   0-7f, a1-fe:1\n"
        "<icu:state>                   40-7e, a1-fe\n"
        },
        {2566,   // Big5_HKSCS based on ibm-950
        "<icu:state>                   0-7f, 81-fe:1, 88-8b:2, 8d-a0:2, c8:2, fa-fe:2\n"
        "<icu:state>                   40-7e, a1-fe\n"
        "<icu:state>                   40-7e.p, a1-a0, a1-fe.p\n"
        }
    };
    const int state_table_size = sizeof(encoding_state_table)/sizeof(encoding_state_table_entry);

    for (int idx = 0; idx < state_table_size; idx++) {
        if (m_enc_num == encoding_state_table[idx].cp) {
            return encoding_state_table[idx].state_table;
        }
    }

    return NULL;
}

const char *
converter::get_OS_vendor()
{
#ifdef U_DARWIN
    return "macos";
#endif
}

const char *
converter::get_OS_interface()
{
#ifdef U_DARWIN
    return "TECCreateConverter";
#endif
}

const char *
converter::get_OS_variant()
{
#ifdef U_DARWIN
    return "10.2";
#endif
}


