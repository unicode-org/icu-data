This directory contains information on the character set conversions of several
different platforms between Unicode and any given encoding.

Known problems for the UCM files:
* The UCM files may require some tweaking in order to get them to work with ICU
* The mappings for the stateful and MBCS encodings may not be correct for the
    non ibm-*.ucm files.  The program for collecting such mappings is still not
    complete.
* Some of the MBCS and stateful character sets are currently missing the
    <icu:state> and <uconv_class> tags.
* Some files are missing the substitution character.

Known problems for the XML files:
* The validity tables for these files are preliminary, and they are based
    upon the set of known validity tables that ICU already knows about.
* The XML format cannot express a stateful character set mapping at this time.

Updated by George Rhoten,   April 18, 2001
