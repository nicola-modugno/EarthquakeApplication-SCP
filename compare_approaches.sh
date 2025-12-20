#!/bin/bash

# Script per confrontare i tre approcci su Google Cloud DataProc
# Usage: ./compare-approaches.sh <bucket-name> <input-file> <num-workers>

set -e

if [ "$#" -ne 3 ]; then
    echo "Usage: $0 <bucket-name> <input-csv-file> <num-workers>"
    echo "Example: $0 my-bucket earthquakes-full.csv 2"
    exit 1
fi

BUCKET_NAME=$1
INPUT_FILE=$2
NUM_WORKERS=$3
REGION="europe-west1"
PROJECT_JAR="target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar"
CLUSTER_NAME="earthquake-cluster-${NUM_WORKERS}w"

# Calcola numero di partizioni (workers × 4 cores)
NUM_PARTITIONS=$((NUM_WORKERS * 4))

echo "=========================================="
echo "Confronto Approcci - Earthquake Analysis"
echo "=========================================="
echo "Bucket: $BUCKET_NAME"
echo "Input: $INPUT_FILE"
echo "Workers: $NUM_WORKERS"
echo "Partitions: $NUM_PARTITIONS"
echo ""

# Verifica JAR
if [ ! -f "$PROJECT_JAR" ]; then
    echo "Error: JAR not found. Run 'sbt assembly' first."
    exit 1
fi

# Upload files
echo "Step 1: Uploading files to GCS..."
gsutil cp $PROJECT_JAR gs://$BUCKET_NAME/jars/
gsutil cp $INPUT_FILE gs://$BUCKET_NAME/data/
echo "✓ Upload complete"
echo ""

# Crea cluster
echo "Step 2: Creating DataProc cluster..."
gcloud dataproc clusters create $CLUSTER_NAME \
    --region=$REGION \
    --num-workers $NUM_WORKERS \
    --master-boot-disk-size 240 \
    --worker-boot-disk-size 240 \
    --master-machine-type=n2-standard-4 \
    --worker-machine-type=n2-standard-4 \
    --quiet

echo "✓ Cluster created: $CLUSTER_NAME"
echo ""

# Array con i tre approcci
declare -a APPROACHES=("groupbykey" "aggregatebykey" "reducebykey")
declare -a APPROACH_NAMES=("GroupByKey" "AggregateByKey" "ReduceByKey")

# File per salvare i risultati
RESULTS_FILE="comparison-${NUM_WORKERS}workers.csv"
echo "approach,workers,partitions,time_seconds" > $RESULTS_FILE

INPUT_PATH="gs://$BUCKET_NAME/data/$(basename $INPUT_FILE)"

# Esegui ogni approccio
for i in "${!APPROACHES[@]}"; do
    approach=${APPROACHES[$i]}
    approach_name=${APPROACH_NAMES[$i]}
    
    echo "=========================================="
    echo "Testing Approach $((i+1))/3: $approach_name"
    echo "=========================================="
    
    OUTPUT_PATH="gs://$BUCKET_NAME/output/${NUM_WORKERS}w-${approach}"
    
    # Rimuovi output precedente
    gsutil -m rm -rf $OUTPUT_PATH 2>/dev/null || true
    
    # Esegui job e misura tempo
    start_time=$(date +%s)
    
    echo "Submitting Spark job..."
    gcloud dataproc jobs submit spark \
        --cluster=$CLUSTER_NAME \
        --region=$REGION \
        --jar=gs://$BUCKET_NAME/jars/earthquake-cooccurrence-assembly-1.0.jar \
        -- $INPUT_PATH $OUTPUT_PATH $NUM_PARTITIONS $approach
    
    end_time=$(date +%s)
    duration=$((end_time - start_time))
    
    echo "✓ Completed in $duration seconds"
    echo "$approach,$NUM_WORKERS,$NUM_PARTITIONS,$duration" >> $RESULTS_FILE
    echo ""
    
    # Scarica risultato
    mkdir -p results/${NUM_WORKERS}w-${approach}
    gsutil -m cp -r $OUTPUT_PATH/* results/${NUM_WORKERS}w-${approach}/ 2>/dev/null || true
done

# Elimina cluster
echo "Step 3: Deleting cluster..."
gcloud dataproc clusters delete $CLUSTER_NAME \
    --region=$REGION \
    --quiet
echo "✓ Cluster deleted"
echo ""

# Mostra risultati
echo "=========================================="
echo "COMPARISON RESULTS"
echo "=========================================="
echo ""
cat $RESULTS_FILE
echo ""

# Analisi risultati
echo "Detailed Analysis:"
echo ""

# Leggi baseline (primo approccio)
baseline_time=$(awk -F',' 'NR==2 {print $4}' $RESULTS_FILE)

echo "Approach           | Time (s) | Speedup | Relative Performance"
echo "-------------------|----------|---------|---------------------"

while IFS=',' read -r approach workers partitions time; do
    if [ "$approach" != "approach" ]; then
        speedup=$(echo "scale=2; $baseline_time / $time" | bc)
        percentage=$(echo "scale=1; 100 * $time / $baseline_time" | bc)
        printf "%-18s | %8s | %7s | %6s%%\n" "$approach" "$time" "$speedup" "$percentage"
    fi
done < $RESULTS_FILE

echo ""
echo "Baseline: ${APPROACH_NAMES[0]} = ${baseline_time}s"
echo "Results saved in: $RESULTS_FILE"
echo "Output files in: ./results/"
echo ""
echo "=========================================="
echo "RECOMMENDATIONS"
echo "=========================================="
echo ""

# Trova l'approccio più veloce
fastest_approach=$(tail -n +2 $RESULTS_FILE | sort -t',' -k4 -n | head -1 | cut -d',' -f1)
fastest_time=$(tail -n +2 $RESULTS_FILE | sort -t',' -k4 -n | head -1 | cut -d',' -f4)

echo "✓ Fastest approach: $fastest_approach ($fastest_time seconds)"
echo ""
echo "Analysis:"
echo "- GroupByKey: Simple but most memory-intensive (highest shuffling)"
echo "- AggregateByKey: Best for large datasets (reduced shuffling)"
echo "- ReduceByKey: Balanced approach (good for counting operations)"
echo ""
echo "For production with large datasets, use: $fastest_approach"
echo ""
