#!/bin/bash

# Script per automatizzare gli esperimenti su Google Cloud DataProc
# Usage: ./run-experiments.sh <bucket-name> <input-file>

set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <bucket-name> <input-csv-file>"
    exit 1
fi

BUCKET_NAME=$1
INPUT_FILE=$2
REGION="europe-west1"
PROJECT_JAR="target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar"

echo "===================================="
echo "Earthquake Analysis - Experiments"
echo "===================================="
echo "Bucket: $BUCKET_NAME"
echo "Input file: $INPUT_FILE"
echo "Region: $REGION"
echo ""

# Verifica che il JAR esista
if [ ! -f "$PROJECT_JAR" ]; then
    echo "Error: JAR file not found. Please run 'sbt assembly' first."
    exit 1
fi

# Upload JAR e dataset su GCS
echo "Step 1: Uploading JAR and dataset to GCS..."
gsutil cp $PROJECT_JAR gs://$BUCKET_NAME/jars/
gsutil cp $INPUT_FILE gs://$BUCKET_NAME/data/
echo "Upload complete."
echo ""

# Configurazioni da testare
declare -a WORKERS=(2 3 4)
declare -a PARTITIONS=(8 12 16)

# Funzione per creare cluster
create_cluster() {
    local num_workers=$1
    local cluster_name="earthquake-cluster-$num_workers"
    
    echo "Creating cluster: $cluster_name with $num_workers workers..."
    
    gcloud dataproc clusters create $cluster_name \
        --region=$REGION \
        --num-workers $num_workers \
        --master-boot-disk-size 240 \
        --worker-boot-disk-size 240 \
        --master-machine-type=n2-standard-4 \
        --worker-machine-type=n2-standard-4 \
        --quiet
    
    echo "Cluster $cluster_name created."
}

# Funzione per eseguire job
run_job() {
    local num_workers=$1
    local num_partitions=$2
    local cluster_name="earthquake-cluster-$num_workers"
    local output_path="gs://$BUCKET_NAME/output/workers-${num_workers}-partitions-${num_partitions}"
    local input_path="gs://$BUCKET_NAME/data/$(basename $INPUT_FILE)"
    
    echo "Running job on $cluster_name with $num_partitions partitions..."
    echo "Output will be saved to: $output_path"
    
    # Rimuovi output precedente se esiste
    gsutil -m rm -rf $output_path 2>/dev/null || true
    
    # Submit job e salva il tempo di inizio
    local start_time=$(date +%s)
    
    gcloud dataproc jobs submit spark \
        --cluster=$cluster_name \
        --region=$REGION \
        --jar=gs://$BUCKET_NAME/jars/earthquake-cooccurrence-assembly-1.0.jar \
        -- $input_path $output_path $num_partitions
    
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    echo "Job completed in $duration seconds."
    echo "$num_workers,$num_partitions,$duration" >> results-summary.csv
    echo ""
}

# Funzione per eliminare cluster
delete_cluster() {
    local num_workers=$1
    local cluster_name="earthquake-cluster-$num_workers"
    
    echo "Deleting cluster: $cluster_name..."
    gcloud dataproc clusters delete $cluster_name \
        --region=$REGION \
        --quiet
    echo "Cluster $cluster_name deleted."
    echo ""
}

# Inizializza file dei risultati
echo "workers,partitions,time_seconds" > results-summary.csv

# Esegui esperimenti
echo "Step 2: Running experiments..."
echo ""

for i in "${!WORKERS[@]}"; do
    workers=${WORKERS[$i]}
    partitions=${PARTITIONS[$i]}
    
    echo "===================================="
    echo "Experiment: $workers workers, $partitions partitions"
    echo "===================================="
    
    # Crea cluster
    create_cluster $workers
    
    # Aspetta che il cluster sia pronto
    sleep 30
    
    # Esegui job
    run_job $workers $partitions
    
    # Elimina cluster
    delete_cluster $workers
    
    echo "Experiment completed."
    echo ""
done

echo "===================================="
echo "All experiments completed!"
echo "===================================="
echo ""

# Download risultati
echo "Step 3: Downloading results..."
for i in "${!WORKERS[@]}"; do
    workers=${WORKERS[$i]}
    partitions=${PARTITIONS[$i]}
    output_path="gs://$BUCKET_NAME/output/workers-${workers}-partitions-${partitions}"
    local_path="./results/workers-${workers}-partitions-${partitions}"
    
    mkdir -p $local_path
    gsutil -m cp -r $output_path/* $local_path/
    echo "Downloaded results for $workers workers to $local_path"
done
echo ""

echo "Results summary:"
cat results-summary.csv
echo ""

# Calcola metriche di scalabilità
echo "Step 4: Calculating scalability metrics..."
python3 - <<EOF
import csv

# Leggi risultati
with open('results-summary.csv', 'r') as f:
    reader = csv.DictReader(f)
    results = list(reader)

# Baseline: 2 workers
baseline_time = float(results[0]['time_seconds'])

print("\n=== Scalability Metrics ===\n")
print(f"{'Workers':<10} {'Partitions':<12} {'Time(s)':<10} {'Speedup':<10} {'Efficiency':<10}")
print("-" * 60)

for row in results:
    workers = int(row['workers'])
    partitions = int(row['partitions'])
    time = float(row['time_seconds'])
    
    speedup = baseline_time / time
    efficiency = speedup / (workers / 2)  # Normalizzato a 2 workers
    
    print(f"{workers:<10} {partitions:<12} {time:<10.2f} {speedup:<10.2f} {efficiency:<10.2%}")

print("\nBaseline: 2 workers, time = {:.2f}s".format(baseline_time))
print("Speedup = T(2) / T(n)")
print("Efficiency = Speedup / (n/2)")
EOF

echo ""
echo "Done! Results saved in ./results/ and results-summary.csv"
