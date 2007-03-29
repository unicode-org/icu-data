// Copyright (c) 2007 IBM and Others. All Rights Reserved

This directory contains build files for eclipse ICU4J plug-in data update
feature patch.  See the instruction below to set up the build environment.

1. Install the target version of eclipse SDK in the system.

2. Copy icu_data_patch.properties.template to icu_data_patch.properties,
then set property values.

3. Run the ant script with a property eclipse.dir=<eclipse SDK directory>.
For example, if you extracted eclipse SDK zip/tar file into
C:\eclipse-SDK-3.2.2-win32, then -

> ant -Declipse.dir=C:\eclipse-SDK-3.2.2-win32\eclipse

4. Output files will be in out/e<eclipse.version>_<icu.patch.version>.
The ant build create update site for testing in updatesite directory
under the build output directory.  If you want to test the patch, you can
point the update site directory and install the patch.


ICU team release instruction

1. After you verified the patch, update icu_data_patch.properties and
explicitly set next two properties -

icu.patch.version
icu.copyright.year

2. Build the patch again with the updated properties file.  If you are
satisfied with the version, then create a sub-directory under versions.
For example, e322_3.4.5.20070308_2007c.  The naming rule is -

e<eclipse.version>_<icu.version>.<build.date>_<tz.version>

Copy icu_data_patch.properties to the directory, so you can recreate the
same patch later.

Also copy the directory 
out/e<eclipse.version>_<icu.version>.<build.date>_<tz.version>/updatesite
to the directory above.
