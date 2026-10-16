@echo off
setlocal
cd /d "%~dp0"
echo ==========================================
echo RectMiner - Minecraft 1.7.10 Forge
echo ==========================================
where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java no esta instalado o no esta en PATH.
  pause
  exit /b 1
)
java -version 2>&1 | findstr /C:"1.8." >nul
if errorlevel 1 (
  echo ERROR: Esta version antigua de ForgeGradle requiere normalmente Java 8.
  echo Configura JAVA_HOME/PATH para usar JDK 8 antes de compilar.
  pause
  exit /b 1
)
where gradle >nul 2>nul
if errorlevel 1 (
  echo ERROR: No se encuentra Gradle en PATH.
  echo Instala Gradle 2.14.1 y vuelve a ejecutar este archivo.
  pause
  exit /b 1
)
gradle --version
echo.
echo Compilando RectMiner...
gradle --no-daemon build
if errorlevel 1 (
  echo.
  echo ERROR: La compilacion fallo. Copia el mensaje de error completo.
  pause
  exit /b 1
)
echo.
echo Compilacion completada. Busca el JAR en build\libs\
dir /b build\libs\*.jar
pause
