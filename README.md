# Earthquake Application

Progetto per il corso di Scalable and Cloud Programming - Analisi distribuita di co-occorrenze di terremoti usando Scala + Apache Spark.

## 📋 Descrizione

Il progetto implementa un'analisi distribuita su dataset di terremoti per trovare la coppia di località che co-occorre più frequentemente, insieme alle date di co-occorrenza ordinate.

### Caratteristiche principali:
- **Arrotondamento coordinate**: Latitudine e longitudine → prima cifra decimale
- **Finestra temporale**: Co-occorrenza basata su giorni (yyyy-MM-dd)
- **Rimozione dei duplicati**: Eventi nella stessa cella geografica e data trattati come unici
- **Tre approcci diversi**: GroupByKey, AggregateByKey, ReduceByKey
- **Partizionamento Hash**: Uso esplicito di `repartition()` per controllo parallelismo
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

**Esempi:**

```bash
# GroupByKey con 16 partizioni, 2 workers
./app input.csv output 16 groupbykey 2

# AggregateByKey con 32 partizioni, 3 workers
./app input.csv output 32 aggregatebykey 3

# ReduceByKey con 48 partizioni, 4 workers
./app input.csv output 48 reducebykey 4
```

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

### Uso delle Metriche

Le metriche CSV possono essere:
- Importate in Excel/Google Sheets per analisi
- Usate per calcolare Speedup ed Efficiency
- Aggregate per generare grafici comparativi
- Analizzate per identificare configurazioni ottimali
- Incluse nel report del progetto

**Esempio analisi:**
```python
import pandas as pd
df = pd.read_csv('metrics.csv')
df['analysis_time_sec'] = df['analysis_time_ms'] / 1000
df.groupby(['approach', 'num_partitions'])['analysis_time_sec'].mean()
```

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

**Configurazione raccomandata (n2-standard-4):**

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

## 🧪 Testing su Configurazioni Multiple

### Script Automatizzato

Per testare multiple configurazioni di partizioni e approcci:

```bash
# Test su cluster 2 workers con diverse partizioni
for PARTITIONS in 8 16 32 48; do
  for APPROACH in groupbykey aggregatebykey reducebykey; do
    gcloud dataproc jobs submit spark \
      --cluster=earthquake-cluster-2w \
      --region=$REGION \
      --jar=gs://$BUCKET/jars/earthquake-application.jar \
      -- gs://$BUCKET/data/dataset-earthquakes-full.csv \
         gs://$BUCKET/output/2w-${PARTITIONS}p-${APPROACH} \
         $PARTITIONS \
         $APPROACH \
         2
  done
done
```

### Configurazioni Raccomandate

**Regola empirica: 2-4× il numero di vCPU disponibili**

| Cluster | vCPU | Partizioni Raccomandate |
|---------|------|------------------------|
| 2 workers | 12 | 16, 24, 32, 48 |
| 3 workers | 16 | 24, 32, 48, 64 |
| 4 workers | 20 | 32, 48, 64, 80 |

**Nota:** La zona ottimale tipicamente è 2-4× vCPU. Oltre 6× si osserva overhead di scheduling.

## 📊 Analisi Risultati

### Confronto Approcci

I tre approcci hanno caratteristiche diverse:

| Approccio | Shuffling | Memoria | Performance Attesa |
|-----------|-----------|---------|-------------------|
| **GroupByKey** | Alto | Alta | Baseline (100%) |
| **AggregateByKey** | Medio | Media | ~40-50% più veloce |
| **ReduceByKey** | Basso | Bassa | ~50-60% più veloce |

### Impatto Partizionamento

**Sottopartizionamento (partitions < 2× vCPU):**
- CPU sottoutilizzata
- Performance: -10-15%

**Ottimale (partitions = 2-4× vCPU):**
- Bilanciamento ideale
- Performance: massima

**Sovrapartizionamento (partitions > 6× vCPU):**
- Overhead scheduling
- Performance: -5-10%

### Grafici Consigliati per Report

1. **Impatto Partizioni**: Tempo vs Numero Partizioni (per approccio)
2. **Confronto Approcci**: Tempo vs Approccio (per configurazione workers)
3. **Scalabilità**: Speedup vs Numero Workers
4. **Zona Ottimale**: Performance vs Partitions/vCPU Ratio

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

Richiedi aumento quota su: https://console.cloud.google.com/iam-admin/quotas

### Dataset

Il dataset deve contenere almeno queste colonne CSV:
- `time`: timestamp in formato ISO8601 (yyyy-MM-dd'T'HH:mm:ss.SSSZ)
- `latitude`: latitudine decimale
- `longitude`: longitudine decimale

Altre colonne (magnitude, depth, etc.) vengono ignorate.

## 🐛 Troubleshooting

### OutOfMemoryError

```
Soluzione: Ridurre executor memory o aumentare partizioni
--conf spark.executor.memory=8g
--conf spark.executor.memoryOverhead=2g
```

### Job troppo lento

```
Causa: Numero partizioni subottimale
Soluzione: Testare 2-4× numero vCPU cluster
```

### Quota vCPU insufficiente

```
Errore: CPUS_ALL_REGIONS quota exceeded
Soluzione: Richiedere aumento quota o ridurre workers/machine-type
```

### Cluster creation timeout

```
Causa: Region sovraccarica o quota esaurita
Soluzione: Cambiare region o attendere
```

## 📚 Documentazione Aggiuntiva

### File di Progetto

- **[COMPLETE-GUIDE.md](COMPLETE-GUIDE.md)**: Guida completa setup e testing
- **[DEPLOYMENT_FINALE_COMPLETO.md](DEPLOYMENT_FINALE_COMPLETO.md)**: Istruzioni deployment cloud
- **Scaladoc**: Generata in `target/scala-2.12/api/index.html` dopo compilazione

### Risorse Esterne

- [Apache Spark Documentation](https://spark.apache.org/docs/latest/)
- [Google Cloud Dataproc](https://cloud.google.com/dataproc/docs)
- [Scala Documentation](https://docs.scala-lang.org/)

## 📈 Performance Attese

Con dataset ~3.4M eventi, cluster 2 workers (n2-standard-4, 16 partizioni):

| Approccio | Tempo Atteso | Memoria Peak |
|-----------|--------------|--------------|
| GroupByKey | ~13-15 min | ~12GB |
| AggregateByKey | ~7-9 min | ~8GB |
| ReduceByKey | ~6-8 min | ~6GB |

**Nota:** Tempi variano in base a configurazione cluster e carico GCP.

## 🎓 Considerazioni Didattiche

### Obiettivi di Apprendimento

Questo progetto dimostra:
- Uso di RDD transformations (map, filter, flatMap, groupByKey, reduceByKey, aggregateByKey)
- Gestione partizionamento con `repartition()`
- Confronto performance diversi approcci Spark
- Deploy e gestione cluster cloud
- Analisi scalabilità distribuita
- Raccolta e interpretazione metriche

### Limitazioni Conosciute

- Hash partitioning può creare sbilanciamento con dati skewed
- GroupByKey non ottimale per dataset molto grandi (>100M eventi)
- Free tier GCP limita testing a configurazioni piccole/medie

## 📄 Licenza

Progetto didattico per corso universitario.

---

**Autore**: Nicola Modugno  
**Corso**: Scalable and Cloud Programming  
**A.A.**: 2024-25  
**Università**: [Nome Università]
