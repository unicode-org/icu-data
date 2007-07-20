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
The history file is read via the constructor, updated via getFilename(), and
intended to then be written via writeHistoryFile()
*/
class FilenameMappingHistory {
public:
    static FilenameMappingHistory *create(const char *filename, UErrorCode *status);

    /*
    Based on rpmap filename, tpmap filename and year, generate an appropriate
    UTS#22 filename. The results are stored in rptp-history.txt. If that file
    contains a pre-existing filename, that will be returned. If another rpmap
    filename and tpmap filename combination generates a conflicting UTS#22 filename,
    then a longer (newer) more unique filename will be returned instead.
    */
    const char *getFilename(const char *rpmapFilename, const char *tpmapFilename, uint16_t year, UErrorCode *status);

    /*
    Save the generated names to a file so that we can generate the same UTS#22
    filename when this tool is run again at a future date.
    */
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
