# Confronto Dettagliato dei Tre Approcci

## Panoramica

Questo documento spiega in dettaglio le differenze tecniche tra i tre approcci implementati, utile per la sezione del report dedicata all'analisi implementativa.

---

## Approccio 1: GroupByKey

### Codice Chiave
```scala
val locationsByDate = eventsByDate.groupByKey()
```

### Come Funziona

1. **Input**: RDD di coppie `(date, location)`
2. **groupByKey()**: Raggruppa TUTTI i valori per ogni chiave (data)
3. **Shuffling**: OGNI location viene trasmessa alla partizione corrispondente
4. **Output**: RDD di `(date, Iterable[Location])`

### Diagramma del Flusso di Dati

```
Partizione 1:          Partizione 2:          Partizione 3:
(2024-01-01, L1)      (2024-01-01, L3)      (2024-01-02, L5)
(2024-01-01, L2)      (2024-01-02, L4)      (2024-01-02, L6)
       ↓                     ↓                     ↓
       └─────────────────────┴─────────────────────┘
                             ↓
                    SHUFFLING COMPLETO
                    (Tutti i dati)
                             ↓
       ┌─────────────────────┬─────────────────────┐
       ↓                     ↓                     ↓
(2024-01-01,         (2024-01-02,         ...
 [L1,L2,L3])          [L4,L5,L6])
```

### Analisi Tecnica

**Shuffling**:
- Quantità: 100% dei dati location
- Ogni location viene trasmessa esattamente una volta
- Esempio: 1M location → 1M trasmissioni

**Memory**:
- Tutte le location per una data devono stare in memoria insieme
- Rischio OutOfMemory per date con molti eventi
- Memory peak alto durante il groupBy

**Network**:
- Traffico massimo sulla rete
- Latenza proporzionale al numero di location
- Collo di bottiglia per dataset grandi

**Performance**:
- Tempo = T_compute + T_shuffle
- T_shuffle dominante per dataset grandi
- Non scala bene con l'aumento dei dati

### Pro e Contro

✅ **Pro**:
- Codice semplice e diretto
- Facile da capire e debuggare
- Va bene per dataset piccoli (< 1GB)

❌ **Contro**:
- Shuffling massiccio
- Alto memory footprint
- Non scala con dataset grandi
- Rischio OutOfMemoryError

### Quando Usarlo
- Dataset piccoli per test
- Quando la semplicità è prioritaria
- Per capire la logica base prima di ottimizzare

---

## Approccio 2: AggregateByKey

### Codice Chiave
```scala
val locationsByDate = eventsByDate.aggregateByKey(Set.empty[Location])(
  (set, loc) => set + loc,      // seqOp: combinazione locale
  (set1, set2) => set1 ++ set2   // combOp: merge tra partizioni
)
```

### Come Funziona

1. **seqOp** (locale): Aggrega location in Set dentro ogni partizione
2. **combOp** (distribuita): Merge i Set tra partizioni
3. **Shuffling**: Solo i Set aggregati vengono trasmessi
4. **Output**: RDD di `(date, Set[Location])`

### Diagramma del Flusso di Dati

```
Partizione 1:              Partizione 2:              Partizione 3:
(2024-01-01, L1)          (2024-01-01, L3)          (2024-01-02, L5)
(2024-01-01, L2)          (2024-01-02, L4)          (2024-01-02, L6)
       ↓                         ↓                         ↓
   AGGREGAZIONE              AGGREGAZIONE              AGGREGAZIONE
   LOCALE (seqOp)            LOCALE (seqOp)            LOCALE (seqOp)
       ↓                         ↓                         ↓
2024-01-01: {L1,L2}      2024-01-01: {L3}          2024-01-02: {L5,L6}
2024-01-02: {}           2024-01-02: {L4}          
       ↓                         ↓                         ↓
       └─────────────────────────┴─────────────────────────┘
                                 ↓
                      SHUFFLING RIDOTTO
                      (Solo Set aggregati)
                                 ↓
       ┌─────────────────────────┬─────────────────────────┐
       ↓                         ↓                         ↓
(2024-01-01,              (2024-01-02,              ...
 {L1,L2} ∪ {L3})           {L4} ∪ {L5,L6})
       ↓                         ↓
(2024-01-01,              (2024-01-02,
 {L1,L2,L3})               {L4,L5,L6})
```

### Analisi Tecnica

**Shuffling**:
- Quantità: Solo Set aggregati per ogni (partizione, data)
- Esempio: 1M location, 100 date, 10 partizioni
  - GroupByKey: 1M trasmissioni
  - AggregateByKey: ~1000 trasmissioni (100 date × 10 partizioni)
- Riduzione: ~99% in meno

**Memory**:
- Ogni partizione mantiene solo un Set per data
- Memory usage distribuito equamente
- No rischio OutOfMemory (se Set per data è ragionevole)

**Network**:
- Traffico ridotto drasticamente
- Latenza molto inferiore
- Scala molto meglio

**Performance**:
- T_shuffle ridotto drasticamente
- T_compute simile a GroupByKey
- Performance complessiva molto migliore

### Pro e Contro

✅ **Pro**:
- Shuffling minimizzato
- Memory efficient
- Scala eccellentemente
- Migliore per dataset grandi

❌ **Contro**:
- Codice leggermente più complesso
- Richiede comprensione di seqOp/combOp

### Quando Usarlo
- **Dataset grandi** (> 1GB) - RACCOMANDATO
- Produzione
- Quando le performance sono critiche
- Quando serve scalabilità

---

## Approccio 3: ReduceByKey

### Codice Chiave
```scala
// Prima: deduplicazione
val uniqueEvents = normalizedEvents
  .map { case (lat, lon, date) => ((Location(lat, lon), date), 1) }
  .reduceByKey(_ + _)  // Conta occorrenze

// Poi: conteggio co-occorrenze
val pairCounts = coOccurrences
  .map { case (pair, _) => (pair, 1) }
  .reduceByKey(_ + _)  // Somma i conteggi
```

### Come Funziona

1. **Map**: Trasforma elementi in coppie (key, 1)
2. **reduceByKey** (locale): Combina valori localmente con operazione associativa
3. **Shuffling**: Solo conteggi parziali trasmessi
4. **reduceByKey** (globale): Combina conteggi finali

### Diagramma del Flusso di Dati

```
Partizione 1:              Partizione 2:              Partizione 3:
(pair1, 1)                (pair1, 1)                (pair2, 1)
(pair1, 1)                (pair2, 1)                (pair2, 1)
(pair2, 1)                (pair2, 1)                (pair3, 1)
       ↓                         ↓                         ↓
   REDUCE LOCALE             REDUCE LOCALE             REDUCE LOCALE
   (operazione +)            (operazione +)            (operazione +)
       ↓                         ↓                         ↓
(pair1, 2)                (pair1, 1)                (pair2, 2)
(pair2, 1)                (pair2, 2)                (pair3, 1)
       ↓                         ↓                         ↓
       └─────────────────────────┴─────────────────────────┘
                                 ↓
                      SHUFFLING OTTIMIZZATO
                      (Solo conteggi parziali)
                                 ↓
       ┌─────────────────────────┬─────────────────────────┐
       ↓                         ↓                         ↓
(pair1, 2+1)              (pair2, 1+2+2)            (pair3, 1)
       ↓                         ↓                         ↓
(pair1, 3)                (pair2, 5)                (pair3, 1)
```

### Analisi Tecnica

**Shuffling**:
- Quantità: Solo conteggi parziali per ogni (partizione, chiave)
- Simile ad AggregateByKey ma ottimizzato per operazioni associative
- Esempio: 1M elementi → ~1000 conteggi parziali trasmessi

**Memory**:
- Mantiene solo map (key → count) in memoria
- Memory usage O(numero_chiavi_uniche) per partizione
- Molto efficiente per conteggi

**Network**:
- Traffico ridotto
- Ottimale per operazioni come +, *, max, min

**Performance**:
- Ottimo per conteggi e somme
- Performance simile ad AggregateByKey
- Leggermente più veloce per operazioni semplici

### Pro e Contro

✅ **Pro**:
- Ottimo per conteggi
- Bilanciato tra semplicità ed efficienza
- Combina valori prima dello shuffling
- Codice relativamente semplice

❌ **Contro**:
- Limitato a operazioni associative
- Per operazioni complesse meglio AggregateByKey

### Quando Usarlo
- Operazioni di conteggio e somma
- Quando serve bilanciamento tra semplicità e performance
- Alternative a GroupByKey senza complessità di AggregateByKey

---

## Confronto Quantitativo

### Esempio Concreto

**Setup**:
- 1,000,000 eventi
- 100 date uniche
- 10 partizioni
- Media: 10,000 eventi per data

### Shuffling (Dati Trasmessi)

| Approccio | Dati Trasmessi | Percentuale |
|-----------|----------------|-------------|
| GroupByKey | ~1,000,000 location objects | 100% |
| AggregateByKey | ~1,000 Set objects (100 date × 10 part) | ~0.1% |
| ReduceByKey | ~1,000 integers | ~0.1% |

### Memory Peak per Nodo

| Approccio | Memory per Executor | Note |
|-----------|---------------------|------|
| GroupByKey | ~500MB | Tutte location per date popolari |
| AggregateByKey | ~50MB | Solo Set aggregati |
| ReduceByKey | ~20MB | Solo contatori |

### Tempo di Esecuzione (Stimato)

| Approccio | 2 Workers | 3 Workers | 4 Workers |
|-----------|-----------|-----------|-----------|
| GroupByKey | 300s | 220s | 180s |
| AggregateByKey | 180s | 130s | 105s |
| ReduceByKey | 200s | 145s | 115s |

### Scalabilità

**Strong Scaling Efficiency** (con aumento workers):

| Workers | GroupByKey | AggregateByKey | ReduceByKey |
|---------|------------|----------------|-------------|
| 2 | 100% | 100% | 100% |
| 3 | 68% | 92% | 83% |
| 4 | 52% | 86% | 75% |

**Interpretazione**:
- AggregateByKey scala meglio (86% efficiency con 4 workers)
- GroupByKey degrada rapidamente (52% efficiency)
- ReduceByKey è intermedio (75% efficiency)

---

## Linee Guida per il Report

### Sezione "Approccio Implementativo"

**Cosa includere**:

1. **Diagramma dei tre approcci** (simile a quelli sopra)
2. **Spiegazione del flusso dati** per ciascuno
3. **Analisi del shuffling**:
   ```
   GroupByKey: 100% dati → rete
   AggregateByKey: ~0.1% dati → rete  (1000× meno!)
   ReduceByKey: ~0.1% dati → rete
   ```
4. **Trade-off espliciti**:
   - Semplicità vs Performance
   - Memory vs Speed
   - Quando usare quale

### Sezione "Risultati Sperimentali"

**Tabella dei tempi**:
```
| Approccio | 2w (8p) | 3w (12p) | 4w (16p) | Speedup | Efficiency |
|-----------|---------|----------|----------|---------|------------|
| GroupByKey | Ts | ... | ... | ... | ... |
| AggregateByKey | ... | ... | ... | ... | ... |
| ReduceByKey | ... | ... | ... | ... | ... |
```

**Grafici**:
1. Tempo vs Workers (per ogni approccio)
2. Confronto Approcci (bar chart)
3. Efficiency vs Workers

### Sezione "Discussione"

**Domande da rispondere**:

1. **Perché AggregateByKey è più veloce?**
   - Riduce shuffling del 99%
   - Memory footprint inferiore
   - Combina dati localmente

2. **Quando GroupByKey è accettabile?**
   - Dataset piccoli
   - Sviluppo/test
   - Quando semplicità > performance

3. **Perché tutti producono lo stesso risultato?**
   - Logica identica
   - Solo l'esecuzione cambia
   - Verificare con assert!

4. **Come migliorare ulteriormente?**
   - Broadcast variables per lookup
   - Custom partitioners
   - Caching strategico

---

## Conclusione

### Raccomandazione Finale

**Per il progetto SCP**:
- Implementare tutti e 3 (✅ fatto)
- Testare con dataset completo
- Documentare differenze osservate
- **Usare AggregateByKey in produzione**

### Checklist Report

- [ ] Spiegato come funziona ogni approccio
- [ ] Inclusi diagrammi di flusso dati
- [ ] Tabella comparativa tempi reali
- [ ] Grafici performance
- [ ] Analisi shuffling con numeri
- [ ] Discussione trade-off
- [ ] Raccomandazioni per casi d'uso
- [ ] Codice sorgente commentato negli snippet

---

**Nota**: Usa questi dati come riferimento per il report. I tuoi risultati reali possono variare in base al dataset e alla configurazione del cluster.
