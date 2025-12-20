#!/bin/bash

# Script completo per eseguire TUTTI gli esperimenti richiesti:
# - 3 Approcci (GroupByKey, AggregateByKey, ReduceByKey)
# - 2 Partitioner (Hash, Range)  
# - 3 Configurazioni cluster (2, 3, 4 workers)
# 
# Genera un CSV completo con tutte le metriche per il report.
#
# Usage: ./run-complete-experiments.sh <bucket-name> <input-file>

set -e

if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <bucket-name> <input-csv-file>"
    echo "Example: $0 my-bucket earthquakes-full.csv"
    exit 1
fi

BUCKET_NAME=$1
INPUT_FILE=$2
REGION="europe-west1"
PROJECT_JAR="target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar"

echo "=============================================="
echo "COMPLETE EXPERIMENTS - Earthquake Analysis"
echo "=============================================="
echo "Bucket: $BUCKET_NAME"
echo "Input: $INPUT_FILE"
echo "Region: $REGION"
echo ""
echo "This will run 18 experiments:"
echo "  3 approaches × 2 partitioners × 3 worker configs"
echo ""
read -p "Continue? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    exit 1
fi

# Verifica JAR
if [ ! -f "$PROJECT_JAR" ]; then
    echo "Error: JAR not found. Run 'sbt assembly' first."
    exit 1
fi

# Upload files
echo ""
echo "Step 1: Uploading files to GCS..."
gsutil cp $PROJECT_JAR gs://$BUCKET_NAME/jars/
gsutil cp $INPUT_FILE gs://$BUCKET_NAME/data/
echo "✓ Upload complete"
echo ""

# Configurazioni
declare -a WORKERS=(2 3 4)
declare -a APPROACHES=("groupbykey" "aggregatebykey" "reducebykey")
declare -a PARTITIONERS=("hash" "range")

# File metriche finale
FINAL_METRICS="final_metrics_$(date +%Y%m%d_%H%M%S).csv"
echo "approach,partitioner,num_workers,num_partitions,total_events,unique_events,co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp" > $FINAL_METRICS

INPUT_PATH="gs://$BUCKET_NAME/data/$(basename $INPUT_FILE)"

total_experiments=0
completed_experiments=0

# Conta esperimenti totali
for workers in "${WORKERS[@]}"; do
    for approach in "${APPROACHES[@]}"; do
        for partitioner in "${PARTITIONERS[@]}"; do
            ((total_experiments++))
        done
    done
done

echo "Step 2: Running $total_experiments experiments..."
echo ""

# Esegui tutti gli esperimenti
for workers in "${WORKERS[@]}"; do
    
    partitions=$((workers * 4))
    cluster_name="earthquake-cluster-${workers}w"
    
    echo "=============================================="
    echo "Cluster Configuration: $workers workers"
    echo "=============================================="
    
    # Crea cluster
    echo "Creating cluster..."
    gcloud dataproc clusters create $cluster_name \
        --region=$REGION \
        --num-workers $workers \
        --master-boot-disk-size 240 \
        --worker-boot-disk-size 240 \
        --master-machine-type=n2-standard-4 \
        --worker-machine-type=n2-standard-4 \
        --quiet
    
    echo "✓ Cluster created: $cluster_name"
    echo ""
    
    # Esegui ogni combinazione approccio × partitioner
    for approach in "${APPROACHES[@]}"; do
        for partitioner in "${PARTITIONERS[@]}"; do
            
            ((completed_experiments++))
            
            echo "----------------------------------------------"
            echo "Experiment $completed_experiments/$total_experiments"
            echo "  Workers: $workers"
            echo "  Approach: $approach"
            echo "  Partitioner: $partitioner"
            echo "----------------------------------------------"
            
            output_path="gs://$BUCKET_NAME/output/${workers}w-${approach}-${partitioner}"
            
            # Rimuovi output precedente
            gsutil -m rm -rf $output_path 2>/dev/null || true
            
            # Esegui job
            start_time=$(date +%s)
            
            gcloud dataproc jobs submit spark \
                --cluster=$cluster_name \
                --region=$REGION \
                --jar=gs://$BUCKET_NAME/jars/earthquake-cooccurrence-assembly-1.0.jar \
                -- $INPUT_PATH $output_path $partitions $approach $partitioner $workers \
                > /dev/null 2>&1
            
            end_time=$(date +%s)
            duration=$((end_time - start_time))
            
            echo "✓ Completed in $duration seconds"
            
            # Scarica metriche
            mkdir -p results/${workers}w-${approach}-${partitioner}
            gsutil -m cp -r $output_path/metrics/* results/${workers}w-${approach}-${partitioner}/ 2>/dev/null || true
            
            # Estrai metriche CSV e aggiungi al file finale
            metrics_file=$(find results/${workers}w-${approach}-${partitioner} -name "part-*" 2>/dev/null | head -1)
            if [ -f "$metrics_file" ]; then
                # Salta header e aggiungi al file finale
                tail -n +2 "$metrics_file" >> $FINAL_METRICS
            fi
            
            echo ""
        done
    done
    
    # Elimina cluster
    echo "Deleting cluster $cluster_name..."
    gcloud dataproc clusters delete $cluster_name \
        --region=$REGION \
        --quiet
    echo "✓ Cluster deleted"
    echo ""
    
done

echo "=============================================="
echo "ALL EXPERIMENTS COMPLETED"
echo "=============================================="
echo ""

# Genera report delle metriche
echo "Step 3: Generating metrics report..."
echo ""

# Genera report con calcoli
python3 - << 'EOF'
import csv
from collections import defaultdict

# Leggi metriche
with open('$FINAL_METRICS', 'r') as f:
    reader = csv.DictReader(f)
    metrics = list(reader)

print("=" * 80)
print("COMPLETE METRICS REPORT")
print("=" * 80)
print()

# Raggruppa per approccio
by_approach = defaultdict(list)
for m in metrics:
    key = f"{m['approach']}_{m['partitioner']}"
    by_approach[key].append(m)

# Trova baseline (GroupByKey-Hash con 2 workers)
baseline = None
for m in metrics:
    if m['approach'] == 'GroupByKey' and m['partitioner'] == 'Hash' and m['num_workers'] == '2':
        baseline = m
        break

if not baseline:
    print("Warning: Baseline (GroupByKey-Hash-2w) not found")
    baseline_time = 1
else:
    baseline_time = float(baseline['total_time_ms'])

print("BASELINE: GroupByKey-Hash with 2 workers")
print(f"Baseline time: {baseline_time}ms ({baseline_time/1000:.2f}s)")
print()

# Report per approccio
print("-" * 80)
print(f"{'Config':<25} {'Workers':<8} {'Time(s)':<10} {'Speedup':<10} {'Efficiency':<12}")
print("-" * 80)

for config_name, config_metrics in sorted(by_approach.items()):
    for m in sorted(config_metrics, key=lambda x: int(x['num_workers'])):
        workers = int(m['num_workers'])
        time_ms = float(m['total_time_ms'])
        time_s = time_ms / 1000.0
        
        # Speedup rispetto al baseline
        speedup = baseline_time / time_ms
        
        # Strong Scaling Efficiency: E(n) = T(2) / (n * T(n) / 2)
        efficiency = baseline_time / (workers * time_ms / 2.0)
        
        print(f"{config_name:<25} {workers:<8} {time_s:<10.2f} {speedup:<10.3f} {efficiency*100:<11.1f}%")

print("-" * 80)
print()

# Trova configurazione migliore
best = min(metrics, key=lambda x: float(x['total_time_ms']))
print("BEST CONFIGURATION:")
print(f"  Approach: {best['approach']}")
print(f"  Partitioner: {best['partitioner']}")
print(f"  Workers: {best['num_workers']}")
print(f"  Time: {float(best['total_time_ms'])/1000:.2f}s")
print(f"  Speedup vs baseline: {baseline_time/float(best['total_time_ms']):.3f}x")
print()

# Confronto Partitioner
print("PARTITIONER COMPARISON (average times):")
print("-" * 80)
hash_times = [float(m['total_time_ms']) for m in metrics if m['partitioner'] == 'Hash']
range_times = [float(m['total_time_ms']) for m in metrics if m['partitioner'] == 'Range']

if hash_times and range_times:
    hash_avg = sum(hash_times) / len(hash_times)
    range_avg = sum(range_times) / len(range_times)
    
    print(f"Hash Partitioner:  {hash_avg/1000:.2f}s average")
    print(f"Range Partitioner: {range_avg/1000:.2f}s average")
    print(f"Difference: {abs(hash_avg - range_avg)/1000:.2f}s")
    
    if hash_avg < range_avg:
        improvement = ((range_avg - hash_avg) / range_avg) * 100
        print(f"Hash is {improvement:.1f}% faster")
    else:
        improvement = ((hash_avg - range_avg) / hash_avg) * 100
        print(f"Range is {improvement:.1f}% faster")

print()
print("=" * 80)

EOF

echo ""
echo "Step 4: Creating summary files..."

# Crea file con istruzioni per il report
cat > REPORT_INSTRUCTIONS.txt << 'EOINSTR'
=== ISTRUZIONI PER IL REPORT ===

Il file 'final_metrics_*.csv' contiene TUTTE le metriche raccolte.

COLONNE NEL CSV:
- approach: GroupByKey, AggregateByKey, ReduceByKey
- partitioner: Hash, Range
- num_workers: 2, 3, 4
- num_partitions: numero di partizioni usate
- total_events: eventi totali caricati
- unique_events: eventi unici dopo deduplicazione
- co_occurrences: coppie di co-occorrenze trovate
- load_time_ms: tempo di caricamento in millisecondi
- analysis_time_ms: tempo di analisi in millisecondi
- total_time_ms: tempo totale in millisecondi
- max_count: numero massimo di co-occorrenze per la coppia vincente
- timestamp: timestamp dell'esecuzione

METRICHE DA CALCOLARE PER IL REPORT:

1. SPEEDUP:
   S(n) = T_baseline / T(n)
   dove T_baseline = tempo GroupByKey-Hash con 2 workers

2. STRONG SCALING EFFICIENCY:
   E(n) = T_baseline / (n × T(n) / 2)
   dove n = numero di workers

3. CONFRONTO APPROCCI:
   Per ogni workers, confronta i 3 approcci

4. CONFRONTO PARTITIONER:
   Per ogni approccio, confronta Hash vs Range

GRAFICI RACCOMANDATI:
- Tempo vs Workers (una linea per ogni approccio)
- Speedup vs Workers
- Efficiency vs Workers  
- Bar chart: confronto approcci con 4 workers
- Bar chart: Hash vs Range per ogni approccio

TABELLE RACCOMANDATE:
- Tabella completa con tutte le configurazioni
- Tabella con solo i risultati migliori per ciascun criterio
- Tabella di confronto Partitioner

I risultati reali sono in: results/
Le metriche sono in: final_metrics_*.csv
EOINSTR

cat REPORT_INSTRUCTIONS.txt

echo ""
echo "=============================================="
echo "SUMMARY"
echo "=============================================="
echo "✓ All $total_experiments experiments completed"
echo "✓ Results saved in: ./results/"
echo "✓ Metrics CSV: $FINAL_METRICS"
echo "✓ Report instructions: REPORT_INSTRUCTIONS.txt"
echo ""
echo "You can now:"
echo "  1. Import $FINAL_METRICS into Excel/Google Sheets"
echo "  2. Create charts and tables for your report"
echo "  3. Calculate additional metrics as needed"
echo ""
