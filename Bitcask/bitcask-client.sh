#!/bin/bash
# filepath: /home/karim/Weather-Stations-Monitoring/bitcask_client.sh

# Default settings
SERVER_HOST=localhost
SERVER_PORT=5000
RESULTS_DIR="$(pwd)/results"

# Create results directory if it doesn't exist
mkdir -p "$RESULTS_DIR"

# Function to print usage information
print_usage() {
    echo "Usage:"
    echo "  $0 --view-all                 # View all keys and values, save to timestamped CSV"
    echo "  $0 --view --key=SOME_KEY      # View value for a specific key"
    echo "  $0 --perf --clients=N         # Run N parallel clients querying all keys"
}

# Function to handle viewing all keys/values
view_all() {
    timestamp=$(date +%s)
    filename="$RESULTS_DIR/${timestamp}.csv"
    
    echo "Requesting all keys and values..."
    
    # Create a header for the CSV file
    echo "Key,Value" > "$filename"
    
    # Connect to server and request all keys
    result=$(echo "a $RESULTS_DIR $timestamp.csv" | nc $SERVER_HOST $SERVER_PORT)
    
    if [ -f "$filename" ]; then
        echo "Data successfully written to $filename"
        echo "File contents:"
        head -n 5 "$filename"
        echo "..."
    else
        echo "Failed to retrieve data"
    fi
}

# Function to view a specific key
view_key() {
    key=$1
    
    echo "Requesting value for key: $key"
    
    # Connect to server and request specific key
    result=$(echo "r $key" | nc $SERVER_HOST $SERVER_PORT)
    
    if [ -n "$result" ]; then
        echo "Value: $result"
    else
        echo "Key not found or no data returned"
    fi
}

# Function to run performance test with multiple clients
run_perf_test() {
    num_clients=$1
    timestamp=$(date +%s)
    
    echo "Starting performance test with $num_clients clients..."
    
    # Run clients in parallel
    for (( i=1; i<=$num_clients; i++ )); do
        (
            filename="${timestamp}_thread_${i}.csv"
            echo "Key,Value" > "$RESULTS_DIR/$filename"
            
            echo "a $RESULTS_DIR $filename" | nc $SERVER_HOST $SERVER_PORT > /dev/null &
            
            echo "Thread $i started"
        ) &
        
        # Add a small delay to prevent overwhelming the server
        if (( i % 10 == 0 )); then
            sleep 0.1
        fi
    done
    
    # Wait for all background processes to finish
    wait
    
    echo "Performance test complete. Results saved in $RESULTS_DIR with prefix ${timestamp}_thread_*"
}

# Main script logic
if [ $# -eq 0 ]; then
    print_usage
    exit 1
fi

case "$1" in
    --view-all)
        view_all
        ;;
        
    --view)
        if [[ $2 =~ ^--key=(.+)$ ]]; then
            key="${BASH_REMATCH[1]}"
            view_key "$key"
        else
            echo "Error: Must specify a key with --key=SOME_KEY"
            print_usage
            exit 1
        fi
        ;;
        
    --perf)
        if [[ $2 =~ ^--clients=([0-9]+)$ ]]; then
            clients="${BASH_REMATCH[1]}"
            run_perf_test "$clients"
        else
            echo "Error: Must specify number of clients with --clients=N"
            print_usage
            exit 1
        fi
        ;;
        
    --help|-h)
        print_usage
        ;;
        
    *)
        echo "Error: Unknown option $1"
        print_usage
        exit 1
        ;;
esac

exit 0