# Earthquake Application - Guida Completa

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
# Output atteso: version 3.5.x o compatibile
```

### Step 1: Compila il Progetto

```bash
cd /path/to/your/project

# Verifica installazione
gcloud version

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
cd EarthquakeAnalysis-SCP
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
time,latitude,longitude,depth,mag
2024-03-12T02:10:00.000Z,37.502,15.312,10.0,3.5
2024-03-12T05:55:00.000Z,38.112,13.372,15.0,2.8
2024-03-12T04:32:00.000Z,37.521,15.324,8.0,3.2
2024-04-01T14:22:00.000Z,37.547,15.323,12.0,4.1
2024-04-01T21:55:00.000Z,38.147,13.324,9.0,3.0
2024-04-03T12:32:00.000Z,37.535,15.341,11.0,2.5
2024-04-03T18:33:00.000Z,38.142,13.387,14.0,3.8
2024-04-03T21:10:00.000Z,43.769,11.255,7.0,2.9
2024-03-12T23:45:00.000Z,37.498,15.289,13.0,3.1
```

**IMPORTANTE**: Il file DEVE avere:
- Header con: `time,latitude,longitude` (minimo, altre colonne opzionali)
- Time in formato ISO8601: `YYYY-MM-DDTHH:MM:SS.sssZ`
- Coordinate decimali

---

## Compilazione

### Pulizia e Compilazione Completa

I seguenti comandi vanno eseguiti nella **root** del repository:

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

**Tempo atteso:** 30-60 secondi per `sbt assembly`

### Verifica JAR Creato

```bash
# Linux/Mac
ls -lh target/scala-2.12/earthquake-application.jar

# Windows (PowerShell)
Get-Item target\scala-2.12\earthquake-application.jar | Select-Object Length,Name

# Windows (CMD)
dir target\scala-2.12\earthquake-application.jar
```

**Size atteso:** ~15-20 MB

---

## Test Locali

### Sintassi Base

```bash
# Test con approccio 1 (GroupByKey) e Hash partitioner
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.12/earthquake-application.jar \
  <INPUT_FILE> \
  <OUTPUT_DIR> \
  <NUM_PARTITIONS> \
  <APPROACH> \
  <NUM_WORKERS>
```

### Specifiche Parametri

| Parametro | Descrizione | Valori | Default |
|-----------|-------------|--------|---------|
| INPUT_FILE | Path al CSV | es. `test-data.csv` | - |
| OUTPUT_DIR | Directory output | es. `output-test` | - |
| NUM_PARTITIONS | Numero partizioni | 4, 8, 16, 32 | 8 |
| APPROACH | Algoritmo | `groupbykey`, `aggregatebykey`, `reducebykey` | `groupbykey` |
| NUM_WORKERS | Numero workers | 1 (locale), 2-4 (cloud) | 1 |

**NOTA:** Il progetto usa sempre **Hash Partitioning** tramite `repartition()`.

---

## Esempi per Ogni Approccio

### Approccio 1: GroupByKey

```bash
# Linux/Mac/Git Bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv \
  output-local-test \
  4 \
  groupbykey \
  1

# Windows CMD
spark-submit ^
  --class Main ^
  --master "local[*]" ^
  --driver-memory 2g ^
  target\scala-2.12\earthquake-application.jar ^
  test-data.csv ^
  output-groupbykey ^
  4 ^
  groupbykey ^
  1
```

**Caratteristiche:**
- Shuffle completo di tutti i dati
- Memoria elevata richiesta
- Baseline per confronto performance

---

### Approccio 2: AggregateByKey

```bash
# Linux/Mac/Git Bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv \
  output-aggregatebykey \
  4 \
  aggregatebykey \
  1

# Windows CMD
spark-submit ^
  --class Main ^
  --master "local[*]" ^
  --driver-memory 2g ^
  target\scala-2.12\earthquake-application.jar ^
  test-data.csv ^
  output-aggregatebykey ^
  4 ^
  aggregatebykey ^
  1
```

**Caratteristiche:**
- Aggregazione locale prima dello shuffle
- Memoria media richiesta
- Tipicamente 40-50% più veloce di GroupByKey

---

### Approccio 3: ReduceByKey

```bash
# Linux/Mac/Git Bash
spark-submit \
  --class Main \
  --master local[*] \
  --driver-memory 2g \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv \
  output-reducebykey \
  4 \
  reducebykey \
  1

# Windows CMD
spark-submit ^
  --class Main ^
  --master "local[*]" ^
  --driver-memory 2g ^
  target\scala-2.12\earthquake-application.jar ^
  test-data.csv ^
  output-reducebykey ^
  4 ^
  reducebykey ^
  1
```

**Caratteristiche:**
- Riduzione distribuita ottimizzata
- Memoria bassa richiesta
- Tipicamente 50-60% più veloce di GroupByKey

---

## Test con Diverse Partizioni

### Impatto Numero Partizioni

```bash
# Sottoutilizzo (4 partitions su 8 vCPU)
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output-4p 4 aggregatebykey 1

# Ottimale (16 partitions su 8 vCPU = 2× vCPU)
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output-16p 16 aggregatebykey 1

# Alto parallelismo (32 partitions su 8 vCPU = 4× vCPU)
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output-32p 32 aggregatebykey 1

# Overhead (64 partitions su 8 vCPU = 8× vCPU)
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output-64p 64 aggregatebykey 1
```

**Regola empirica:** Zona ottimale = **2-4× numero vCPU**

---

## Verifica Risultati Locali

### Visualizza Output Principale

```bash
# Linux/Mac/Git Bash
cat output-aggregatebykey/part-00000

# Windows CMD
type output-aggregatebykey\part-00000

# Windows PowerShell
Get-Content output-aggregatebykey\part-00000
```

**Formato output atteso:**
```
((37.5, 15.3), (38.1, 13.4))
2024-03-12
2024-04-01
2024-04-03
```

- **Riga 1:** Coppia località con massime co-occorrenze
- **Righe successive:** Date ordinate cronologicamente

---

### Visualizza Metriche

```bash
# Linux/Mac/Git Bash
cat output-aggregatebykey/metrics/part-00000

# Windows CMD
type output-aggregatebykey\metrics\part-00000
```

**Formato metriche CSV:**

```csv
approach,num_workers,num_partitions,total_events,unique_events,co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp
AggregateByKey,1,4,9,9,12,523,1247,1770,3,1735678900000
```

**Campi:**
- `approach`: GroupByKey/AggregateByKey/ReduceByKey
- `num_workers`: Numero workers (1 per locale)
- `num_partitions`: Partizioni usate con `repartition()`
- `total_events`: Eventi caricati dal CSV
- `unique_events`: Eventi dopo deduplicazione
- `co_occurrences`: Coppie trovate
- `load_time_ms`: Tempo caricamento (ms)
- `analysis_time_ms`: Tempo analisi (ms)
- `total_time_ms`: Tempo totale (ms)
- `max_count`: Co-occorrenze coppia vincente
- `timestamp`: Epoch timestamp

---

## Esecuzione su Google Cloud

### Setup Google Cloud Storage

```bash
# 1. Definisci variabili
export BUCKET_NAME="bucket_scp_1"
export PROJECT_ID="your-project-id"
export REGION="europe-west1"

# 2. Configura progetto
gcloud config set project $PROJECT_ID

# 3. Crea bucket
gcloud storage buckets create gs://$BUCKET_NAME --location=$REGION

# 4. Upload JAR
gcloud storage cp target/scala-2.12/earthquake-application.jar \
  gs://$BUCKET_NAME/jars/

# 5. Upload dataset
gcloud storage cp dataset-earthquakes-full.csv \
  gs://$BUCKET_NAME/data/

# 6. Verifica upload
gcloud storage ls gs://$BUCKET_NAME/jars/
gcloud storage ls gs://$BUCKET_NAME/data/
```

---

### Creazione Cluster Dataproc

#### Cluster 2 Workers (12 vCPU totali)

```bash
gcloud dataproc clusters create earthquake-cluster-2w \
  --region=$REGION \
  --image-version=2.1-debian11 \
  --num-workers 2 \
  --master-boot-disk-size 240 \
  --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4 \
  --properties=spark:spark.executor.memory=10g,spark:spark.driver.memory=6g,spark:spark.executor.memoryOverhead=2g,spark:spark.driver.memoryOverhead=1g
```

**Configurazione:**
- Master: n2-standard-4 (4 vCPU, 16GB RAM)
- Workers: 2× n2-standard-4
- Totale: 12 vCPU, 48GB RAM

#### Cluster 3 Workers (16 vCPU totali)

```bash
gcloud dataproc clusters create earthquake-cluster-3w \
  --region=$REGION \
  --image-version=2.1-debian11 \
  --num-workers 3 \
  --master-boot-disk-size 240 \
  --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4 \
  --properties=spark:spark.executor.memory=10g,spark:spark.driver.memory=6g,spark:spark.executor.memoryOverhead=2g,spark:spark.driver.memoryOverhead=1g
```

**Requisito quota:** 16 vCPU (richiede aumento quota free tier)

#### Cluster 4 Workers (20 vCPU totali)

```bash
gcloud dataproc clusters create earthquake-cluster-4w \
  --region=$REGION \
  --image-version=2.1-debian11 \
  --num-workers 4 \
  --master-boot-disk-size 240 \
  --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4 \
  --properties=spark:spark.executor.memory=10g,spark:spark.driver.memory=6g,spark:spark.executor.memoryOverhead=2g,spark:spark.driver.memoryOverhead=1g
```

**Requisito quota:** 20 vCPU (richiede aumento quota free tier)

---

### Esecuzione Job: Approccio 1 (GroupByKey)

```bash
# Parametri
BUCKET_NAME="earthquake-analysis-TUOMATRICOLA"  # Il TUO bucket!
CLUSTER_NAME="earthquake-cluster-2w"
REGION="europe-west1"

# Esegui job con GroupByKey e Hash partitioner
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=$REGION \
  --jar=gs://$BUCKET_NAME/jars/earthquake-application.jar \
  -- gs://$BUCKET_NAME/data/dataset-earthquakes-full.csv \
     gs://$BUCKET_NAME/output/2w-16p-groupbykey \
     16 \
     groupbykey \
     2

# Attendi che il job finisca (guarda nella console)
```

**Durata attesa (2w, dataset 3.4M eventi):** ~13-15 minuti

---

### Esecuzione Job: Approccio 2 (AggregateByKey)

```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=$REGION \
  --jar=gs://$BUCKET_NAME/jars/earthquake-application.jar \
  -- gs://$BUCKET_NAME/data/dataset-earthquakes-full.csv \
     gs://$BUCKET_NAME/output/2w-16p-aggregatebykey \
     16 \
     aggregatebykey \
     2
```

**Durata attesa (2w, dataset 3.4M eventi):** ~7-9 minuti

---

### Esecuzione Job: Approccio 3 (ReduceByKey)

```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=$REGION \
  --jar=gs://$BUCKET_NAME/jars/earthquake-application.jar \
  -- gs://$BUCKET_NAME/data/dataset-earthquakes-full.csv \
     gs://$BUCKET_NAME/output/2w-16p-reducebykey \
     16 \
     reducebykey \
     2
```

**Durata attesa (2w, dataset 3.4M eventi):** ~6-8 minuti

---

### Download Risultati

```bash
# Download output completo
gcloud storage cp -r gs://$BUCKET_NAME/output/2w-16p-aggregatebykey ./results/

# Visualizza risultato principale
gcloud storage cat gs://$BUCKET_NAME/output/2w-16p-aggregatebykey/part-*

# Visualizza metriche
gcloud storage cat gs://$BUCKET_NAME/output/2w-16p-aggregatebykey/metrics/part-* > metrics-2w-16p-aggregatebykey.csv

# Scarica solo metriche
gcloud storage cat gs://$BUCKET_NAME/output/*/metrics/part-* > all-metrics.csv
```

---

### Eliminazione Cluster

```bash
# IMPORTANTE: Elimina sempre il cluster per non consumare crediti!
gcloud dataproc clusters delete earthquake-cluster-2w --region=$REGION --quiet

# Verifica eliminazione
gcloud dataproc clusters list --region=$REGION
# Output: Listed 0 items.
```

---

## 🔬 PARTE 3: Esperimenti Completi (Per il Report)

### Matrice Esperimenti Raccomandata

**Dataset:** ~3.4M eventi  
**Approcci:** GroupByKey (GBK), AggregateByKey (ABK), ReduceByKey (RBK)  
**Partitioning:** Hash (via `repartition()`)

| # | Workers | vCPU | Partitions | Ratio | Approach | Durata | Costo |
|---|---------|------|------------|-------|----------|--------|-------|
| 1 | 2 | 12 | 8 | 0.67× | GBK | ~15 min | $0.05 |
| 2 | 2 | 12 | 16 | 1.33× | GBK | ~13 min | $0.04 |
| 3 | 2 | 12 | 32 | 2.67× | GBK | ~13 min | $0.04 |
| 4 | 2 | 12 | 48 | 4.00× | GBK | ~14 min | $0.05 |
| 5 | 2 | 12 | 16 | 1.33× | ABK | ~8 min | $0.03 |
| 6 | 2 | 12 | 32 | 2.67× | ABK | ~8 min | $0.03 |
| 7 | 2 | 12 | 16 | 1.33× | RBK | ~7 min | $0.02 |
| 8 | 2 | 12 | 32 | 2.67× | RBK | ~7 min | $0.02 |
| 9 | 3 | 16 | 12 | 0.75× | GBK | ~12 min | $0.06 |
| 10 | 3 | 16 | 24 | 1.50× | GBK | ~10 min | $0.05 |
| 11 | 3 | 16 | 36 | 2.25× | GBK | ~10 min | $0.05 |
| 12 | 3 | 16 | 24 | 1.50× | ABK | ~6 min | $0.03 |
| 13 | 3 | 16 | 24 | 1.50× | RBK | ~5 min | $0.03 |
| 14 | 4 | 20 | 16 | 0.80× | GBK | ~10 min | $0.06 |
| 15 | 4 | 20 | 32 | 1.60× | GBK | ~8 min | $0.05 |
| 16 | 4 | 20 | 48 | 2.40× | GBK | ~8 min | $0.05 |
| 17 | 4 | 20 | 32 | 1.60× | ABK | ~5 min | $0.03 |
| 18 | 4 | 20 | 32 | 1.60× | RBK | ~4 min | $0.03 |

**Totale:** 18 test, ~3-4 ore, **~$1.50**

---

### Script Automatizzato per Tutti gli Esperimenti

**Salva come `run-all-tests.sh`**:

```bash
chmod +x run-complete-experiments.sh

BUCKET="YOUR_BUCKET_NAME"
DATASET="earthquakes-full.csv"
JAR="gs://$BUCKET/jars/earthquake-application.jar"
DATA="gs://$BUCKET/data/$DATASET"
REGION="europe-west1"

# Funzione per creare cluster
create_cluster() {
  local workers=$1
  local cluster="earthquake-cluster-${workers}w"
  
  echo "Creating $cluster..."
  gcloud dataproc clusters create $cluster \
    --region=$REGION \
    --image-version=2.1-debian11 \
    --num-workers $workers \
    --master-boot-disk-size 240 \
    --worker-boot-disk-size 240 \
    --master-machine-type=n2-standard-4 \
    --worker-machine-type=n2-standard-4 \
    --properties=spark:spark.executor.memory=10g,spark:spark.driver.memory=6g,spark:spark.executor.memoryOverhead=2g,spark:spark.driver.memoryOverhead=1g \
    --quiet
}

# Funzione per eseguire job
run_job() {
  local workers=$1
  local partitions=$2
  local approach=$3
  local cluster="earthquake-cluster-${workers}w"
  local output="gs://$BUCKET/output/${workers}w-${partitions}p-${approach}"
  
  echo "Running: $workers workers, $partitions partitions, $approach"
  
  gcloud dataproc jobs submit spark \
    --cluster=$cluster \
    --region=$REGION \
    --jar=$JAR \
    -- $DATA $output $partitions $approach $workers
    
  # Scarica metriche
  gcloud storage cat "${output}/metrics/part-*" > "metrics-${workers}w-${partitions}p-${approach}.csv" 2>/dev/null
}

# Funzione per eliminare cluster
delete_cluster() {
  local workers=$1
  local cluster="earthquake-cluster-${workers}w"
  echo "Deleting $cluster..."
  gcloud dataproc clusters delete $cluster --region=$REGION --quiet
  sleep 30
}

# ============================================
# TEST 2 WORKERS
# ============================================
echo "=== 2 WORKERS TESTS ==="
create_cluster 2

# GroupByKey con diverse partizioni
run_job 2 8  groupbykey
run_job 2 16 groupbykey
run_job 2 32 groupbykey
run_job 2 48 groupbykey

# Altri approcci (partizioni ottimali)
run_job 2 16 aggregatebykey
run_job 2 32 aggregatebykey
run_job 2 16 reducebykey
run_job 2 32 reducebykey

delete_cluster 2

# ============================================
# TEST 3 WORKERS (richiede quota 24 vCPU)
# ============================================
echo "=== 3 WORKERS TESTS ==="
create_cluster 3

run_job 3 12 groupbykey
run_job 3 24 groupbykey
run_job 3 36 groupbykey
run_job 3 24 aggregatebykey
run_job 3 24 reducebykey

delete_cluster 3

# ============================================
# TEST 4 WORKERS (richiede quota 24 vCPU)
# ============================================
echo "=== 4 WORKERS TESTS ==="
create_cluster 4

run_job 4 16 groupbykey
run_job 4 32 groupbykey
run_job 4 48 groupbykey
run_job 4 32 aggregatebykey
run_job 4 32 reducebykey

delete_cluster 4

# ============================================
# RIEPILOGO
# ============================================
echo ""
echo "All tests completed!"
echo "Metrics files:"
ls -lh metrics-*.csv

echo ""
echo "Combine all metrics:"
echo "cat metrics-*.csv | grep -v '^approach' > all-metrics.csv"
echo "echo 'approach,num_workers,num_partitions,...' | cat - all-metrics.csv > final-metrics.csv"
```

**Esecuzione:**
```bash
chmod +x run-all-tests.sh
./run-all-tests.sh
```

**Nota:** Test 3w e 4w richiedono **quota 24 vCPU**. Richiedi aumento su:
https://console.cloud.google.com/iam-admin/quotas

---

## 📊 Interpretare le Metriche

### Unire Tutti i CSV Metriche

```bash
# Linux/Mac
cat metrics-*.csv | grep -v '^approach' | sort > all-data.csv
echo 'approach,num_workers,num_partitions,total_events,unique_events,co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp' | cat - all-data.csv > final-metrics.csv

# Windows PowerShell
Get-Content metrics-*.csv | Select-String -Pattern '^approach' -NotMatch | Sort-Object > all-data.csv
```

---

### Calcolo Speedup

```
Speedup(config) = T_baseline / T_config

Baseline: 2 workers, GroupByKey, 16 partitions
```

**Esempio Python:**

```python
import pandas as pd

df = pd.read_csv('final-metrics.csv')
df['analysis_time_sec'] = df['analysis_time_ms'] / 1000

baseline = df[(df['num_workers']==2) & 
              (df['approach']=='GroupByKey') & 
              (df['num_partitions']==16)]['analysis_time_sec'].values[0]

df['speedup'] = baseline / df['analysis_time_sec']

print(df[['approach', 'num_workers', 'num_partitions', 'speedup']])
```

---

### Calcolo Strong Scaling Efficiency

```
Efficiency(n) = Speedup(n) / n
dove n = numero workers
```

**Esempio:**
```
T(2) = 120s
T(4) = 40s

Speedup(4) = 120 / 40 = 3.0
Efficiency(4) = 3.0 / 4 = 0.75 = 75%
```

**Interpretazione:**
- 100%: Scaling perfetto
- 75-90%: Scaling buono
- <75%: Overhead significativo

---

### Grafici Consigliati

#### 1. Impatto Partizioni (GroupByKey)

```python
import matplotlib.pyplot as plt

gbk = df[df['approach'] == 'GroupByKey']
for workers in [2, 3, 4]:
    data = gbk[gbk['num_workers'] == workers]
    plt.plot(data['num_partitions'], data['analysis_time_sec'], 
             label=f'{workers} workers', marker='o')

plt.xlabel('Number of Partitions')
plt.ylabel('Time (seconds)')
plt.title('GroupByKey - Impact of Partitions')
plt.legend()
plt.grid(True)
plt.savefig('impact_partitions.png')
plt.show()
```

#### 2. Confronto Approcci (2 workers, 16 partitions)

```python
data = df[(df['num_workers']==2) & (df['num_partitions']==16)]
plt.bar(data['approach'], data['analysis_time_sec'])
plt.xlabel('Approach')
plt.ylabel('Time (seconds)')
plt.title('Approach Comparison (2w, 16p)')
plt.xticks(rotation=45)
plt.tight_layout()
plt.savefig('comparison_approaches.png')
plt.show()
```

#### 3. Scalabilità Workers

```python
for approach in ['GroupByKey', 'AggregateByKey', 'ReduceByKey']:
    data = df[df['approach'] == approach]
    # Usa partizioni ottimali: 2× vCPU per ogni config
    opt_data = data[((data['num_workers']==2) & (data['num_partitions']==16)) |
                    ((data['num_workers']==3) & (data['num_partitions']==24)) |
                    ((data['num_workers']==4) & (data['num_partitions']==32))]
    plt.plot(opt_data['num_workers'], opt_data['analysis_time_sec'],
             label=approach, marker='o')

plt.xlabel('Number of Workers')
plt.ylabel('Time (seconds)')
plt.title('Scalability by Workers (optimal partitions)')
plt.legend()
plt.grid(True)
plt.savefig('scalability_workers.png')
plt.show()
```

#### 4. Zona Ottimale Partitions/vCPU

```python
df['partitions_per_vcpu'] = df['num_partitions'] / (df['num_workers'] * 4)
df['time_normalized'] = df['analysis_time_sec'] / df.groupby('num_workers')['analysis_time_sec'].transform('min')

gbk = df[df['approach'] == 'GroupByKey']
plt.scatter(gbk['partitions_per_vcpu'], gbk['time_normalized'], s=100, alpha=0.6)
plt.axvspan(2, 4, alpha=0.2, color='green', label='Optimal zone (2-4× vCPU)')
plt.xlabel('Partitions / vCPU Ratio')
plt.ylabel('Normalized Time')
plt.title('Optimal Partitioning Zone')
plt.legend()
plt.grid(True)
plt.savefig('optimal_zone.png')
plt.show()
```

---

### Import in Excel

1. Apri Excel
2. Data → Get Data → From Text/CSV
3. Seleziona `final-metrics.csv`
4. Import

**Pivot Table per confronto:**
- Rows: `num_workers`
- Columns: `approach`
- Values: `analysis_time_ms` (Average)

**Grafici:**
- Insert → Charts → Line Chart
- X-axis: `num_partitions`
- Y-axis: `analysis_time_sec`
- Series: `approach`

---

## 🎯 Configurazioni da Testare

```bash
# === COMPILAZIONE ===
sbt clean assembly

# === TEST LOCALE ===
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv output 8 aggregatebykey 1

# === UPLOAD CLOUD ===
gcloud storage cp target/scala-2.12/earthquake-application.jar \
  gs://bucket_scp_1/jars/

# === CREA CLUSTER ===
gcloud dataproc clusters create earthquake-cluster-2w \
  --region=europe-west1 --num-workers 2 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4

# === SUBMIT JOB ===
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=europe-west1 \
  --jar=gs://bucket_scp_1/jars/earthquake-application.jar \
  -- gs://bucket_scp_1/data/dataset-earthquakes-full.csv \
     gs://bucket_scp_1/output/2w-16p-aggregatebykey \
     16 aggregatebykey 2

# === DOWNLOAD METRICHE ===
gcloud storage cat gs://bucket_scp_1/output/*/metrics/part-* > all-metrics.csv

# === ELIMINA CLUSTER ===
gcloud dataproc clusters delete earthquake-cluster-2w \
  --region=europe-west1 --quiet

# === VERIFICA QUOTA ===
gcloud compute project-info describe --format="value(quotas)" | grep CPUS
```

---

## Troubleshooting

### Problema: 0 Eventi Caricati

**Sintomi:**
```
Total events loaded: 0
Unique events after deduplication: 0
```

**Soluzioni:**

### 1. Sempre Eliminare i Cluster!
```bash
# 1. Verifica file esista
ls -la test-data.csv

# 2. Verifica formato CSV
head -5 test-data.csv
# Deve avere header: time,latitude,longitude,...

# 3. Verifica encoding (deve essere UTF-8)
file test-data.csv

# 4. Usa path assoluto
spark-submit ... $(pwd)/test-data.csv ...
```

---

### Problema: NoClassDefFoundError

**Sintomi:**
```
Error: Failed to load class Main
```

**Soluzioni:**

```bash
# Verifica JAR esista
ls -la target/scala-2.12/earthquake-application.jar

# Se manca, ricompila
sbt clean assembly

# Verifica classe Main nel JAR
jar tf target/scala-2.12/earthquake-application.jar | grep Main

# Dovrebbe mostrare: Main.class
```

---

### Problema: OutOfMemoryError

**Sintomi:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Soluzioni:**

```bash
# LOCALE: Aumenta driver memory
spark-submit --driver-memory 4g --executor-memory 4g ...

# CLOUD: Riduci executor memory o aumenta partizioni
gcloud dataproc clusters create ... \
  --properties=spark:spark.executor.memory=8g,...

# Oppure aumenta partizioni
... 32 aggregatebykey 2  # invece di 8
```

---

### Problema: Quota vCPU Insufficiente (Google Cloud)

**Sintomi:**
```
ERROR: Insufficient 'CPUS_ALL_REGIONS' quota. Requested 16.0, available 12.0
```

**Soluzioni:**

1. **Verifica quota attuale:**
```bash
gcloud compute project-info describe --format="value(quotas)" | grep CPUS
# Output: CPUS_ALL_REGIONS: 0.0/12.0
```

2. **Richiedi aumento quota:**
   - Vai su: https://console.cloud.google.com/iam-admin/quotas
   - Cerca: "CPUS_ALL_REGIONS"
   - Richiedi: 24 vCPU
   - Giustificazione: "University project, testing Spark with 2-4 worker clusters"
   - Attesa: 1-2 giorni

3. **Alternativa temporanea (solo 2 workers):**
```bash
# Usa solo configurazione 2w (12 vCPU)
gcloud dataproc clusters create earthquake-cluster-2w ...
```

---

### Problema: Permission Denied (GCS)

**Sintomi:**
```
403 Forbidden
Access denied
```

**Soluzioni:**

```bash
# 1. Re-autentica
gcloud auth login
gcloud auth application-default login

# 2. Verifica progetto
gcloud config get-value project

# 3. Verifica ruolo
gcloud projects get-iam-policy PROJECT_ID \
  --flatten="bindings[].members" \
  --filter="bindings.members:user:YOUR_EMAIL"

# 4. Se necessario, aggiungi ruolo Storage Admin
gcloud projects add-iam-policy-binding PROJECT_ID \
  --member="user:YOUR_EMAIL" \
  --role="roles/storage.admin"
```

---

### Problema: Job Timeout o Lentezza

**Sintomi:**
```
Job running for >30 minutes
```

**Diagnosi:**

```bash
# Verifica job attivo
gcloud dataproc jobs list --region=europe-west1 --limit=5

# Vedi log job
gcloud dataproc jobs describe JOB_ID --region=europe-west1

# Vedi log Spark UI
# Nel browser: https://CLUSTER_NAME-m:8088
```

**Soluzioni:**

1. **Aumenta partizioni** (se poche)
2. **Riduci executor memory** (se OOM)
3. **Verifica configurazione cluster** (tipo macchina corretto?)
4. **Controlla dataset size** (troppo grande?)

---

### Problema: ClassCastException (se usi vecchio codice)

**Sintomi:**
```
java.lang.ClassCastException: Location cannot be cast to java.lang.String
```

**Soluzione:**
Assicurati di usare la versione aggiornata del codice che usa **solo Hash partitioning via `repartition()`**. Il bug era legato a RangePartitioner che è stato rimosso.

---

## Best Practices

### Performance

1. **Partizioni:** Usa 2-4× numero vCPU
2. **Memoria:** Lascia 2-4GB overhead per OS
3. **Persistence:** Usa `.persist()` solo su RDD riutilizzati
4. **Shuffle:** Preferisci `reduceByKey` a `groupByKey`

### Costi Google Cloud

1. **Elimina cluster subito dopo uso**
2. **Usa preemptible workers** (se possibile)
3. **Monitora billing dashboard**
4. **Set budget alerts** ($5, $10, $20)

### Debugging

1. **Test locale prima** di cloud
2. **Usa dataset piccolo** per debug
3. **Controlla log Spark UI**
4. **Verifica metriche CSV** per bottleneck

---

**Autore:** Nicola Modugno  
**Corso:** Scalable and Cloud Programming  
**A.A.:** 2024-25
