@echo off
set JAVA_OPTS=-XX:TieredStopAtLevel=1
.\gradlew.bat :client:run --args="hw-accel=OFF" ^
  -x :client:pmdMain ^
  -x :client:pmdTest ^
  -x :client:checkstyleMain ^
  -x :client:checkstyleTest ^
  -x :client:javadoc
