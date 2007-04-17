// Copyright (c) 2007 IBM and Others. All Rights Reserved

This directory contains build files for eclipse ICU4J plug-in data update
feature patch.  See the instruction below to set up the build environment.

1. Install the target version of eclipse SDK in the system.

2. Copy icu_data_patch.properties.template to icu_data_patch.properties,
then set required property values.

3. Run the ant script with a property eclipse.dir=<eclipse SDK directory>.
For example, if you extracted eclipse SDK zip/tar file into
C:\eclipse-SDK-3.2.2-win32, then -

> ant -Declipse.dir=C:\eclipse-SDK-3.2.2-win32\eclipse

4. Output files will be in out/<patch.feature.id>_<patch.version>-<tz.version>.
The ant build create update site for testing in updatesite directory
under the build output directory.  If you want to test the patch, you can
point the update site directory and install the patch.


ICU team release instruction

1. Contact eclipse release engineering team and tell them ICU team wants to
release a new patch.  They will give you a new RCP patch number.  Set the
number to patch.number property in icu_data_patch.properties.

Note: Eclipse release engineering team has a naming convention for patches.
ICU4J plug-in is currently included in rcp feature and a patch for the feature
in eclipse 3.2.2 stream is defined as - 

org.rcp.eclipse.rcp.patch<patch#>_3.2.2_v<build_date>_322

You should always check with them to see if there is no updates with this
naming convention.

2. Make sure the target feature, version and other properties are properly
set in icu_data_patch.properties.

3. Run the ant target to create a patch.  After you verified the patch, update
icu_data_patch.properties and explicitly set next two properties -

copyright.year
build.date.str
patch.version
icu.patch.plugin.version

4. Build the patch again with the updated properties file.  If you are
satisfied with the version, then create a sub-directory under versions.
For example, org.eclipse.rcp.patch2_3.2.2.v20070417_322-2007e.
The naming rule is -

<patch.feature.id>_<patch.version>-<tz.version>

Copy icu_data_patch.properties to the directory, so you can recreate the
same patch later.

Also copy the directory 
out/<patch.feature.id>_<patch.version>-<tz.version>/updatesite
to the directory above.
