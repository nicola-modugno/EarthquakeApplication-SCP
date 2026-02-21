#!/bin/bash

# Script per testare diverse configurazioni di partizioni
# Con gestione cluster esistenti e recupero da errori

# Funzione di help
show_help() {
  cat << EOF
Usage: $0 [OPTIONS]

Opzioni:
  -h, --help                Mostra questo messaggio di aiuto

All'avvio lo script chiederà interattivamente:
  - BUCKET: Nome del bucket GCS
  - JAR: Path completo al JAR su GCS (es: gs://bucket/jars/app.jar)
  - DATASET: Path completo al dataset su GCS (es: gs://bucket/data/file.csv)
  - REGION: Regione GCP (es: europe-west1)

EOF
  exit 0
}

# Check per --help
if [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
  show_help
fi

# Menu di configurazione
echo ""
echo "  CONFIGURAZIONE PARAMETRI"
echo ""
echo "Inserisci i parametri richiesti:"
echo ""
  
read -p "BUCKET (Nome del bucket GCS): " BUCKET
while [ -z "$BUCKET" ]; do
  echo "Errore: BUCKET non può essere vuoto"
  read -p "BUCKET (Nome del bucket GCS): " BUCKET
done

read -p "JAR (Path completo al JAR, es: gs://bucket/jars/app.jar): " JAR
while [ -z "$JAR" ]; do
  echo "Errore: JAR non può essere vuoto"
  read -p "JAR (Path completo al JAR): " JAR
done

read -p "DATASET (Path completo al dataset, es: gs://bucket/data/file.csv): " DATA
while [ -z "$DATA" ]; do
  echo "Errore: DATASET non può essere vuoto"
  read -p "DATASET (Path completo al dataset): " DATA
done

read -p "REGION (Regione GCP, es: europe-west1): " REGION
while [ -z "$REGION" ]; do
  echo "Errore: REGION non può essere vuota"
  read -p "REGION (Regione GCP): " REGION
done

# Riepilogo configurazione
echo ""
echo "  RIEPILOGO CONFIGURAZIONE"
echo "  BUCKET:  $BUCKET"
echo "  JAR:     $JAR"
echo "  DATASET: $DATA"
echo "  REGION:  $REGION"

echo ""
read -p "Confermi e procedi? (y/n): " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
  echo "Operazione annullata"
  exit 0
fi

# Funzione per verificare se cluster esiste
cluster_exists() {
  local cluster=$1
  gcloud dataproc clusters describe $cluster --region=$REGION &>/dev/null
  return $?
}

# Funzione per creare cluster con n2-standard-4 per tutte le macchine
create_cluster() {
  local workers=$1
  local cluster="earthquake-cluster-${workers}w"
  
  # Verifica se cluster esiste già
  if cluster_exists $cluster; then
    echo ""
    echo "Cluster $cluster già esistente!"
    echo "Opzioni:"
    echo "  1) Usa il cluster esistente"
    echo "  2) Elimina e ricrea il cluster"
    echo "  3) Salta questo test"
    read -p "Scelta (1/2/3): " choice
    
    case $choice in
      1)
        echo "Usando cluster esistente"
        return 0
        ;;
      2)
        echo "Eliminando cluster esistente..."
        delete_cluster $workers
        ;;
      3)
        echo "Saltando test per $workers workers"
        return 1
        ;;
      *)
        echo "Scelta non valida, saltando test"
        return 1
        ;;
    esac
  fi
  
  echo ""
  echo "Creating ${workers}-worker cluster..."
  
  if [ $workers -eq 2 ]; then
    echo "1 master + 2 workers)"
  elif [ $workers -eq 3 ]; then
    echo "1 master + 3 workers)"
  elif [ $workers -eq 4 ]; then
    echo "1 master + 4 workers)"
  fi
  
  gcloud dataproc clusters create $cluster \
    --region=$REGION \
    --image-version=2.1-debian11 \
    --num-workers $workers \
    --master-boot-disk-size 240 \
    --worker-boot-disk-size 240 \
    --master-machine-type=n2-standard-4 \
    --worker-machine-type=n2-standard-4 \
    --quiet
  
  return $?
}

# Funzione per eseguire job
run_job() {
  local workers=$1
  local partitions=$2
  local approach=$3
  local cluster="earthquake-cluster-${workers}w"
  local output="gs://$BUCKET/output/${workers}w-${partitions}p-${approach}"
  
  echo ""
  echo "Running: $workers workers, $partitions partitions, $approach"
  
  gcloud dataproc jobs submit spark \
    --cluster=$cluster \
    --region=$REGION \
    --jar=$JAR \
    -- $DATA $output $partitions $approach $workers
  
  if [ $? -eq 0 ]; then
    echo "Job completed successfully!"
    
    # Scarica metriche
    echo "Downloading metrics..."
    gcloud storage cat "${output}/metrics/part-*" > "metrics-${workers}w-${partitions}p-${approach}.csv" 2>/dev/null
    
    if [ $? -eq 0 ]; then
      echo "Metrics saved: metrics-${workers}w-${partitions}p-${approach}.csv"
    fi
  else
    echo "Job failed!"
    echo ""
    echo "Opzioni:"
    echo "  1) Riprova questo job"
    echo "  2) Continua con il prossimo job"
    echo "  3) Interrompi tutti i test"
    read -p "Scelta (1/2/3): " choice
    
    case $choice in
      1)
        echo "Riprovando job..."
        run_job $workers $partitions $approach
        ;;
      2)
        echo "Continuando con prossimo job..."
        ;;
      3)
        echo "Interruzione test"
        echo "Pulizia cluster prima dell'uscita"
        cleanup_all_clusters
        exit 1
        ;;
    esac
  fi
}

# Funzione per eliminare cluster
delete_cluster() {
  local workers=$1
  local cluster="earthquake-cluster-${workers}w"
  
  echo ""
  echo "Deleting cluster $cluster..."
  
  gcloud dataproc clusters delete $cluster --region=$REGION --quiet
  echo "Waiting for resources to be released..."
  sleep 30
}

# Funzione per pulire tutti i cluster
cleanup_all_clusters() {
  echo ""
  echo "Pulizia cluster esistenti..."
  
  for w in 2 3 4; do
    local cluster="earthquake-cluster-${w}w"
    if cluster_exists $cluster; then
      echo "  - Eliminando $cluster..."
      delete_cluster $w
    fi
  done
  
  echo "Pulizia completata"
}

# Menu principale
echo ""
echo "  EARTHQUAKE CO-OCCURRENCE EXPERIMENTS"
echo ""
echo "Test pianificati:"
echo "  1. Cluster 2 workers: 8, 16, 32, 48 partizioni"
echo "  2. Cluster 3 workers: 16, 32, 48 partizioni"
echo "  3. Cluster 4 workers: 16, 32, 48 partizioni"
echo ""
echo ""
echo "Opzioni:"
echo "  1) Esegui tutti i test (raccomandato)"
echo "  2) Solo cluster 2 workers"
echo "  3) Solo cluster 3 workers"
echo "  4) Solo cluster 4 workers"
echo "  5) Pulisci tutti i cluster"
echo "  6) Esci"
echo ""
read -p "Scelta (1-6): " main_choice

case $main_choice in
  2)
    RUN_2W=true
    RUN_3W=false
    RUN_4W=false
    ;;
  3)
    RUN_2W=false
    RUN_3W=true
    RUN_4W=false
    ;;
  4)
    RUN_2W=false
    RUN_3W=false
    RUN_4W=true
    ;;
  5)
    cleanup_all_clusters
    exit 0
    ;;
  6)
    echo "Uscita"
    exit 0
    ;;
  1|*)
    RUN_2W=true
    RUN_3W=true
    RUN_4W=true
    ;;
esac

# TEST 2 WORKERS
if [ "$RUN_2W" = true ]; then
  echo ""
  
  echo "  2 WORKERS "
  
  
  create_cluster 2
  if [ $? -eq 0 ]; then
    
    echo ""
    echo "--- Test primo approccio ---"
    run_job 2 8 1
    run_job 2 16 1
    run_job 2 32 1
    run_job 2 48 1
    
    echo ""
    echo "--- Test secondo approccio ---"
    run_job 2 8 2
    run_job 2 16 2
    run_job 2 32 2
    run_job 2 48 2
    
    echo ""
    echo "--- Test terzo approccio ---"
    run_job 2 8 3
    run_job 2 16 3
    run_job 2 32 3
    run_job 2 48 3

    
    delete_cluster 2
  fi
fi

# TEST 3 WORKERS
if [ "$RUN_3W" = true ]; then
  echo ""
  
  echo "  3 WORKERS "
  

  create_cluster 3
  if [ $? -eq 0 ]; then
    
    echo ""
    echo "--- Test primo approccio ---"
    run_job 3 16 1
    run_job 3 32 1
    run_job 3 48 1
    
    echo ""
    echo "--- Test secondo approccio ---"
    run_job 3 16 2
    run_job 3 32 2
    run_job 3 48 2
    
    echo ""
    echo "--- Test terzo approccio ---"
    run_job 3 16 3
    run_job 3 32 3
    run_job 3 48 3
    
    delete_cluster 3
  fi
fi

# TEST 4 WORKERS
if [ "$RUN_4W" = true ]; then
  echo ""
  
  echo "  4 WORKERS "
  

  create_cluster 4
  if [ $? -eq 0 ]; then
    
    echo ""
    echo "--- Test primo approccio ---"
    run_job 4 16 1
    run_job 4 32 1
    run_job 4 48 1
    
    echo ""
    echo "--- Test secondo approccio ---"
    run_job 4 16 2
    run_job 4 32 2
    run_job 4 48 2
    
    echo ""
    echo "--- Test terzo approccio ---"
    run_job 4 16 3
    run_job 4 32 3
    run_job 4 48 3
    
    delete_cluster 4
  fi
fi

# RIEPILOGO FINALE
echo ""
echo "  TEST COMPLETATI"
echo ""
echo "Metriche scaricate:"
if ls metrics-*.csv 1> /dev/null 2>&1; then
  ls -lh metrics-*.csv | awk '{print "  ✓ " $9 " (" $5 ")"}'
else
  echo "Nessun file di metriche trovato"
fi
echo ""
