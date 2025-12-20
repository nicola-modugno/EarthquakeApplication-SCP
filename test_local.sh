#!/bin/bash

# Script per testare il progetto in locale
# Usage: ./test-local.sh

set -e

echo "===================================="
echo "Local Testing - Earthquake Analysis"
echo "===================================="
echo ""

# Verifica prerequisiti
if ! command -v sbt &> /dev/null; then
    echo "Error: sbt not found. Please install SBT first."
    exit 1
fi

if ! command -v spark-submit &> /dev/null; then
    echo "Error: spark-submit not found. Please install Apache Spark first."
    exit 1
fi

# Compila il progetto
echo "Step 1: Compiling project..."
sbt clean compile
echo "Compilation successful."
echo ""

# Crea il JAR
echo "Step 2: Creating assembly JAR..."
sbt assembly
echo "JAR created."
echo ""

# Verifica che esista un file di test
TEST_FILE="earthquakes-small.csv"
if [ ! -f "$TEST_FILE" ]; then
    echo "Creating sample test file..."
    cat > $TEST_FILE << 'EOL'
latitude,longitude,date
37.502,15.312,2024-03-12 02:10
38.112,13.372,2024-03-12 05:55
37.521,15.324,2024-03-12 04:32
37.547,15.323,2024-04-01 14:22
38.147,13.324,2024-04-01 21:55
37.535,15.341,2024-04-03 12:32
38.142,13.387,2024-04-03 18:33
43.769,11.255,2024-04-03 21:10
37.522,15.308,2024-04-23 02:10
38.123,13.379,2024-04-27 05:55
EOL
    echo "Sample file created: $TEST_FILE"
fi
echo ""

# Esegui test locale
echo "Step 3: Running local test..."
OUTPUT_DIR="output-local-test"

# Rimuovi output precedente
rm -rf $OUTPUT_DIR

# Esegui con spark-submit
spark-submit \
    --class Main \
    --master local[*] \
    --driver-memory 2g \
    target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar \
    $TEST_FILE \
    $OUTPUT_DIR \
    4

echo ""
echo "Test completed!"
echo ""

# Mostra risultati
echo "===================================="
echo "Results:"
echo "===================================="
if [ -d "$OUTPUT_DIR" ]; then
    cat $OUTPUT_DIR/part-* 2>/dev/null || echo "No output files found"
else
    echo "Output directory not found"
fi
echo ""

echo "Output saved in: $OUTPUT_DIR"
echo ""

# Test con dataset di dimensioni medie (se esiste)
MEDIUM_FILE="earthquakes-medium.csv"
if [ -f "$MEDIUM_FILE" ]; then
    echo "Found medium dataset, running additional test..."
    OUTPUT_MEDIUM="output-medium-test"
    rm -rf $OUTPUT_MEDIUM
    
    spark-submit \
        --class Main \
        --master local[*] \
        --driver-memory 4g \
        target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar \
        $MEDIUM_FILE \
        $OUTPUT_MEDIUM \
        8
    
    echo "Medium dataset test completed!"
    echo "Results in: $OUTPUT_MEDIUM"
fi

echo ""
echo "All local tests completed successfully!"
