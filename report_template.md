# Report Progetto - Earthquake Co-occurrence Analysis

**Corso**: Scalable and Cloud Programming  
**Anno Accademico**: 2025-26  
**Studente**: [Nome e Cognome]  
**Matricola**: [Numero Matricola]

---

## 1. Introduzione

### 1.1 Obiettivo del progetto
[Descrivere brevemente l'obiettivo: analisi di co-occorrenza di terremoti per trovare la coppia di località che co-occorre più frequentemente]

### 1.2 Dataset utilizzato
- **Nome file**: [es. earthquakes-full.csv]
- **Numero di eventi totali**: [X eventi]
- **Periodo temporale**: [es. 2020-2024]
- **Dimensione file**: [es. XX MB]

---

## 2. Approccio Implementativo

### 2.1 Architettura generale
[Descrivere l'architettura del sistema: Main → DataLoader → CoOccurrenceAnalysis → Output]

### 2.2 Pipeline di elaborazione

#### Step 1: Caricamento e normalizzazione
- Lettura del CSV con Spark DataFrame API
- Arrotondamento coordinate alla prima cifra decimale
- Estrazione della data (formato yyyy-MM-dd)

#### Step 2: Deduplicazione
- Rimozione eventi duplicati con stessa località e data
- Tecnica utilizzata: [distinct() su RDD con chiave (location, date)]
- Numero eventi unici: [Y eventi]

#### Step 3: Generazione co-occorrenze
- Raggruppamento per data
- Generazione coppie ordinate di località
- Tecnica: [groupByKey/aggregateByKey + flatMap]

#### Step 4: Conteggio e selezione
- Conteggio co-occorrenze per coppia
- Selezione coppia con massimo conteggio
- Estrazione date ordinate

### 2.3 Tecniche di ottimizzazione utilizzate

#### 2.3.1 Minimizzazione dello shuffling
- **reduceByKey vs groupByKey**: [Spiegare perché reduceByKey è preferibile]
- **Esempio**: Nel conteggio delle co-occorrenze...

#### 2.3.2 Partitioning
- **HashPartitioner**: Distribuzione uniforme delle coppie chiave-valore
- **Numero di partizioni**: [Formula utilizzata: num_workers × 4]
- **Benefici osservati**: [Descrivere l'impatto]

#### 2.3.3 Persistence
- **RDD persistiti**: 
  - normalizedEvents
  - uniqueEvents
  - coOccurrences
- **Motivazione**: [Spiegare perché questi RDD sono riusati]
- **Storage level**: MEMORY_ONLY

#### 2.3.4 Alternative implementate
- Versione standard con `groupByKey`
- Versione ottimizzata con `aggregateByKey` (per dataset molto grandi)
- Spiegazione delle differenze e quando usare ciascuna

---

## 3. Configurazione degli Esperimenti

### 3.1 Ambiente di test

#### Local testing
- **Sistema**: [es. MacBook Pro, Ubuntu 22.04, ecc.]
- **CPU**: [es. 8 cores]
- **RAM**: [es. 16GB]
- **Dataset**: earthquakes-small.csv ([X eventi])

#### Google Cloud DataProc
- **Regione**: europe-west1
- **Machine type**: n2-standard-4
- **Master**: 1 nodo (4 vCPUs, 16GB RAM, 240GB disk)
- **Workers**: 2, 3, 4 nodi
- **Spark version**: 3.5.0
- **Scala version**: 2.12.18

### 3.2 Configurazioni testate

| Configurazione | Workers | Partitions | Rationale |
|---------------|---------|------------|-----------|
| Config 1      | 2       | 8          | 2 × 4 cores |
| Config 2      | 3       | 12         | 3 × 4 cores |
| Config 3      | 4       | 16         | 4 × 4 cores |

[Spiegare la scelta del numero di partizioni in relazione al numero di workers]

---

## 4. Risultati

### 4.1 Risultato dell'analisi

**Coppia di località con massime co-occorrenze**:
```
((lat1, lon1), (lat2, lon2))
```

**Numero di co-occorrenze**: [N]

**Prime 10 date**:
```
2024-01-01
2024-01-15
...
```

### 4.2 Performance e tempi di esecuzione

#### Tabella riassuntiva

| Workers | Partitions | Tempo totale (s) | Tempo caricamento (s) | Tempo analisi (s) | Throughput (eventi/s) |
|---------|-----------|------------------|----------------------|-------------------|----------------------|
| 2       | 8         | [T2]             | [L2]                 | [A2]              | [TP2]                |
| 3       | 12        | [T3]             | [L3]                 | [A3]              | [TP3]                |
| 4       | 16        | [T4]             | [L4]                 | [A4]              | [TP4]                |

[Inserire i dati reali dei vostri esperimenti]

#### Grafico dei tempi
[Inserire grafico: Workers (x) vs Tempo esecuzione (y)]

---

## 5. Analisi di Scalabilità

### 5.1 Speedup

**Formula**: S(n) = T(2) / T(n)

| Workers (n) | Tempo T(n) | Speedup S(n) |
|-------------|------------|--------------|
| 2           | [T2]       | 1.00         |
| 3           | [T3]       | [S3]         |
| 4           | [T4]       | [S4]         |

**Speedup ideale**: S(n) = n/2 (normalizzato a 2 workers)

**Analisi**:
- Con 3 workers: speedup [reale vs ideale]
- Con 4 workers: speedup [reale vs ideale]
- [Commentare eventuali deviazioni dall'ideale]

### 5.2 Strong Scaling Efficiency

**Formula**: E(n) = T(2) / (n × T(n) / 2)

| Workers (n) | Efficiency E(n) | E(n) % |
|-------------|-----------------|--------|
| 2           | 1.00            | 100%   |
| 3           | [E3]            | [E3%]  |
| 4           | [E4]            | [E4%]  |

**Analisi**:
- [Discutere l'efficienza osservata]
- [Identificare eventuali bottleneck]
- [Spiegare perché l'efficienza degrada (legge di Amdahl)]

### 5.3 Weak Scaling (se applicabile)

[Se avete testato con dataset di dimensioni crescenti proporzionali al numero di workers]

---

## 6. Discussione

### 6.1 Fattori che influenzano le performance

#### 6.1.1 Shuffling
- Operazioni che causano shuffling: [groupByKey, join, ecc.]
- Impatto osservato: [quantificare se possibile]
- Strategie di mitigazione adottate: [reduceByKey, partitionBy, ecc.]

#### 6.1.2 Data skew
- [Discutere se alcune partizioni erano molto più grandi di altre]
- [Impatto sul bilanciamento del carico]
- [Possibili soluzioni]

#### 6.1.3 Network latency
- [Impatto della comunicazione tra nodi]
- [Relazione con il numero di workers]

#### 6.1.4 Overhead di coordinamento
- [Overhead fisso indipendente dal numero di workers]
- [Spiega perché l'efficienza non è 100%]

### 6.2 Confronto tra approcci implementativi

#### groupByKey vs aggregateByKey
- **Memoria**: [aggregateByKey più efficiente]
- **Shuffling**: [confronto]
- **Quando usare ciascuno**: [raccomandazioni]

#### Numero di partizioni
- Esperimenti con diversi numeri: [se fatto]
- Impatto osservato: [risultati]
- Raccomandazione finale: [formula]

### 6.3 Limiti e possibili miglioramenti

#### Limiti attuali
1. [es. Richiede tutto il dataset in memoria]
2. [es. Non gestisce dati in streaming]
3. [es. Assume formato CSV fisso]

#### Possibili estensioni
1. **Streaming**: Analisi in tempo reale con Spark Streaming
2. **Top-K**: Trovare le top-K coppie invece che solo la massima
3. **Finestra temporale variabile**: Permettere finestre di N giorni
4. **Clustering geografico**: Usare clustering invece di griglia fissa
5. **Machine Learning**: Predire future co-occorrenze

---

## 7. Conclusioni

### 7.1 Risultati principali
[Riassumere i risultati chiave del progetto]

### 7.2 Apprendimenti
[Cosa avete imparato riguardo a:
- Programmazione distribuita
- Ottimizzazione Spark
- Scalabilità
- Trade-off implementativi]

### 7.3 Applicabilità
[Discutere l'applicabilità di questo approccio a:
- Altri tipi di eventi geo-temporali
- Scale di dati maggiori
- Requisiti real-time]

---

## 8. Appendice

### 8.1 Comandi eseguiti

#### Compilazione
```bash
sbt clean compile
sbt assembly
```

#### Upload su GCS
```bash
gsutil cp target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar gs://bucket/jars/
```

#### Creazione cluster
```bash
gcloud dataproc clusters create earthquake-cluster-2 \
  --region=europe-west1 \
  --num-workers 2 \
  ...
```

#### Esecuzione job
```bash
gcloud dataproc jobs submit spark \
  --cluster=earthquake-cluster-2 \
  ...
```

### 8.2 Log significativi
[Includere estratti di log che mostrano performance, warning, ecc.]

### 8.3 Codice chiave
[Estratti di codice particolarmente significativi con spiegazione]

---

## Bibliografia e Riferimenti

1. Apache Spark Documentation: https://spark.apache.org/docs/latest/
2. Scalable and Cloud Programming - Course Materials
3. [Altri riferimenti utilizzati]

---

**Data**: [GG/MM/AAAA]  
**Firma**: [Nome Cognome]
