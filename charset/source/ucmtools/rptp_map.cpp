/*
*******************************************************************************
*   Copyright (C) 2007-2007, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*/

#include "rptp_map.h"
#include "uvector.h"

#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>

typedef enum {
    ITEM_UTS22_NAME,
    ITEM_RMAP_NAME,
    ITEM_TMAP_NAME,
    ITEM_COUNT
} LineTypes;

struct FilenameMapping {
    char *uts22Name;
    char *rmapName;
    char *tmapName;
    FilenameMapping(const char *uts22, const char *rmap, const char *tmap) :
        uts22Name(strdup(uts22)), rmapName(strdup(rmap)), tmapName(strdup(tmap))
    {
    }
    ~FilenameMapping() {
        free(uts22Name);
        free(rmapName);
        free(tmapName);
    }
};

U_CDECL_BEGIN
static void U_EXPORT2
freeFilenameMapping(void *obj) {
    delete (FilenameMapping*)obj;
}

static int8_t U_EXPORT2
sortByUTS22(const UHashTok key1, const UHashTok key2) {
    FilenameMapping* item1 = (FilenameMapping*)key1.pointer;
    FilenameMapping* item2 = (FilenameMapping*)key2.pointer;
    return strcmp(item1->uts22Name, item2->uts22Name);
}

static int8_t U_EXPORT2
compareByUTS22(const UHashTok key1, const UHashTok key2) {
    return sortByUTS22(key1, key2) == 0;
}

static UBool U_EXPORT2
compareFilenameMapping(const UHashTok key1, const UHashTok key2) {
    FilenameMapping* item1 = (FilenameMapping*)key1.pointer;
    FilenameMapping* item2 = (FilenameMapping*)key2.pointer;
    return strcmp(item1->rmapName, item2->rmapName) == 0 && strcmp(item1->tmapName, item2->tmapName) == 0;
}
U_CDECL_END

/* Simple uppercase a string */
static char *toUpperStr(char *str) {
    char *origStr = str;
    while (*str) {
        *str = toupper(*str);
        str++;
    }
    return origStr;
}

/* Trim off all line endings. */
static char *trim(char *str) {
    int32_t lastIdx = (int32_t)(strlen(str) - 1);
    while (lastIdx > 0 && (str[lastIdx] == '\r' || str[lastIdx] == '\n')) {
        str[lastIdx--] = 0;
    }
    return str;
}

FilenameMappingHistory::FilenameMappingHistory() :
    throwAway(U_ZERO_ERROR),
    sortedByResult(NULL, compareByUTS22, throwAway),
    origFilename(NULL)
{
}
FilenameMappingHistory::~FilenameMappingHistory() {
    if (origFilename) {
        free(origFilename);
    }
}

void FilenameMappingHistory::addItem(UVector *vect, FilenameMapping *key, UErrorCode *status) {
    vect->addElement(key, *status);
    hashByRmap.put(key->rmapName, vect, *status);
    sortedByResult.sortedInsert((void*)key, sortByUTS22, *status);
}

FilenameMappingHistory *FilenameMappingHistory::create(const char *filename, UErrorCode *status) {
    FilenameMappingHistory *retVal = new FilenameMappingHistory();
    FILE *file = fopen(filename, "r");
    int32_t lineNum = 1;
    char line[1024];
    char *str, *prevStr;
    char *itemStr[ITEM_COUNT];

    if (file == NULL) {
        *status = U_FILE_ACCESS_ERROR;
        return NULL;
    }
    retVal->origFilename = strdup(filename);

    while (fgets(line, sizeof(line), file) != NULL) {
        *status = U_ZERO_ERROR;
        trim(line);
        if (line[0] != '#' && line[0] != 0) {
            prevStr = line;
            str = prevStr;
            for (int32_t idx = ITEM_UTS22_NAME; idx < ITEM_COUNT; idx++) {
                str = strchr(str, ',');
                // Make sure we parse the last field. The comma is in between fields.
                if (idx < ITEM_COUNT-1) {
                    if (str == NULL) {
                        fprintf(stderr, "Parse error for history file on line %d", lineNum);
                        *status = U_PARSE_ERROR;
                        return NULL;
                    }
                    str[0] = 0;
                }
                //fprintf(stderr, "%s", prevStr);
                itemStr[idx] = prevStr;
                if (idx < ITEM_COUNT-1) {
                    // Get ready to parse the next item on the line.
                    str++;
                    prevStr = str;
                }
            }
            //fprintf(stderr, "%s,%s,%s\n", itemStr[ITEM_UTS22_NAME], itemStr[ITEM_RMAP_NAME], itemStr[ITEM_TMAP_NAME]);
            FilenameMapping *item = new FilenameMapping(itemStr[ITEM_UTS22_NAME],
                    toUpperStr(itemStr[ITEM_RMAP_NAME]),
                    toUpperStr(itemStr[ITEM_TMAP_NAME]));
            const UHashElement *elem = retVal->hashByRmap.find(itemStr[ITEM_RMAP_NAME]);
            UVector *vect;
            if (elem == NULL) {
                // New mapping
                vect = new UVector(freeFilenameMapping, compareFilenameMapping, *status);
            }
            else {
                // Mapping conflict for the RPMAP. We will have to be careful about this in the future.
                vect = (UVector*)(elem->value.pointer);
                if (vect->contains(item)) {
                    fprintf(stderr, "Duplicate R?MAP/T?MAP combination in history file on line %d\n", lineNum);
                    *status = U_PARSE_ERROR;
                    return NULL;
                }
            }
            if (retVal->sortedByResult.indexOf(item) >= 0) {
                fprintf(stderr, "Duplicate result in history file on line %d\n", lineNum);
                *status = U_PARSE_ERROR;
                return NULL;
            }
            retVal->addItem(vect, item, status);
        }
        lineNum++;
    }
    fclose(file);
    return retVal;
}

void FilenameMappingHistory::writeHistoryFile(UErrorCode *status) {
    int32_t pos = -1;
    FILE *file = fopen(origFilename, "w");
    if (file == NULL) {
        *status = U_FILE_ACCESS_ERROR;
        return;
    }

    fprintf(file, "# This file was machine generated by the rptp2ucm tool\n");
    for (int32_t idx = 0; idx < sortedByResult.size(); idx++) {
        FilenameMapping *item = (FilenameMapping *)sortedByResult.elementAt(idx);
        fprintf(file, "%s,%s,%s\n", item->uts22Name, item->rmapName, item->tmapName);
    }
    fclose(file);
}

U_CFUNC UBool getCCSIDValues(uint32_t value, uint16_t *unicodeCCSID, uint16_t *ccsid) {
    *unicodeCCSID = 0;
    *ccsid = 0;
    /* is this really a Unicode conversion table? - get the CCSID */
    *unicodeCCSID=value&0xffff;
    if(*unicodeCCSID==13488
        || *unicodeCCSID==17584)
    {
        *ccsid = (uint16_t)(value>>16);
    }
    else {
        *unicodeCCSID=value>>16;
        if(*unicodeCCSID==13488 /* Unicode 2.0, UTF-16BE with IBM PUA */
            || *unicodeCCSID==17584 /* Unicode 3.0, UTF-16BE with IBM PUA */
            || *unicodeCCSID==1200 /* UTF-16BE with IBM PUA */
            || *unicodeCCSID==1232 /* UTF-32BE with IBM PUA */
            || *unicodeCCSID==21680 /* Unicode 4.0, UTF-16BE with IBM PUA */
            || *unicodeCCSID==61956 /* UTF-16BE with Microsoft HKSCS-Big 5 PUA */
            )
        {
            *ccsid = (uint16_t)(value&0xffff);
        } else {
            return FALSE;
        }
    }
    return TRUE;
}

static const char *getUnicodeSuffix(uint16_t unicode) {
    switch (unicode) {
    case 13488:
        return "_U2"; /* Unicode 2.0 */
    case 17584:
        return "_U3"; /* Unicode 3.0 */
    case 21680:
        return "_U4"; /* Unicode 4.0 */
    /*case 25776:
        return "_U4.1";*/ /* Not used */
    case 61956:
        return "_MS"; /* Microsoft PUA extensions */
    }
    return "";
}

static char *generateFileName(const char *rpmapFilename, const char *tpmapFilename, uint16_t year, UBool useOldFormat) {
    char filename[1024];
    char *s = NULL;
    uint32_t value = strtoul(rpmapFilename, &s, 16);
    uint16_t unicode, ccsid;
    int32_t length;

    getCCSIDValues(value, &unicode, &ccsid);
    length=sprintf(filename, "ibm-%u_", ccsid);
    filename[length++]=toupper(rpmapFilename[10]);  /* P or X */
    filename[length++]=toupper(rpmapFilename[14]);  /* last 3 suffix characters */
    filename[length++]=toupper(rpmapFilename[15]);
    filename[length++]=toupper(rpmapFilename[16]);
    if (!useOldFormat) {
        filename[length++]='_';
        filename[length++]=toupper(tpmapFilename[10]);  /* P or X */
        filename[length++]=toupper(tpmapFilename[14]);  /* last 3 suffix characters */
        filename[length++]=toupper(tpmapFilename[15]);
        filename[length++]=toupper(tpmapFilename[16]);
    }
    length+=sprintf(filename+length, "-%d", year);
    if (!useOldFormat) {
        strcat(filename, getUnicodeSuffix(unicode));
    }
    strcat(filename, ".ucm");
    return strdup(filename);
}

const char *FilenameMappingHistory::getFilename(const char *rmapFilename, const char *tmapFilename, uint16_t year, UErrorCode *status) {
    const char *retVal = NULL;
    UVector *vect = NULL;
    UBool useOldNameFormat = TRUE;
    int idx;
    const UHashElement *elem;
    char *rmapFilenameDup = toUpperStr(strdup(rmapFilename));
    char *tmapFilenameDup = toUpperStr(strdup(tmapFilename));

    rmapFilename = rmapFilenameDup;
    tmapFilename = tmapFilenameDup;

    elem = hashByRmap.find(rmapFilename);
    if (elem != NULL) {
        FilenameMapping tempVal("", rmapFilename, tmapFilename);
        // We already know about this mapping table. Get the old value.
        vect = (UVector*)(elem->value.pointer);
        idx = vect->indexOf(&tempVal);
        if (idx >= 0) {
            FilenameMapping *prevItem = (FilenameMapping*)vect->elementAt(idx);
            retVal = prevItem->uts22Name;
            free(rmapFilenameDup);
            free(tmapFilenameDup);
            return retVal;
        }
        // else More than one TMAP is available.

        // This RPMAP has multiple choices, and it's new.
        useOldNameFormat = FALSE;
    }
    else {
        // New mapping table. Store information for future reference.
        vect = new UVector(freeFilenameMapping, compareFilenameMapping, *status);
    }
    // We didn't find this name. Make up a new one.
    char *fileNameDup = generateFileName(rmapFilename, tmapFilename, year, useOldNameFormat);
    FilenameMapping *item = new FilenameMapping(fileNameDup, rmapFilename, tmapFilename);

    // Double check that we haven't generated this name in the past.
    idx = sortedByResult.indexOf(item);
    if (idx >= 0) {
        FilenameMapping *foundItem = (FilenameMapping *)sortedByResult.elementAt(idx);
        if (strcmp(foundItem->rmapName, rmapFilename) != 0 || strcmp(foundItem->tmapName, tmapFilename) != 0) {
            free(fileNameDup); // Another Unicode CCSID conflicts with this table, or some other conflict.
            useOldNameFormat = FALSE;
            fileNameDup = generateFileName(rmapFilename, tmapFilename, year, useOldNameFormat);
            item->uts22Name = fileNameDup;
        }
    }
    else {
        free(fileNameDup); // filename was already copied.
    }
    addItem(vect, item, status);

    free(rmapFilenameDup);
    free(tmapFilenameDup);
    return retVal;
}
