================================================================================
QUICK REFERENCE - Comandi Essenziali
================================================================================

SETUP INIZIALE
--------------
cd /path/to/project
sbt clean compile
sbt assembly

TEST LOCALE
-----------
spark-submit --class Main --master local[*] \
  target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar \
  test-data.csv output-local 4 aggregatebykey hash 1

GOOGLE CLOUD - SETUP
--------------------
# Login
gcloud auth login

# Crea bucket
BUCKET="earthquake-YOUR_MATRICOLA"
gsutil mb gs://$BUCKET/

# Upload
gsutil cp target/scala-2.12/earthquake-cooccurrence-assembly-1.0.jar gs://$BUCKET/jars/
gsutil cp earthquakes-full.csv gs://$BUCKET/data/

GOOGLE CLOUD - CLUSTER
----------------------
# Crea (2 workers)
gcloud dataproc clusters create my-cluster \
  --region=europe-west1 --num-workers 2 \
  --master-boot-disk-size 240 --worker-boot-disk-size 240 \
  --master-machine-type=n2-standard-4 --worker-machine-type=n2-standard-4

# Lista cluster attivi
gcloud dataproc clusters list --region=europe-west1

# Elimina (IMPORTANTE!)
gcloud dataproc clusters delete my-cluster --region=europe-west1

GOOGLE CLOUD - JOB
------------------
# Submit job
gcloud dataproc jobs submit spark \
  --cluster=my-cluster --region=europe-west1 \
  --jar=gs://$BUCKET/jars/earthquake-cooccurrence-assembly-1.0.jar \
  -- gs://$BUCKET/data/earthquakes-full.csv \
     gs://$BUCKET/output/run1 \
     8 aggregatebykey hash 2

# Parametri:
# - 8 = num partitions
# - aggregatebykey = approach (o groupbykey, reducebykey)
# - hash = partitioner (o range)
# - 2 = num workers (per metriche)

GOOGLE CLOUD - DOWNLOAD
------------------------
# Scarica risultati
gsutil cp -r gs://$BUCKET/output/run1 ./results/

# Scarica metriche
gsutil cat gs://$BUCKET/output/run1/metrics/part-*

# Scarica output
gsutil cat gs://$BUCKET/output/run1/part-*

AUTOMATIZZARE TUTTO
-------------------
# Test locali (3 approcci)
./test-all-approaches.sh

# Esperimenti completi cloud (18 configurazioni)
./run-complete-experiments.sh $BUCKET earthquakes-full.csv

CONFIGURAZIONI DA TESTARE
--------------------------
Minimo (9 esperimenti):
- GroupByKey, AggregateByKey, ReduceByKey
- Hash partitioner
- 2, 3, 4 workers

Completo (18 esperimenti):
- GroupByKey, AggregateByKey, ReduceByKey
- Hash E Range partitioner
- 2, 3, 4 workers

METRICHE DA CALCOLARE
----------------------
1. Speedup:
   S(n) = T_baseline / T(n)
   dove baseline = GroupByKey-Hash-2w

2. Strong Scaling Efficiency:
   E(n) = T_baseline / (n × T(n) / 2)

3. Confronto Approcci:
   % = T_approach / T_groupbykey × 100

4. Confronto Partitioner:
   % = (T_range - T_hash) / T_hash × 100

TROUBLESHOOTING
---------------
# Compila non funziona
sbt clean
rm -rf target/
sbt compile

# Cluster non si crea
gcloud auth login
gcloud config set project YOUR_PROJECT_ID
# Prova altra regione: us-central1

# OutOfMemory
# Usa AggregateByKey invece di GroupByKey

# Job lento
# Aumenta num partitions
# Usa AggregateByKey

# Crediti finiti
# Controlla: https://console.cloud.google.com/billing

CHECKLIST PRE-CONSEGNA
-----------------------
[ ] Codice compila
[ ] Test locale funziona
[ ] Almeno 9 configurazioni testate su cloud
[ ] CSV metriche generato
[ ] Tutti i cluster eliminati
[ ] Report completato

FILES IMPORTANTI
----------------
GUIDA-SEMPLICE.md           → Leggi questo per iniziare
RIEPILOGO-FINALE.md         → Panoramica completa
APPROACHES-COMPARISON.md    → Dettagli tecnici approcci
PARTITIONER-ANALYSIS.md     → Hash vs Range
final_metrics_*.csv         → Dati per report

HELP
----
Problemi? Controlla:
1. GUIDA-SEMPLICE.md sezione Troubleshooting
2. Log di Spark
3. Console Google Cloud

CONTATTI
--------
Per domande sul codice: vedi documentazione Scaladoc in src/
Per problemi Google Cloud: https://console.cloud.google.com/

================================================================================
GOOD LUCK! 🚀
================================================================================
