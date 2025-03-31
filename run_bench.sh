#!/bin/bash

# filepath: /home/tatoune/src/TelecomParis/2A/slr210/run_bench.sh

# N_f=("3 1" "10 4" "100 49")
# Te=(500 1000 1500 2000)
# alphas=(0 0.1 1)
N_f=("10 4")
Te=(500)
alphas=(0)

tests=(0 1 2 3 4)


for nf in "${N_f[@]}"; do
    for te in "${Te[@]}"; do
        for alpha in "${alphas[@]}"; do
            for test in "${tests[@]}"; do
                mkdir -p "benchs${test}"

                alpha_int=$(echo "$alpha * 100" | bc | awk '{printf "%d", $0}')
                file_name="benchs${test}/${nf// /_}_${te}_${alpha_int}.log"
                echo "-> $file_name"
                mvn exec:java \
                    -Dexec.mainClass=paxos.Main \
                    -Dexec.args="$nf $te $alpha_int" \
                    > "$file_name" &
                pid=$!
                sleep 3
                kill -SIGINT "$pid"

            done
        done
    done
done
