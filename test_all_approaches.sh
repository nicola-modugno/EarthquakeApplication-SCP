#!/bin/bash

# Script per testare tutti e tre gli approcci in locale
# Verifica che tutti producano lo stesso risultato

set -e

echo "=========================================="
echo "Test Locale - Tre Approcci"
echo "=========================================="
echo ""

# Verifica prerequisiti
if ! command -v sbt &> /dev/null; then
    echo "Error: sbt not found"
    exit 1
fi

if ! command -v spark-submit &> /dev/null; then
    echo "Error: spark-submit not found"
    exit 1
fi

# Compila
echo "Step 1: Compilazione..."
sbt clean compile
echo "✓ Compilazione completata"
echo ""

echo "Step 2: Creazione JAR..."
sbt assembly
echo "✓ JAR creato"
echo ""

# File di test
TEST_FILE="earthquakes-sample.csv"

# Verifica o crea file di test
if [ ! -f "$TEST_FILE" ]; then
    echo "Creazione file di test..."
    cat > $TEST_FILE << 'EOL'
latitude,longitude,date
37.502,15.312,2024-03-12 02:10:00
38.112,13.372,2024-03-12 05:55:00
37.521,15.324,2024-03-12 04:32:00
37.547,15.323,2024-04-01 14:22:00
38.147,13.324,2024-04-01 21:55:00
37.535,15.341,2024-04-03 12:32:00
38.142,13.387,2024-04-03 18:33:00
43.769,11.255,2024-04-03 21:10:00
37.522,15.308,2024-04-23 02:10:00
38.123,13.379,2024-04-27 05:55:00
37.498,15.289,2024-03-12 23:45:00
38.156,13.401,2024-04-01 03:15:00
37.543,15.337,2024-04-03 16:20:00
40.234,14.567,2024-05-10 08:30:00
40.287,14.523,2024-05-10 10:45:00
EOL
    echo "✓ File di test creato: $TEST_FILE"
fi
echo ""

# Array approcci
declare -a APPROACHES=("groupbykey" "aggregatebykey" "reducebykey")
declare -a APPROACH_NAMES=("GroupByKey" "AggregateByKey" "ReduceByKey")

JAR="target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar"
RESULTS_FILE="test-results-comparison.txt"

echo "Step 3: Esecuzione dei tre approcci..."
echo ""
echo "Approach           | Time (s) | Output File"
echo "-------------------|----------|------------------"

# Pulisci risultati precedenti
> $RESULTS_FILE

for i in "${!APPROACHES[@]}"; do
    approach=${APPROACHES[$i]}
    approach_name=${APPROACH_NAMES[$i]}
    output_dir="output-test-$approach"
    
    # Rimuovi output precedente
    rm -rf $output_dir
    
    # Esegui test
    start_time=$(date +%s)
    
    spark-submit \
        --class Main \
        --master local[*] \
        --driver-memory 2g \
        $JAR \
        $TEST_FILE \
        $output_dir \
        4 \
        $approach \
        > /dev/null 2>&1
    
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    
    # Salva output
    if [ -d "$output_dir" ]; then
        result=$(cat $output_dir/part-* 2>/dev/null || echo "ERROR")
        echo "=== $approach_name ===" >> $RESULTS_FILE
        echo "$result" >> $RESULTS_FILE
        echo "" >> $RESULTS_FILE
    fi
    
    printf "%-18s | %8s | %-20s\n" "$approach_name" "$duration" "$output_dir"
done

echo ""
echo "Step 4: Verifica consistenza risultati..."
echo ""

# Estrai solo le prime righe (coppia di località) da ogni output
pair1=$(cat output-test-groupbykey/part-* 2>/dev/null | head -1)
pair2=$(cat output-test-aggregatebykey/part-* 2>/dev/null | head -1)
pair3=$(cat output-test-reducebykey/part-* 2>/dev/null | head -1)

echo "Risultati:"
echo "  GroupByKey:     $pair1"
echo "  AggregateByKey: $pair2"
echo "  ReduceByKey:    $pair3"
echo ""

# Verifica che siano uguali
if [ "$pair1" = "$pair2" ] && [ "$pair2" = "$pair3" ]; then
    echo "✓ SUCCESSO: Tutti e tre gli approcci producono lo stesso risultato!"
    echo ""
    echo "Coppia trovata: $pair1"
    
    # Conta le date
    num_dates=$(cat output-test-groupbykey/part-* 2>/dev/null | tail -n +2 | wc -l)
    echo "Numero di co-occorrenze: $num_dates"
    
    echo ""
    echo "Date (prime 5):"
    cat output-test-groupbykey/part-* 2>/dev/null | tail -n +2 | head -5
    
    exit 0
else
    echo "✗ ERRORE: Gli approcci producono risultati diversi!"
    echo ""
    echo "Dettagli completi in: $RESULTS_FILE"
    exit 1
fi
