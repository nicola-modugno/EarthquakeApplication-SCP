# Earthquake Application - Complete Guide

**Guida Completa per l'Esecuzione del Progetto**

---

## 📋 Indice

1. [Prerequisiti](#prerequisiti)
2. [Setup Iniziale](#setup-iniziale)
3. [Compilazione](#compilazione)
4. [Test Locali](#test-locali)
5. [Esecuzione su Google Cloud](#esecuzione-su-google-cloud)
6. [Configurazioni e Parametri](#configurazioni-e-parametri)
7. [Esempi per Ogni Approccio](#esempi-per-ogni-approccio)
8. [Analisi dei Risultati](#analisi-dei-risultati)
9. [Troubleshooting](#troubleshooting)

---

## Prerequisiti

### Software Necessario

```bash
# Verifica Java (richiesto: 17+)
java -version
# Output atteso: java version "17" o superiore

# Verifica Scala (richiesto: 2.13.x)
scala -version
# Output atteso: Scala code runner version 2.13.x

# Verifica SBT
sbt --version
# Output atteso: sbt version 1.x.x

# Verifica Spark (richiesto: 4.x per test locali)
spark-submit --version
# Output atteso: version 4.0.x o compatibile
```

### Google Cloud SDK (per esecuzione cloud)

```bash
# Installa da: https://cloud.google.com/sdk/docs/install

# Verifica installazione
gcloud version
gsutil version

# Login
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
```

---

## Setup Iniziale

### 1. Clona/Scarica il Progetto

```bash
cd /path/to/your/workspace
# Se hai Git
git clone YOUR_REPO_URL
cd EarthquakeCoOccurrenceAnalysis-SACP
```

### 2. Verifica Struttura

```bash
# Verifica che esistano questi file:
ls -la src/main/scala/Main.scala
ls -la build.sbt
ls -la project/plugins.sbt

# Windows
dir src\main\scala\Main.scala
dir build.sbt
dir project\plugins.sbt
```

### 3. Prepara Dataset di Test

Crea `test-data.csv`:

```csv
latitude,longitude,date
37.502,15.312,2024-03-12 02:10:00
38.112,13.372,2024-03-12 05:55:00
37.521,15.324,2024-03-12 04:32:00
37.547,15.323,2024-04-01 14:22:00
38.147,13.324,2024-04-01 21:55:00
37.535,15.341,2024-04-03 12:32:00
38.142,13.387,2024-04-03 18:33:00
43.769,11.255,2024-04-03 21:10:00
37.498,15.289,2024-03-12 23:45:00
```

**IMPORTANTE**: Il file DEVE avere:
- Header: `latitude,longitude,date`
- Almeno 3 colonne
- Date in formato `YYYY-MM-DD` o `YYYY-MM-DD HH:MM:SS`

---

## Compilazione

### Pulizia e Compilazione Completa
I seguenti comandi vanno eseguiti nella *root* del repository
```bash
# Pulisci build precedente
sbt clean

# Compila sorgenti
sbt compile
# Verifica: [success] e nessun [error]

# Crea JAR assembly
sbt assembly
# Verifica: "Built: .../earthquake-application.jar"
```

### Verifica JAR Creato

```bash
# Linux/Mac
ls -lh target/scala-2.13/earthquake-application.jar

# Windows (PowerShell)
Get-Item target\scala-2.13\earthquake-application.jar

# Windows (CMD)
dir target\scala-2.13\earthquake-application.jar
```

---

## Test Locali

### Sintassi Base

```bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.13/earthquake-application.jar \
  <INPUT_FILE> \
  <OUTPUT_DIR> \
  <NUM_PARTITIONS> \
  <APPROACH> \
  <PARTITIONER> \
  <NUM_WORKERS>
```

### Specifiche Parametri

| Parametro | Descrizione | Valori | Default |
|-----------|-------------|--------|---------|
| INPUT_FILE | Path al CSV | es. `test-data.csv` | - |
| OUTPUT_DIR | Directory output | es. `output-test` | - |
| NUM_PARTITIONS | Numero partizioni | 4, 8, 12, 16 | 8 |
| APPROACH | Algoritmo | `groupbykey`, `aggregatebykey`, `reducebykey` | `groupbykey` |
| PARTITIONER | Tipo partitioner | `hash`, `range` | `hash` |
| NUM_WORKERS | Numero workers | 1 (locale), 2-4 (cloud) | 1 |

---

## Esempi per Ogni Approccio

### Approccio 1: GroupByKey

```bash
# Linux/Mac/Git Bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.13/earthquake-application.jar \
  test-data.csv \
  output-groupbykey \
  4 \
  groupbykey \
  hash \
  1

# Windows CMD
spark-submit ^
  --class Main ^
  --master "local[*]" ^
  --driver-memory 2g ^
  target\scala-2.13\earthquake-application.jar ^
  test-data.csv ^
  output-groupbykey ^
  4 ^
  groupbykey ^
  hash ^
  1
```

### Approccio 2: AggregateByKey

```bash
# Linux/Mac/Git Bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.13/earthquake-application.jar \
  test-data.csv \
  output-aggregatebykey \
  4 \
  aggregatebykey \
  hash \
  1

# Windows CMD
spark-submit ^
  --class Main ^
  --master "local[*]" ^
  --driver-memory 2g ^
  target\scala-2.13\earthquake-application.jar ^
  test-data.csv ^
  output-aggregatebykey ^
  4 ^
  aggregatebykey ^
  hash ^
  1
```

### Approccio 3: ReduceByKey

```bash
# Linux/Mac/Git Bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.13/earthquake-application.jar \
  test-data.csv \
  output-reducebykey \
  4 \
  reducebykey \
  hash \
  1

# Windows CMD
spark-submit ^
  --class Main ^
  --master "local[*]" ^
  --driver-memory 2g ^
  target\scala-2.13\earthquake-application.jar ^
  test-data.csv ^
  output-reducebykey ^
  4 ^
  reducebykey ^
  hash ^
  1
```

---

## Test con Diversi Partitioner

### Hash Partitioner (Default)

```bash
spark-submit \
  --class Main \
  --master local[*] \
  target/scala-2.13/earthquake-application.jar \
  test-data.csv \
  output-hash \
  4 \
  aggregatebykey \
  hash \
  1
```

### Range Partitioner

```bash
spark-submit \
  --class Main \
  --master local[*] \
  target/scala-2.13/earthquake-application.jar \
  test-data.csv \
  output-range \
  4 \
  aggregatebykey \
  range \
  1
```

---

## Verifica Risultati Locali

### Visualizza Output

```bash
# Linux/Mac/Git Bash
cat output-aggregatebykey/part-00000

# Windows CMD
type output-aggregatebykey\part-00000

# Windows PowerShell
Get-Content output-aggregatebykey\part-00000
```

**Formato output**:
```
((lat1, lon1), (lat2, lon2))
2024-03-12
2024-04-01
2024-04-03
```

### Visualizza Metriche

```bash
# Linux/Mac/Git Bash
cat output-aggregatebykey/metrics/part-00000

# Windows CMD
type output-aggregatebykey\metrics\part-00000
```

**Formato metriche CSV**:

```csv
approach,partitioner,num_workers,num_partitions,total_events,unique_events,
co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp
```

## Esecuzione su Google Cloud

### Setup Google Cloud Storage

```bash
# 1. Crea bucket
BUCKET_NAME="earthquake-YOUR_MATRICOLA"
gsutil mb gs://$BUCKET_NAME/

# 2. Upload JAR
gsutil cp target/scala-2.13/earthquake-application.jar \
  gs://$BUCKET_NAME/jars/

# 3. Upload dataset
gsutil cp earthquakes-full.csv \
  gs://$BUCKET_NAME/data/

# Verifica upload
gsutil ls gs://$BUCKET_NAME/jars/
gsutil ls gs://$BUCKET_NAME/data/
```

### Creazione Cluster DataProc

```bash
# Cluster con 2 workers
gcloud dataproc clusters create earthquake-cluster-2w \
  --region=europe-west1 \
  --num-workers 2 \
  --master-boot-disk-size 240 \
  --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4

# Verifica creazione
gcloud dataproc clusters list --region=europe-west1
```

### Esecuzione Job: Approccio 1 (GroupByKey)

```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=europe-west1 \
  --jar=gs://$BUCKET_NAME/jars/earthquake-application.jar \
  -- gs://$BUCKET_NAME/data/earthquakes-full.csv \
     gs://$BUCKET_NAME/output/2w-groupbykey-hash \
     8 \
     groupbykey \
     hash \
     2
```

### Esecuzione Job: Approccio 2 (AggregateByKey)

```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=europe-west1 \
  --jar=gs://$BUCKET_NAME/jars/earthquake-application.jar \
  -- gs://$BUCKET_NAME/data/earthquakes-full.csv \
     gs://$BUCKET_NAME/output/2w-aggregatebykey-hash \
     8 \
     aggregatebykey \
     hash \
     2
```

### Esecuzione Job: Approccio 3 (ReduceByKey)

```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=europe-west1 \
  --jar=gs://$BUCKET_NAME/jars/earthquake-application.jar \
  -- gs://$BUCKET_NAME/data/earthquakes-full.csv \
     gs://$BUCKET_NAME/output/2w-reducebykey-hash \
     8 \
     reducebykey \
     hash \
     2
```

### Download Risultati

```bash
# Download output
gsutil cp -r gs://$BUCKET_NAME/output/2w-aggregatebykey-hash ./results/

# Visualizza risultato
gsutil cat gs://$BUCKET_NAME/output/2w-aggregatebykey-hash/part-*

# Visualizza metriche
gsutil cat gs://$BUCKET_NAME/output/2w-aggregatebykey-hash/metrics/part-*
```

### Eliminazione Cluster

```bash
# IMPORTANTE: Elimina sempre il cluster per non consumare crediti!
gcloud dataproc clusters delete earthquake-cluster-2w \
  --region=europe-west1
```

---

## Configurazioni Complete per il Progetto

### Matrice Esperimenti Raccomandata

| # | Workers | Partitions | Approach | Partitioner | Comando |
|---|---------|------------|----------|-------------|---------|
| 1 | 2 | 8 | groupbykey | hash | Vedi sotto |
| 2 | 2 | 8 | aggregatebykey | hash | Vedi sotto |
| 3 | 2 | 8 | reducebykey | hash | Vedi sotto |
| 4 | 2 | 8 | aggregatebykey | range | Vedi sotto |
| 5 | 3 | 12 | groupbykey | hash | Vedi sotto |
| 6 | 3 | 12 | aggregatebykey | hash | Vedi sotto |
| 7 | 3 | 12 | reducebykey | hash | Vedi sotto |
| 8 | 4 | 16 | groupbykey | hash | Vedi sotto |
| 9 | 4 | 16 | aggregatebykey | hash | Vedi sotto |
| 10 | 4 | 16 | reducebykey | hash | Vedi sotto |

### Script per Eseguire Tutti gli Esperimenti

**Salva come `run-all-experiments.sh`**:

```bash
#!/bin/bash

BUCKET="YOUR_BUCKET_NAME"
DATASET="earthquakes-full.csv"
JAR="gs://$BUCKET/jars/earthquake-application.jar"
DATA="gs://$BUCKET/data/$DATASET"
REGION="europe-west1"

# Array configurazioni
declare -a CONFIGS=(
  "2:8:groupbykey:hash"
  "2:8:aggregatebykey:hash"
  "2:8:reducebykey:hash"
  "2:8:aggregatebykey:range"
  "3:12:groupbykey:hash"
  "3:12:aggregatebykey:hash"
  "3:12:reducebykey:hash"
  "4:16:groupbykey:hash"
  "4:16:aggregatebykey:hash"
  "4:16:reducebykey:hash"
)

for config in "${CONFIGS[@]}"; do
  IFS=':' read -r workers partitions approach partitioner <<< "$config"
  
  cluster="earthquake-cluster-${workers}w"
  output="gs://$BUCKET/output/${workers}w-${approach}-${partitioner}"
  
  echo "=============================================="
  echo "Config: $workers workers, $approach, $partitioner"
  echo "=============================================="
  
  # Crea cluster se non esiste
  if ! gcloud dataproc clusters describe $cluster --region=$REGION &>/dev/null; then
    echo "Creating cluster $cluster..."
    gcloud dataproc clusters create $cluster \
      --region=$REGION \
      --num-workers $workers \
      --master-boot-disk-size 240 \
      --worker-boot-disk-size 240 \
      --master-machine-type=n2-standard-4 \
      --worker-machine-type=n2-standard-4 \
      --quiet
  fi
  
  # Submit job
  echo "Submitting job..."
  gcloud dataproc jobs submit spark \
    --cluster=$cluster \
    --region=$REGION \
    --jar=$JAR \
    -- $DATA $output $partitions $approach $partitioner $workers
  
  # Download metriche
  gsutil cp ${output}/metrics/part-* metrics-${workers}w-${approach}-${partitioner}.csv
  
  echo "Done!"
  echo ""
done

# Elimina tutti i cluster
for workers in 2 3 4; do
  cluster="earthquake-cluster-${workers}w"
  if gcloud dataproc clusters describe $cluster --region=$REGION &>/dev/null; then
    echo "Deleting cluster $cluster..."
    gcloud dataproc clusters delete $cluster --region=$REGION --quiet
  fi
done

echo "All experiments completed!"
```

Esegui:
```bash
chmod +x run-all-experiments.sh
./run-all-experiments.sh
```

---

## Analisi dei Risultati

### Calcolo Speedup

```
Speedup(n) = T_baseline / T(n)

dove:
- T_baseline = tempo con 2 workers, GroupByKey, Hash
- T(n) = tempo configurazione corrente
```

**Esempio**:
```
T_baseline = 120s
T_current = 80s
Speedup = 120 / 80 = 1.5x
```

### Calcolo Strong Scaling Efficiency

```
Efficiency(n) = T(2) / (n × T(n) / 2)

dove:
- T(2) = tempo con 2 workers
- T(n) = tempo con n workers
- n = numero workers
```

**Esempio**:
```
T(2) = 120s
T(4) = 40s
n = 4

Efficiency = 120 / (4 × 40 / 2) = 120 / 80 = 1.5 = 150%
```

### Import Metriche in Excel/Google Sheets

1. Combina tutti i file CSV metriche:
```bash
cat metrics-*.csv > all-metrics.csv
```

2. Import in Excel/Sheets

3. Crea tabelle pivot:
    - Rows: num_workers
    - Columns: approach
    - Values: total_time_ms (average)

4. Genera grafici:
    - Line chart: Time vs Workers
    - Bar chart: Approach comparison
    - Line chart: Speedup vs Workers

---

## Comandi Quick Reference

```bash
# Compila
sbt assembly

# Test locale (AggregateByKey)
spark-submit --class Main --master local[*] \
  target/scala-2.13/earthquake-application.jar \
  test-data.csv output 4 aggregatebykey hash 1

# Upload cloud
gsutil cp target/scala-2.13/earthquake-application.jar gs://BUCKET/jars/

# Crea cluster
gcloud dataproc clusters create my-cluster \
  --region=europe-west1 --num-workers 2 \
  --master-machine-type=n2-standard-4 --worker-machine-type=n2-standard-4

# Submit job
gcloud dataproc jobs submit spark \
  --cluster=my-cluster --region=europe-west1 \
  --jar=gs://BUCKET/jars/earthquake-application.jar \
  -- gs://BUCKET/data/earthquakes-full.csv gs://BUCKET/output/test 8 aggregatebykey hash 2

# Elimina cluster
gcloud dataproc clusters delete my-cluster --region=europe-west1
```
---

## Troubleshooting

### Problema: 0 Eventi Caricati

**Sintomi**:
```
Total events loaded: 0
Unique events after deduplication: 0
```

**Cause possibili**:
1. File non trovato
2. Path errato
3. Formato CSV incorretto

**Soluzioni**:

```bash
# Verifica che il file esista
ls -la test-data.csv

# Verifica formato CSV
head -5 test-data.csv
# Deve mostrare:
# latitude,longitude,date
# 37.502,15.312,2024-03-12...

# Verifica con path assoluto
spark-submit ... $(pwd)/test-data.csv ...

# Su Windows, usa path completo
spark-submit ... C:\Users\...\test-data.csv ...
```

### Problema: NoClassDefFoundError

**Sintomi**:
```
Error: Failed to load class Main
```

**Soluzione**:
```bash
# Verifica che il JAR esista
ls -la target/scala-2.13/earthquake-application.jar

# Se non esiste, ricompila
sbt clean assembly
```

### Problema: OutOfMemoryError

**Sintomi**:
```
java.lang.OutOfMemoryError: Java heap space
```

**Soluzioni**:

```bash
# Aumenta driver memory
spark-submit --driver-memory 4g ...

# Oppure riduci dimensione dataset per test
head -100 earthquakes-full.csv > earthquakes-small.csv
```

### Problema: Permission Denied (Google Cloud)

**Sintomi**:
```
403 Forbidden
```

**Soluzioni**:

```bash
# Re-autentica
gcloud auth login
gcloud auth application-default login

# Verifica progetto
gcloud config get-value project

# Verifica permessi bucket
gsutil iam get gs://YOUR_BUCKET
```

---

**Autore**: Nicola Modugno  
**Corso**: Scalable and Cloud Programming  
**A.A.**: 2025-26