# Earthquake Application - Guida Completa

Guida operativa per l'esecuzione del progetto di analisi distribuita di co-occorrenze sismiche.

---

## 📋 Indice

1. [Prerequisiti](#prerequisiti)
2. [Setup Iniziale](#setup-iniziale)
3. [Compilazione](#compilazione)
4. [Esecuzione Locale](#esecuzione-locale)
5. [Deployment su Google Cloud](#deployment-su-google-cloud)
6. [Analisi Risultati](#analisi-risultati)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisiti

### Software Richiesto

```bash
# Java 11+
java -version

# Scala 2.12.x
scala -version

# SBT 1.x
sbt --version

# Spark 3.5.x (per test locali)
spark-submit --version

# Google Cloud SDK (per deployment cloud)
gcloud version
```

### Installazione Google Cloud SDK

```bash
# Download da: https://cloud.google.com/sdk/docs/install

# Autenticazione
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
```

---

## Setup Iniziale

### 1. Struttura Progetto

```
EarthquakeAnalysis-SCP/
├── src/main/scala/
│   ├── Main.scala
│   ├── analysis/
│   |   ├── AnalysisResult.scala
│   |   ├── Create AnalysisResult.scala
│   |   ├── CoOccurrenceAnalysis.scala
│   |   ├── EarthquakeEvent.scala
│   |   ├── ExecutionMetrics.scala
│   |   ├── Location.scala
│   |   ├── LocationPair.scala
│   |   └── MetricsCollector.scala
│   ├── extraction/
│   |   └── DataExtractor.scala
│   └── utils/
│   |   └── Utils.scala
├── build.sbt
└── project/
```

### 2. Dataset di Test

Cerca `test-data.csv`:

```csv
time,latitude,longitude,depth,mag
2024-03-12T02:10:00.000Z,37.502,15.312,10.0,3.5
2024-03-12T05:55:00.000Z,38.112,13.372,15.0,2.8
2024-04-01T14:22:00.000Z,37.547,15.323,12.0,4.1
2024-04-01T21:55:00.000Z,38.147,13.324,9.0,3.0
2024-04-03T12:32:00.000Z,37.535,15.341,11.0,2.5
2024-04-03T18:33:00.000Z,38.142,13.387,14.0,3.8
```

**Requisiti formato:**
- Header con `time,latitude,longitude` (minimo)
- Time in formato: `YYYY-MM-DDTHH:MM:SS.sssZ`
- Coordinate decimali

---

## Compilazione

```bash
# Dalla root del progetto
sbt clean
sbt compile
sbt assembly

# Verifica JAR generato
ls -lh target/scala-2.12/earthquake-application.jar
```

**Output atteso:** JAR di ~15-20 MB

---

## Esecuzione Locale

### Sintassi Base

```bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.12/earthquake-application.jar \
  <INPUT_FILE> <OUTPUT_DIR> <NUM_PARTITIONS> <APPROACH> <NUM_WORKERS>
```

### Parametri

| Parametro | Valori | Default | Descrizione |
|-----------|--------|---------|-------------|
| INPUT_FILE | path | - | File CSV input |
| OUTPUT_DIR | path | - | Directory output |
| NUM_PARTITIONS | 4,8,16,32,48 | 8 | Partizioni `repartition()` |
| APPROACH | groupbykey, aggregatebykey, reducebykey | groupbykey | Strategia aggregazione |
| NUM_WORKERS | 1-4 | 1 | Numero workers |

### Esempi per Approccio

#### GroupByKey
```bash
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output-gbk 8 groupbykey 1
```

#### AggregateByKey
```bash
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output-abk 8 aggregatebykey 1
```

#### ReduceByKey
```bash
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output-rbk 8 reducebykey 1
```

### Verifica Output

```bash
# Risultato principale
cat output-abk/part-00000

# Metriche CSV
cat output-abk/metrics/part-00000
```

**Output atteso:**
```
((37.5, 15.3), (38.1, 13.4))
2024-03-12
2024-04-01
2024-04-03
```

---

## Deployment su Google Cloud

### 1. Setup Storage

```bash
BUCKET="bucket_scp_1"
PROJECT="your-project-id"
REGION="europe-west1"

# Configura progetto
gcloud config set project $PROJECT

# Crea bucket
gcloud storage buckets create gs://$BUCKET --location=$REGION

# Upload JAR e dataset
gcloud storage cp target/scala-2.12/earthquake-application.jar gs://$BUCKET/jars/
gcloud storage cp dataset-earthquakes-full.csv gs://$BUCKET/data/
```

### 2. Creazione Cluster

**Configurazione standard n2-standard-4:**

```bash
# 2 workers (12 vCPU) - Free tier
gcloud dataproc clusters create earthquake-cluster-2w \
  --region=$REGION \
  --image-version=2.1-debian11 \
  --num-workers 2 \
  --master-boot-disk-size 240 \
  --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4 \
  --properties=spark:spark.executor.memory=10g,spark:spark.driver.memory=6g,spark:spark.executor.memoryOverhead=2g,spark:spark.driver.memoryOverhead=1g

# 3 workers (16 vCPU) - Richiede quota 24 vCPU
gcloud dataproc clusters create earthquake-cluster-3w \
  --region=$REGION \
  --num-workers 3 \
  [stessi parametri]

# 4 workers (20 vCPU) - Richiede quota 24 vCPU
gcloud dataproc clusters create earthquake-cluster-4w \
  --region=$REGION \
  --num-workers 4 \
  [stessi parametri]
```

**Nota:** Configurazioni 3w/4w richiedono aumento quota vCPU a 24.

### 3. Esecuzione Job

```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=$REGION \
  --jar=gs://$BUCKET/jars/earthquake-application.jar \
  -- gs://$BUCKET/data/dataset-earthquakes-full.csv \
     gs://$BUCKET/output/2w-16p-aggregatebykey \
     16 aggregatebykey 2
```

## 3.1 Script bash per l'esecuzione di ogni espserimento
```bash
#!/bin/bash

# Script per testare diverse configurazioni di partizioni

BUCKET="bucket_scp_1"
DATASET="dataset-earthquakes-full.csv"
JAR="gs://$BUCKET/jars/earthquake-application.jar"
DATA="gs://$BUCKET/data/$DATASET"
REGION="europe-west1"

# Funzione per creare cluster con n2-standard-4 per tutte le macchine
create_cluster() {
  local workers=$1
  local cluster="earthquake-cluster-${workers}w"
  
  echo ""
  echo "Creating ${workers}-worker cluster..."
  echo "All machines: n2-standard-4 (4 vCPU, 16GB RAM)"
  
  if [ $workers -eq 2 ]; then
    echo "Total vCPU: 12 (master + 2 workers)"
  elif [ $workers -eq 3 ]; then
    echo "Total vCPU: 16 (master + 3 workers)"
  elif [ $workers -eq 4 ]; then
    echo "Total vCPU: 20 (master + 4 workers)"
  fi
  
  gcloud dataproc clusters create $cluster \
    --region=$REGION \
    --image-version=2.1-debian11 \
    --num-workers $workers \
    --master-boot-disk-size 240 \
    --worker-boot-disk-size 240 \
    --master-machine-type=n2-standard-4 \
    --worker-machine-type=n2-standard-4 \
    --quiet
  
  return $?
}

# Funzione per eseguire job
run_job() {
  local workers=$1
  local partitions=$2
  local approach=$3
  local cluster="earthquake-cluster-${workers}w"
  local output="gs://$BUCKET/output/${workers}w-${partitions}p-${approach}"
  
  echo ""
  echo "Running: $workers workers, $partitions partitions, $approach"
  
  gcloud dataproc jobs submit spark \
    --cluster=$cluster \
    --region=$REGION \
    --jar=$JAR \
    -- $DATA $output $partitions $approach $workers
  
  if [ $? -eq 0 ]; then
    echo "Job completed successfully!"
    
    # Scarica metriche
    echo "Downloading metrics..."
    gcloud storage cat "${output}/metrics/part-*" > "metrics-${workers}w-${partitions}p-${approach}.csv" 2>/dev/null
    
    if [ $? -eq 0 ]; then
      echo "Metrics saved: metrics-${workers}w-${partitions}p-${approach}.csv"
    fi
  else
    echo "Errore! -  Job failed!"
  fi
}

# Funzione per eliminare cluster
delete_cluster() {
  local workers=$1
  local cluster="earthquake-cluster-${workers}w"
  
  echo ""
  echo "Deleting cluster $cluster..."
  
  gcloud dataproc clusters delete $cluster --region=$REGION --quiet
  echo "Waiting for resources to be released..."
  sleep 30
}

echo ""
echo "TEST PARTIZIONAMENTO - CONFIGURAZIONI COMPLETE"
echo ""
echo "Configurazione hardware:"
echo "  - Tipo macchina: n2-standard-4"
echo "  - Partitioner: Hash (via repartition)"
echo "  - Configurazioni: 2, 3, 4 workers"
echo ""
echo "Test pianificati:"
echo "  1. Cluster 2 workers (12 vCPU): 8, 16, 32, 48 partizioni"
echo "  2. Cluster 3 workers (16 vCPU): 12, 24, 36 partizioni"
echo "  3. Cluster 4 workers (20 vCPU): 16, 32, 48 partizioni"
echo ""
echo "Approcci testati:"
echo "  - GroupByKey (tutte le configurazioni)"
echo "  - AggregateByKey (partizioni ottimali)"
echo "  - ReduceByKey (partizioni ottimali)"
echo ""
read -p "Premere INVIO per continuare o Ctrl+C per annullare..."
echo ""


# TEST 2: 3 WORKERS


echo ""
echo "3 WORKERS (16 vCPU)"

create_cluster 3
if [ $? -eq 0 ]; then
  
  # Test GroupByKey con diverse partizioni
  echo ""
  echo "--- GroupByKey Tests ---"
  run_job 3 12 groupbykey
  run_job 3 24 groupbykey
  run_job 3 36 groupbykey
  
  # Test AggregateByKey (partizioni ottimali)
  echo ""
  echo "--- AggregateByKey Tests ---"
  run_job 3 12 aggregatebykey
  run_job 3 24 aggregatebykey
  run_job 3 36 aggregatebykey
  
  # Test ReduceByKey (partizioni ottimali)
  echo ""
  echo "--- ReduceByKey Tests ---"
  run_job 3 12 reducebykey
  run_job 3 24 reducebykey
  run_job 3 36 reducebykey
  
  delete_cluster 3
else
  echo "Errore! -  Failed to create 3-worker cluster!"
  echo "Warning! -   Possibile problema quota vCPU. Verifica con:"
  echo "    gcloud compute project-info describe --format='value(quotas)' | grep CPUS"
  exit 1
fi

# TEST 3: 4 WORKERS - Variazioni partizioni

echo ""
echo "4 WORKERS (20 vCPU)"

create_cluster 4
if [ $? -eq 0 ]; then
  
  # Test GroupByKey con diverse partizioni
  echo ""
  echo "--- GroupByKey Tests ---"
  run_job 4 16 groupbykey
  run_job 4 32 groupbykey
  run_job 4 48 groupbykey
  
  # Test AggregateByKey (partizioni ottimali)
  echo ""
  echo "--- AggregateByKey Tests ---"
  run_job 4 16 aggregatebykey
  run_job 4 32 aggregatebykey
  run_job 4 48 aggregatebykey
  
  # Test ReduceByKey (partizioni ottimali)
  echo ""
  echo "--- ReduceByKey Tests ---"
  run_job 4 16 reducebykey
  run_job 4 32 reducebykey
  run_job 4 48 reducebykey
  
  delete_cluster 4
else
  echo "Errore! - Failed to create 4-worker cluster!"
  echo "Warning! -  Possibile problema quota vCPU. Verifica con:"
  echo "    gcloud compute project-info describe --format='value(quotas)' | grep CPUS"
  exit 1
fi

# RIEPILOGO FINALE

echo ""
echo "Esperimenti completati"
echo ""
echo "Metriche scaricate:"
ls -lh metrics-*.csv 2>/dev/null | awk '{print "  - " $9 " (" $5 ")"}'
echo ""

```
### 4. Download Risultati

```bash
# Metriche specifiche
gcloud storage cat gs://$BUCKET/output/2w-16p-aggregatebykey/metrics/part-* > metrics.csv

# Tutte le metriche
gcloud storage cat gs://$BUCKET/output/*/metrics/part-* > all-metrics.csv
```

### 5. Eliminazione Cluster

```bash
# IMPORTANTE: Elimina sempre il cluster dopo l'uso
gcloud dataproc clusters delete earthquake-cluster-2w --region=$REGION --quiet
```

---

## Troubleshooting

### OutOfMemoryError

**Sintomi:** `java.lang.OutOfMemoryError: Java heap space`

**Soluzioni:**
```bash
# Locale: aumenta memory
spark-submit --driver-memory 4g ...

# Cloud: riduci executor memory o aumenta partizioni
--properties=spark:spark.executor.memory=8g
```

### Quota vCPU Insufficiente

**Sintomi:** `Insufficient 'CPUS_ALL_REGIONS' quota`

**Verifica:**
```bash
gcloud compute project-info describe --format="value(quotas)" | grep CPUS
```

**Soluzione:** Richiedi aumento quota (vedi sezione Deployment)

### Permission Denied (GCS)

**Sintomi:** `403 Forbidden`

**Soluzioni:**
```bash
# Re-autentica
gcloud auth login
gcloud auth application-default login

# Verifica ruolo
gcloud projects get-iam-policy $PROJECT_ID
```

---

## Quick Reference

```bash
# Compilazione
sbt clean assembly

# Test locale
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  input.csv output 16 aggregatebykey 1

# Crea cluster (2w)
gcloud dataproc clusters create earthquake-cluster-2w \
  --region=europe-west1 --num-workers 2 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4

# Submit job
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=europe-west1 \
  --jar=gs://bucket/jars/earthquake-application.jar \
  -- gs://bucket/data/dataset.csv \
     gs://bucket/output/2w-16p-abk \
     16 aggregatebykey 2

# Download metriche
gcloud storage cat gs://bucket/output/*/metrics/part-* > metrics.csv

# Elimina cluster
gcloud dataproc clusters delete earthquake-cluster-2w \
  --region=europe-west1 --quiet

# Verifica quota
gcloud compute project-info describe \
  --format="value(quotas)" | grep CPUS
```

---

**Autore:** Nicola Modugno  
**Corso:** Scalable and Cloud Programming  
**A.A.:** 2024-25
