@rem Gradle 8.8 wrapper launcher
@echo off
set DIRNAME=%~dp0
if "%JAVA_HOME%"=="" goto usePath
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
goto execute
:usePath
set JAVA_EXE=java.exe
:execute
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradlew -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
