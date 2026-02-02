# Earthquake Application

Progetto per il corso di Scalable and Cloud Programming - Analisi distribuita di co-occorrenze di terremoti usando Scala + Apache Spark.

## 📋 Descrizione

Il progetto implementa un'analisi distribuita su dataset di terremoti per trovare la coppia di località che co-occorre più frequentemente, insieme alle date di co-occorrenza ordinate.

### Caratteristiche principali:
- **Arrotondamento coordinate**: Latitudine e longitudine → prima cifra decimale
- **Finestra temporale**: Co-occorrenza basata su giorni (yyyy-MM-dd)
- **Rimozione dei duplicati**: Eventi nella stessa cella geografica e data trattati come unici
- **Tre approcci diversi**: GroupByKey, AggregateByKey, ReduceByKey
- **Partizionamento Hash**: con  `repartition()` per controllo parallelismo
- **Scalabilità**: Testabile su cluster 2, 3, 4 worker nodes

## 🔬 Tre Approcci Implementati

L'analisi è stata eseguita utilizzando **tre approcci diversi**:

1. **GroupByKey**: Raggruppamento semplice per data seguito da generazione coppie
2. **AggregateByKey**: Aggregazione efficiente in Set per località per data
3. **ReduceByKey**: Riduzione distribuita per deduplicazione e aggregazione

Tutti gli approcci utilizzano **Hash Partitioning** tramite il metodo `repartition()` di Spark per garantire distribuzione uniforme dei dati e controllo esplicito del parallelismo.

## 📁 Struttura del Progetto

```
project/
├── src/main/scala/
│   ├── analysis/
│   │   ├── CoOccurrenceAnalysis.scala  # 3 APPROCCI + Hash partitioning
│   │   ├── EarthquakeEvent.scala
│   │   ├── Location.scala
│   │   ├── LocationPair.scala
│   │   ├── AnalysisResult.scala
│   │   ├── ExecutionMetrics.scala
│   │   └── MetricsCollector.scala      # Sistema metriche automatico
│   ├── extraction/
│   │   └── DataExtractor.scala
│   ├── utils/
│   │   └── Utils.scala
│   └── Main.scala
├── build.sbt
└── README.md
```

## 🚀 Quick Start

### Compilazione

```bash
sbt clean compile
sbt assembly
```

Il JAR finale sarà in: `target/scala-2.12/earthquake-application.jar`

### Test Locale

```bash
spark-submit \
  --class Main \
  --master local[*] \
  target/scala-2.12/earthquake-application.jar \
  test-data.csv \
  output-test \
  8 \
  groupbykey \
  1
```

### Parametri

```
<input-file> <output-dir> <num-partitions> <approach> <num-workers>
```

**Parametri:**
- `input-file`: Path al file CSV con dati terremoti
- `output-dir`: Directory di output per risultati e metriche
- `num-partitions`: Numero di partizioni per `repartition()` (default: 8)
- `approach`: `groupbykey` | `aggregatebykey` | `reducebykey` (default: groupbykey)
- `num-workers`: Numero worker nodes nel cluster (default: 1)

## 📊 Sistema Metriche Automatico

Il progetto genera automaticamente un file CSV con tutte le metriche necessarie per l'analisi delle performance.

### Struttura del CSV Metriche

```csv
approach,num_workers,num_partitions,total_events,unique_events,co_occurrences,
load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp
```

### Campi delle Metriche

| Campo | Descrizione | Unità |
|-------|-------------|-------|
| approach | Approccio utilizzato | GroupByKey/AggregateByKey/ReduceByKey |
| num_workers | Numero di worker nodes | int |
| num_partitions | Numero di partizioni | int |
| total_events | Eventi totali caricati | count |
| unique_events | Eventi unici dopo dedup | count |
| co_occurrences | Coppie co-occorrenze trovate | count |
| load_time_ms | Tempo caricamento dati | milliseconds |
| analysis_time_ms | Tempo analisi | milliseconds |
| total_time_ms | Tempo totale esecuzione | milliseconds |
| max_count | Co-occorrenze coppia massima | count |
| timestamp | Timestamp esecuzione | epoch milliseconds |

### File Generati

Per ogni esecuzione vengono generati:

1. **`output/part-*`** - Risultato dell'analisi (coppia + date)
2. **`output/metrics/part-*`** - Metriche in formato CSV
3. **`output/metrics-readable/part-*`** - Metriche in formato leggibile

## 📝 Formato Output

```
((37.5, 15.3), (38.1, 13.4))
2024-03-12
2024-04-01
2024-04-03
```

- **Prima riga**: Coppia di località (lat1, lon1), (lat2, lon2) con massime co-occorrenze
- **Righe successive**: Date delle co-occorrenze in ordine cronologico crescente

## ☁️ Esecuzione su Google Cloud Dataproc

### Setup Iniziale

```bash
# Definisci variabili
PROJECT_ID="your-project-id"
BUCKET="your-bucket-name"
REGION="europe-west1"

# Crea bucket (se non esiste)
gcloud storage buckets create gs://$BUCKET --location=$REGION

# Upload JAR
gcloud storage cp target/scala-2.12/earthquake-application.jar \
  gs://$BUCKET/jars/

# Upload dataset
gcloud storage cp dataset-earthquakes-full.csv \
  gs://$BUCKET/data/
```

### Crea Cluster

**Configurazione obbligatoria (n2-standard-4):**

```bash
# Cluster 2 workers (12 vCPU totali)
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

**Note configurazione:**
- Tipo macchina: **n2-standard-4** (4 vCPU, 16GB RAM) per tutte le macchine
- Executor memory: 10GB (lascia buffer per overhead)
- Driver memory: 6GB
- Boot disk: 240GB (per dataset grandi)

### Esegui Job

```bash
# Esempio: GroupByKey con 16 partizioni su cluster 2 workers
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2w \
  --region=$REGION \
  --jar=gs://$BUCKET/jars/earthquake-application.jar \
  -- gs://$BUCKET/data/dataset-earthquakes-full.csv \
     gs://$BUCKET/output/2w-16p-groupbykey \
     16 \
     groupbykey \
     2
```

### Scarica Risultati

```bash
# Scarica output completo
gcloud storage cp -r gs://$BUCKET/output/2w-16p-groupbykey ./results/

# Scarica solo metriche CSV
gcloud storage cat gs://$BUCKET/output/2w-16p-groupbykey/metrics/part-* > metrics.csv
```

### Elimina Cluster

```bash
gcloud dataproc clusters delete earthquake-cluster-2w --region=$REGION --quiet
```

## 🔧 Requisiti

### Software
- **Java**: JDK 11
- **Scala**: 2.12.x
- **SBT**: 1.5.x o superiore
- **Apache Spark**: 3.5.x
- **Google Cloud SDK**: Latest (per esecuzione cloud)

### Quota Google Cloud

Per testare tutte le configurazioni (2, 3, 4 workers con n2-standard-4):
- **Quota minima**: 12 vCPU (solo 2 workers)
- **Quota raccomandata**: 24 vCPU (tutte le configurazioni)

## 📚 Documentazione Aggiuntiva

### File di Progetto

- **[COMPLETE-GUIDE.md](COMPLETE-GUIDE.md)**: Guida completa setup e testing
- **Scaladoc**: Generata in `target/scala-2.12/api/index.html` dopo compilazione

---

**Autore**: Nicola Modugno  
**Corso**: Scalable and Cloud Programming  
**A.A.**: 2025-26  
**Università di Bologna**
