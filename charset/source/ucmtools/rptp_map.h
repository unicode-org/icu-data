/*
*******************************************************************************
*   Copyright (C) 2007-2007, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*/

#include "unicode/utypes.h"
#include "hash.h"
#include "uvector.h"

struct FilenameMapping;

/*
This class allows the rptp2ucm tool to remember what combination of files generated which UCM file.
*/
class FilenameMappingHistory {
public:
    static FilenameMappingHistory *create(const char *filename, UErrorCode *status);

    const char *getFilename(const char *rpmapFilename, const char *tpmapFilename, uint16_t year, UErrorCode *status);

    void writeHistoryFile(UErrorCode *status);

    ~FilenameMappingHistory();

private:
    FilenameMappingHistory();

    void addItem(UVector *vect, FilenameMapping *key, UErrorCode *status);

    UErrorCode throwAway;
    Hashtable hashByRmap;
    UVector sortedByResult;
    char *origFilename;
};

U_CFUNC UBool getCCSIDValues(uint32_t value, uint16_t *unicodeCCSID, uint16_t *ccsid);
