# Earthquake Application - Guida Completa

Guida operativa per l'esecuzione del progetto di analisi distribuita di co-occorrenze sismiche.

---

## 📋 Indice

1. [Prerequisiti](#prerequisiti)
2. [Setup Iniziale](#setup-iniziale)
3. [Compilazione](#compilazione)
4. [Esecuzione Locale](#esecuzione-locale)
5. [Esecuzione degli esperimenti](#esecuzione-degli-esperimenti)
6. [Troubleshooting](#troubleshooting)

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

## Esecuzione degli esperimenti
Esegui nel terminale:
```bash
./runexps.sh
```
Comparirà la seguente schermata in cui dovrà essere compilato ciascun campo.
```bash
  CONFIGURAZIONE PARAMETRI

Inserisci i parametri richiesti:

BUCKET (Nome del bucket GCS): il_mio_bucket
JAR (Path completo al JAR, es: gs://bucket/jars/app.jar): path_al_jar
DATASET: path_al_datasetdataset, es: gs://bucket/data/file.csv): path_al_datas  REGION:  path_al_dataset
REGION (Regione GCP, es: europe-west1): regione
Confermi e procedi? (y/n): y
```
Dopo aver inserito i dati e aver confermato comparirà la seguente schermata in cui è possibile scegliere quali esperimenti eseguire o se eseguire la cancellazione dei cluster (questa verrà fatta in automatico alla fine di ogni esperimento)
```bash
  RIEPILOGO CONFIGURAZIONE
  EARTHQUAKE CO-OCCURRENCE EXPERIMENTS
  JAR:     path_al_ar
Test pianificati:
  1. Cluster 2 workers (8 vCPU): 8, 16, 32, 48 partizioni
  2. Cluster 3 workers (12 vCPU): 16, 32, 48 partizioni
  3. Cluster 4 workers (16 vCPU): 16, 32, 48 partizioni

Approcci da testare:
  - GroupByKey
  - AggregateByKey
  - ReduceByKey

Opzioni:
  1) Esegui tutti i test (raccomandato)
  2) Solo cluster 2 workers
  3) Solo cluster 3 workers
  4) Solo cluster 4 workers
  5) Pulisci tutti i cluster
  6) Esci

Scelta (1-6):
```
Al termine degli esperimenti il programma scaricherà automaticamente i risultati in formato csv.
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

### Errore nella cancellazione di una cartella di output

**Sintomi:** `? Error during execution: Output directory gs://your_bucket/output/[...] already exists`

```bash
gcloud storage rm -r gs://your_bucket/output/
./runexps.sh
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

# Esegui gli esperimenti
./runexps.sh
```

---

**Autore:** Nicola Modugno  
**Corso:** Scalable and Cloud Programming  
**A.A.:** 2024-25
