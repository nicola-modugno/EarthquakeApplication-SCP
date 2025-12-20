# Earthquake Application

Progetto per il corso di Scalable and Cloud Programming - Analisi distribuita di co-occorrenze di terremoti usando Scala + Apache Spark.

## 📋 Descrizione

Il progetto implementa un'analisi distribuita su dataset di terremoti per trovare la coppia di località che co-occorre più frequentemente, insieme alle date di co-occorrenza ordinate.

### Caratteristiche principali:
- **Arrotondamento coordinate**: Latitudine e longitudine → prima cifra decimale
- **Finestra temporale**: Co-occorrenza basata su giorni (yyyy-MM-dd)
- **Rimozione dei duplicati**: Eventi nella stessa cella geografica e data trattati come unici
- **Tre approcci diversi**: Implementazioni con partitioner e funzioni differenti per confrontare le prestazioni
- **Scalabilità**: Testabile su cluster 2, 3, 4 worker nodes

## 🔬 Tre Approcci Implementati

L'analisi è stata eseguita utilizzando **tre approcci diversi**:

- GroupByKey<br> 
- AggregateByKey<br>
- ReduceByKey

con l'ausilio di due partitioner differenti:

- **Hash** <br>
- **Range**

## 📁 Struttura del Progetto

```
project/
├── src/main/scala/
│   ├── analysis/
│   │   ├── CoOccurrenceAnalysis.scala  # 3 APPROCCI + 2 PARTITIONER
│   │   ├── EarthquakeEvent.scala
│   │   ├── Location.scala
│   │   └── LocationPair.scala
│   ├── extraction/
│   │   └── DataExtractor.scala
│   ├── metrics/
│   │   └── MetricsCollector.scala      # Sistema metriche automatico
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

### Test Locale

```bash
spark-submit \
  --class Main \
  --master local[*] \
  target/scala-2.13/earthquake-cooccurrence-assembly-1.0.jar \
  test-data.csv \
  output-test \
  4 \
  aggregatebykey \
  hash \
  1
```

### Parametri

```
<input-file> <output-dir> <num-partitions> <approach> <partitioner> <num-workers>
```

- **approach**: `groupbykey` | `aggregatebykey` | `reducebykey`
- **partitioner**: `hash` | `range`
- **num-workers**: `1` (locale) | `2-4` (cloud)

## 📊 Sistema Metriche Automatico

Il progetto genera automaticamente un file CSV con tutte le metriche necessarie per l'analisi:

### Struttura del CSV Metriche

```csv
approach,partitioner,num_workers,num_partitions,total_events,unique_events,
co_occurrences,load_time_ms,analysis_time_ms,total_time_ms,max_count,timestamp
```

### Campi delle Metriche

| Campo | Descrizione | Unità |
|-------|-------------|-------|
| approach | Approccio utilizzato | GroupByKey/AggregateByKey/ReduceByKey |
| partitioner | Tipo di partitioner | Hash/Range |
| num_workers | Numero di worker nodes | int |
| num_partitions | Numero di partizioni | int |
| total_events | Eventi totali caricati | count |
| unique_events | Eventi unici dopo dedup | count |
| co_occurrences | Coppie co-occorrenze | count |
| load_time_ms | Tempo caricamento | milliseconds |
| analysis_time_ms | Tempo analisi | milliseconds |
| total_time_ms | Tempo totale | milliseconds |
| max_count | Conteggio coppia vincente | count |
| timestamp | Timestamp esecuzione | epoch |

### File Generati

Per ogni esecuzione vengono generati:

1. **`output/part-*`** - Risultato dell'analisi
2. **`output/metrics/part-*`** - Metriche in formato CSV
3. **`output/metrics-readable/part-*`** - Metriche in formato leggibile

### Uso delle Metriche

Le metriche CSV possono essere:
- Importate in Excel/Google Sheets
- Usate per calcolare Speedup ed Efficiency
- Aggregate per generare grafici
- Analizzate per il report del progetto

## 📝 Formato Output

```
((37.5, 15.3), (38.1, 13.4))
2024-03-12
2024-04-01
2024-04-03
```

Prima riga: coppia di località che co-occorre più frequentemente
Righe successive: date in cui avvengono le co-occorrenze (ordine crescente)

## ☁️ Esecuzione su Google Cloud DataProc

### Setup

```bash
# Upload JAR
gsutil cp target/scala-2.13/earthquake-cooccurrence-assembly-1.0.jar \
  gs://YOUR_BUCKET/jars/

# Upload dataset
gsutil cp earthquakes-full.csv gs://YOUR_BUCKET/data/
```

### Crea Cluster

```bash
gcloud dataproc clusters create earthquake-cluster \
  --region=europe-west1 \
  --num-workers 2 \
  --master-boot-disk-size 240 \
  --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 \
  --worker-machine-type=n2-standard-4
```

### Esegui Job

```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster \
  --region=europe-west1 \
  --jar=gs://YOUR_BUCKET/jars/earthquake-cooccurrence-assembly-1.0.jar \
  -- gs://YOUR_BUCKET/data/earthquakes-full.csv \
     gs://YOUR_BUCKET/output/test \
     8 \
     aggregatebykey \
     hash \
     2
```

### Elimina Cluster

```bash
gcloud dataproc clusters delete earthquake-cluster --region=europe-west1
```

## 📚 Documentazione

### Per Iniziare

**Leggi la [COMPLETE-GUIDE.md](COMPLETE-GUIDE.md)** per istruzioni passo-passo su:
- Setup completo
- Esempi per ogni approccio
- Test locali e cloud
- Analisi risultati
- Troubleshooting

### API Doc
- **Codice**: Documentazione Scaladoc in [target/scala-2.13/api/index.html](target/scala-2.13/api/index.html)

## 🔧 Requisiti

- **Java**: 17 o superiore
- **Scala**: 2.13.x
- **Spark**: 4.0.x
- **Google Cloud SDK** (per esecuzione cloud)

---

**Autore**: Nicola Modugno  
**Corso**: Scalable and Cloud Programming  
**A.A.**: 2025-26
