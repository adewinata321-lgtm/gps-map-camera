@if "%DEBUG%" == "" @echo off
setlocal
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_HOME=C:\Users\Ade Winata\.gradle\wrapper\dists\gradle-8.5-bin\5t9huq95ubn472n8rpzujfbqh\gradle-8.5"
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
