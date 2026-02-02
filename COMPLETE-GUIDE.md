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
│   ├── extraction/
│   └── utils/
│       └── Utils.scala
├── build.sbt
└── project/
```

### 2. Dataset di Test

Crea `test-data.csv`:

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
- Time in formato ISO8601: `YYYY-MM-DDTHH:MM:SS.sssZ`
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
# Test con approccio 1 (GroupByKey) e Hash partitioner
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
# Parametri
BUCKET_NAME="earthquake-analysis-TUOMATRICOLA"  # Il TUO bucket!
CLUSTER_NAME="earthquake-cluster-2w"
REGION="europe-west1"

# Esegui job con GroupByKey e Hash partitioner
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=$REGION \
  --jar=gs://$BUCKET/jars/earthquake-application.jar \
  -- gs://$BUCKET/data/dataset-earthquakes-full.csv \
     gs://$BUCKET/output/2w-16p-aggregatebykey \
     16 aggregatebykey 2
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

### Job Timeout

**Diagnosi:**
```bash
# Verifica job attivo
gcloud dataproc jobs list --region=$REGION --limit=5

# Vedi log
gcloud dataproc jobs describe JOB_ID --region=$REGION
```

**Soluzioni:** Aumenta partizioni o verifica configurazione cluster

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
