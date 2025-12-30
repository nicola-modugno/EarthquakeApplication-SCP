## 🏠 PARTE 1: Test in Locale

---
# Earthquake Application - Guida all'uso

**Guida completa per l'esecuzione del progetto**

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
# Verifica Java (richiesto: 11+)
java -version
# Output atteso: java version "11"

# Verifica Scala (richiesto: 2.12.x)
scala -version
# Output atteso: Scala code runner version 2.12.x

# Verifica SBT
sbt --version
# Output atteso: sbt version 1.x.x

# Verifica Spark (richiesto: 3.x per test locali)
spark-submit --version
# Output atteso: version 3.0.x o compatibile
```

### Step 1: Compila il Progetto

```bash
cd /path/to/your/project

# Compila
sbt clean compile

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
# Test con approccio 1 (GroupByKey) e Hash partitioner
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
  output-local-test \
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

### Step 3: Test Tutti gli Approcci in Locale

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

Questo testerà:
- ✅ GroupByKey
- ✅ AggregateByKey  
- ✅ ReduceByKey

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

## ☁️ PARTE 2: Esecuzione su Google Cloud

### Prerequisiti Google Cloud

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

### Step 1: Crea un Bucket su Google Cloud Storage

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
BUCKET_NAME="bucket_scp_1"
gsutil mb gs://$BUCKET_NAME/

# 2. Upload JAR
gsutil cp target/scala-2.13/earthquake-application.jar \
  gs://$BUCKET_NAME/jars/

# Upload del dataset (usa il TUO file!)
gsutil cp /path/to/earthquakes-full.csv \
  gs://$BUCKET_NAME/data/

# Verifica upload
gsutil ls gs://$BUCKET_NAME/jars/
gsutil ls gs://$BUCKET_NAME/data/
```

### Step 3: Crea un Cluster DataProc

```bash
# Cluster con 2 workers
gcloud dataproc clusters create earthquake-cluster-2w \
  --region=europe-west1 \
  --num-workers 2 \
  --master-boot-disk-size 240 \
  --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4

# IMPORTANTE: Aspetta che il cluster sia pronto (1-2 minuti)
```

### Step 4: Esegui UN Job Singolo

```bash
# Parametri
BUCKET_NAME="earthquake-analysis-TUOMATRICOLA"  # Il TUO bucket!
CLUSTER_NAME="earthquake-cluster-2w"
REGION="europe-west1"

# Esegui job con GroupByKey e Hash partitioner
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=europe-west1 \
  --jar=gs://$BUCKET_NAME/jars/earthquake-application.jar \
  -- gs://$BUCKET_NAME/data/earthquakes-full.csv \
     gs://$BUCKET_NAME/output/test-run \
     8 \
     groupbykey \
     hash \
     2

# Attendi che il job finisca (guarda nella console)
```

### Step 5: Scarica i Risultati

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

### Step 6: Elimina il Cluster (IMPORTANTE!)

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

## 🔬 PARTE 3: Esperimenti Completi (Per il Report)

### Opzione A: Script Automatico (RACCOMANDATO)

```bash
chmod +x run-complete-experiments.sh

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
  "2:8:groupbykey:range"
  "2:8:aggregatebykey:range"
  "2:8:reducebykey:range"
  "3:12:groupbykey:hash"
  "3:12:aggregatebykey:hash"
  "3:12:reducebykey:hash"
  "3:12:groupbykey:range"
  "3:12:aggregatebykey:range"
  "3:12:reducebykey:range"
  "4:16:groupbykey:hash"
  "4:16:aggregatebykey:hash"
  "4:16:reducebykey:hash"
  "4:16:groupbykey:range"
  "4:16:aggregatebykey:range"
  "4:16:reducebykey:range"
)

for config in "${CONFIGS[@]}"; do
  IFS=':' read -r workers partitions approach partitioner <<< "$config"
  
  cluster="earthquake-cluster-${workers}w"
  output="gs://$BUCKET/output/${workers}w-${approach}-${partitioner}-4cpus"
  
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
  gcloud storage cp -r gs://$BUCKET/output ./cloud-results

  
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

## 📊 Interpretare le Metriche

Il file CSV generato avrà queste colonne:

```csv
approach,partitioner,num_workers,num_partitions,total_events,unique_events,co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp
GroupByKey,Hash,2,8,1000000,950000,50000,12345,45678,58023,150,1234567890
```

### Metriche Principali:

1. **total_time_ms**: Tempo totale (questo è il più importante!)

2. **Speedup**: 
   ```
   S(n) = T(baseline) / T(current)
   dove baseline = GroupByKey-Hash con 2 workers
   ```

3. **Strong Scaling Efficiency**:
   ```
   E(n) = T(2) / (n × T(n) / 2)
   ```

4. **Confronto Partitioner**:
   ```
   Differenza % = (T_hash - T_range) / T_hash × 100
   ```

---

## 🎯 Configurazioni da Testare

### MINIMO (Per consegna base):

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
✅ 2 workers: GroupByKey-Hash
✅ 3 workers: GroupByKey-Hash  
✅ 4 workers: GroupByKey-Hash
```

### RACCOMANDATO (Per buon voto):

```
✅ Tutti e 3 gli approcci
✅ Hash e Range partitioner
✅ 2, 3, 4 workers
= 18 esperimenti totali
```

---

## ⚠️ COSE IMPORTANTI

### 1. Sempre Eliminare i Cluster!
```bash
# Lista cluster attivi
gcloud dataproc clusters list --region=europe-west1

# Elimina TUTTI
gcloud dataproc clusters delete CLUSTER_NAME --region=europe-west1
```

### 2. Controlla i Costi
```bash
# Verifica che il JAR esista
ls -la target/scala-2.13/earthquake-application.jar

# Se non esiste, ricompila
sbt clean assembly
```

### 3. Dataset Corretto
- ❌ NON usare i dati di esempio dalla traccia!
- ✅ USA il file `earthquakes-full.csv` fornito dal prof
- ✅ O un dataset reale di terremoti

### 4. Formato Output
Il risultato REALE dipenderà dal TUO dataset. Formato:
```
((lat1, lon1), (lat2, lon2))
data1
data2
data3
...
```

---

## 🆘 Problemi Comuni

### "Permission denied" su Google Cloud
```bash
gcloud auth login
gcloud auth application-default login
```

### "Cluster creation failed"
- Verifica di avere crediti education attivi
- Prova una regione diversa (es. `us-central1`)
- Verifica quota workers nel tuo progetto

### "OutOfMemoryError"
- Aumenta `--driver-memory` e `--executor-memory`
- Usa AggregateByKey invece di GroupByKey

### Job troppo lento
- Aumenta numero di partizioni
- Usa AggregateByKey
- Verifica di non usare troppi workers (overhead)

---

## 📝 Per il Report

Dopo aver eseguito gli esperimenti:

1. ✅ Apri `final_metrics_*.csv` in Excel/Google Sheets
2. ✅ Crea tabelle pivot per raggruppare dati
3. ✅ Genera grafici:
   - Tempo vs Workers (linee)
   - Speedup vs Workers (linee)
   - Confronto approcci (barre)
   - Hash vs Range (barre)
4. ✅ Calcola metriche con formule Excel
5. ✅ Spiega i risultati osservati

---

## 💡 Consigli Finali

1. **Inizia SEMPLICE**: Prima un test locale, poi uno su Cloud
2. **Un passo alla volta**: Non lanciare tutti gli esperimenti insieme
3. **Verifica sempre**: Controlla che i risultati siano sensati
4. **Documenta tutto**: Salva log e screenshot per il report
5. **Elimina cluster**: SEMPRE eliminare dopo ogni uso

---

## 🚀 Quick Start TL;DR

```bash
# LOCALE
sbt assembly
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar \
  test-data.csv output 4 aggregatebykey hash 1

# CLOUD (completo automatico)
./run-complete-experiments.sh YOUR_BUCKET earthquakes-full.csv

# Risultati
cat final_metrics_*.csv
```

Fatto! 🎉
