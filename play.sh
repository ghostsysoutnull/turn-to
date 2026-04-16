#!/usr/bin/env bash
set -euo pipefail

ADVENTURES_DIR="adventures"

# Collect adventure JSON files (exclude manifests and any non-adventure JSON)
mapfile -t FILES < <(ls "$ADVENTURES_DIR"/*.json 2>/dev/null | grep -v '\-manifest\.json' | sort)

if [[ ${#FILES[@]} -eq 0 ]]; then
    echo "No adventures found in $ADVENTURES_DIR/" >&2
    exit 1
fi

echo ""
echo "=== TAS Neo — Choose an Adventure ==="
echo ""

declare -a IDS
declare -a TITLES
declare -a DESCRIPTIONS

for i in "${!FILES[@]}"; do
    FILE="${FILES[$i]}"
    ID=$(basename "$FILE" .json)
    TITLE=$(python3 -c "import json,sys; d=json.load(open('$FILE')); print(d.get('title', '$ID'))")
    DESC=$(python3 -c "import json,sys; d=json.load(open('$FILE')); print(d.get('description', '')[:80])")
    IDS[$i]="$ID"
    TITLES[$i]="$TITLE"
    DESCRIPTIONS[$i]="$DESC"
    printf "  [%d] %s\n" "$((i+1))" "$TITLE"
    printf "      %s\n" "$DESC"
    echo ""
done

while true; do
    read -rp "Enter number (1-${#FILES[@]}): " CHOICE
    if [[ "$CHOICE" =~ ^[0-9]+$ ]] && (( CHOICE >= 1 && CHOICE <= ${#FILES[@]} )); then
        break
    fi
    echo "  Invalid choice. Please enter a number between 1 and ${#FILES[@]}."
done

SELECTED_ID="${IDS[$((CHOICE-1))]}"
SELECTED_TITLE="${TITLES[$((CHOICE-1))]}"

echo ""
echo "Launching: $SELECTED_TITLE"
echo ""

mvn -q exec:java -Dexec.mainClass=com.tas.neo.Main -Dexec.args="$SELECTED_ID"
