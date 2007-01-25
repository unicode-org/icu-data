@rem Copyright (C) 2003-2007, International Business Machines Corporation and others. All Rights Reserved.
rem @echo off
rem java -verbosegc -classpath CharMapMetricTool.jar MappingTableComparisonManager
del *.class
javac MappingTableComparisonManager.java
jar cvf CharMapMetricTool.jar *.class
java -classpath CharMapMetricTool.jar MappingTableComparisonManager
rem java -classpath CharMapMetricTool.jar UserInterfaceBuilder
pause