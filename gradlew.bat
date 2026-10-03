@echo off
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if defined JAVA_HOME set JAVACMD=%JAVA_HOME%\bin\java.exe
if not defined JAVACMD set JAVACMD=java.exe
"%JAVACMD%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
