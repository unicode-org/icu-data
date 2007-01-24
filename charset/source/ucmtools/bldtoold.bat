@rem Copyright (C) 2003-2007, International Business Machines Corporation and others. All Rights Reserved.
@rem build one of the ucmtools with debug libraries
@rem assume CVS hierarchy with charset and icu modules parallel
@rem pass the tool source file as an argument
cl -nologo -MDd -I..\..\..\..\icu\source\common -I..\..\..\..\icu\source\tools\toolutil -D_CRT_SECURE_NO_DEPRECATE -D_CRT_NONSTDC_NO_DEPRECATE %1 -link /LIBPATH:..\..\..\..\icu\lib icuucd.lib icutud.lib
