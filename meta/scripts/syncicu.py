#!/usr/local/bin/python
# Copyright (C) 2013 IBM Corporation and Others. All Rights Reserved.
# (depends on CLDR ticket #6375 in CLDR trunk)
# depends on ../db/trac.db being the TRAC sqlite3 db
#
#
# Purpose:  read ICU and CLDR metadata, and update trac's "version" database
#
# usage:
# $ svn co http://unicode.org/repos/cldr/trunk/common common
# $ svn co http://source.icu-project.org/repos/icu/data/trunk/meta/xml icuxml
# $ python syncicu.py

import sqlite3
import xml.etree.ElementTree as ET
import dateutil.parser

vers = {}

print 'reading ICU'
tree = ET.parse('icuxml/icumeta.xml')
root = tree.getroot()
prods = root.find('icuProducts')
for icuProduct in prods.findall('icuProduct'):
    whichProduct = icuProduct.get('type')
    if whichProduct not in ['icu4c','icu4j']:
        continue
    releases = icuProduct.find('releases')
    for release in releases.findall('release'):
        version = release.get('version')
        for date in release.find('dates').findall('date'):
            if date.get('type') == 'ga':
                dateStr = date.get('date')
                #print "OK:",whichProduct,version,dateStr
                vers["icu-%s" % version] = dateStr


print 'reading CLDR'
ctree = ET.parse('common/supplemental/cldrInfo.xml')
croot = ctree.getroot()
for version in croot.findall('version'):
    ver = version.get('version')
    for release in version.findall('release'):
        if release.get('type') == 'ga':
            #if release.get('tentative') == 'true':
            #    print "Skipping tentative release CLDR %s" % ver
            #    continue
            dateStr = release.get('date')
            vers["cldr-%s" % ver] = dateStr

conn = sqlite3.connect('../db/trac.db')
c = conn.cursor()



for version in vers:
    vtime = dateutil.parser.parse(vers[version]).strftime('%s000000')
    sql = "INSERT INTO version VALUES('%s', %s, '')" % (version, vtime)
    print sql
    c.execute("delete from version where name='%s'" % version)
    c.execute(sql)

conn.commit()
conn.close()


# CREATE TABLE version (
#         name text PRIMARY KEY,
#             time integer,
#             description text
#         );
# sqlite> select * from version limit 4
#    ...> ;
#    icu-2.0|-57600|
#    icu-2.0.1|-57600|
#    icu-2.0.2|-57600|
#    icu-2.0.3|-57600|
#    sqlite> select * from version order by name desc limit 4;
#    icu-49.1|1332356400|
#    icu-4.8.1|1311188400|
#    icu-4.8|1306350000|
#    icu-4.5.2|0|
   
