@rem Copyright (C) 2003, International Business Machines Corporation and others. All Rights Reserved.
@rem build one of the ucmtools with debug libraries
@rem assume CVS hierarchy with charset and icu modules parallel
@rem pass the tool source file as an argument
cl -nologo -MDd -I..\..\..\icu\source\common -I..\..\..\icu\source\tools\toolutil %1 -link /LIBPATH:..\..\..\icu\lib icuucd.lib icutud.lib
