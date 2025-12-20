@echo off
REM Script per testare in locale su Windows

echo ==========================================
echo Test Locale - Earthquake Analysis
echo ==========================================
echo.

REM Verifica se esiste il JAR
if not exist "target\scala-2.12\earthquake-cooccurrence-assembly-1.0.jar" (
    echo ERRORE: JAR non trovato!
    echo Esegui prima: sbt assembly
    pause
    exit /b 1
)

REM Test con approccio 1 (GroupByKey)
echo Test 1: GroupByKey con Hash partitioner
echo.
spark-submit ^
  --class Main ^
  --master "local[*]" ^
  --driver-memory 2g ^
  target\scala-2.12\earthquake-cooccurrence-assembly-1.0.jar ^
  test-data.csv ^
  output-test-1 ^
  4 ^
  groupbykey ^
  hash ^
  1

if errorlevel 1 (
    echo.
    echo ERRORE: Test fallito!
    pause
    exit /b 1
)

echo.
echo ==========================================
echo Test completato con successo!
echo ==========================================
echo.
echo Risultati in: output-test-1
echo.

REM Mostra risultato
if exist "output-test-1\part-00000" (
    echo Risultato:
    type output-test-1\part-00000
    echo.
) else (
    echo ATTENZIONE: File output non trovato
)

if exist "output-test-1\metrics\part-00000" (
    echo Metriche:
    type output-test-1\metrics\part-00000
    echo.
)

pause