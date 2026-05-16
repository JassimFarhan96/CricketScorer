# poi-android already ships its own -keep rules via the ":proguard"
# companion artifact (com.github.SUPERCILEX.poi-android:proguard:3.17),
# so we mainly need warning suppressions here for the AWT and Java-11
# references that POI's source code mentions but the transpiled bytecode
# never executes on Android.

-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.x2000.**
-dontwarn javax.xml.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.security.auth.**
-dontwarn com.graphbuilder.**
-dontwarn com.microsoft.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**
