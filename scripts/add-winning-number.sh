#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

usage() {
  echo "Usage: ./scripts/add-winning-number.sh <round> <draw_date> <n1> <n2> <n3> <n4> <n5> <n6> <bonus>"
}

if [ "$#" -ne 9 ]; then
  usage
  exit 1
fi

round="$1"
draw_date="$2"
shift 2
nums=("$1" "$2" "$3" "$4" "$5" "$6")
bonus="$7"

if ! [[ "$round" =~ ^[0-9]+$ ]]; then
  echo "Error: round must be a positive integer"
  exit 1
fi
if [ "$round" -le 0 ]; then
  echo "Error: round must be greater than 0"
  exit 1
fi

if ! [[ "$draw_date" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
  echo "Error: draw_date must be YYYY-MM-DD"
  exit 1
fi

for n in "${nums[@]}" "$bonus"; do
  if ! [[ "$n" =~ ^[0-9]+$ ]]; then
    echo "Error: all numbers must be integers"
    exit 1
  fi
  if [ "$n" -lt 1 ] || [ "$n" -gt 45 ]; then
    echo "Error: numbers must be in range 1..45"
    exit 1
  fi
done

if ! [ "${nums[0]}" -lt "${nums[1]}" ] || \
   ! [ "${nums[1]}" -lt "${nums[2]}" ] || \
   ! [ "${nums[2]}" -lt "${nums[3]}" ] || \
   ! [ "${nums[3]}" -lt "${nums[4]}" ] || \
   ! [ "${nums[4]}" -lt "${nums[5]}" ]; then
  echo "Error: main numbers must be strictly ascending"
  exit 1
fi

for i in "${!nums[@]}"; do
  for j in "${!nums[@]}"; do
    if [ "$i" -lt "$j" ] && [ "${nums[$i]}" -eq "${nums[$j]}" ]; then
      echo "Error: main numbers must be unique"
      exit 1
    fi
  done
  if [ "${nums[$i]}" -eq "$bonus" ]; then
    echo "Error: bonus number must not duplicate main numbers"
    exit 1
  fi
done

: "${KRAFT_DB_NAME:?KRAFT_DB_NAME is required in .env}"
: "${KRAFT_DB_USER:?KRAFT_DB_USER is required in .env}"
: "${KRAFT_DB_PASSWORD:?KRAFT_DB_PASSWORD is required in .env}"

db_exec() {
  if docker compose exec -T mariadb sh -lc 'command -v mysql >/dev/null 2>&1'; then
    docker compose exec -T mariadb mysql "$@"
  else
    docker compose exec -T mariadb mariadb "$@"
  fi
}

exists="$(db_exec -N -s -u"$KRAFT_DB_USER" -p"$KRAFT_DB_PASSWORD" "$KRAFT_DB_NAME" \
  -e "SELECT COUNT(*) FROM winning_numbers WHERE round = ${round};")"
if [ "$exists" != "0" ]; then
  echo "Error: round ${round} already exists"
  exit 1
fi

insert_sql="INSERT INTO winning_numbers (
  round, draw_date, n1, n2, n3, n4, n5, n6, bonus_number,
  first_prize, first_winners, total_sales, first_accum_amount,
  raw_json, created_at
) VALUES (
  ${round}, '${draw_date}', ${nums[0]}, ${nums[1]}, ${nums[2]}, ${nums[3]}, ${nums[4]}, ${nums[5]}, ${bonus},
  0, 0, 0, 0,
  NULL, NOW()
);"

db_exec -N -s -u"$KRAFT_DB_USER" -p"$KRAFT_DB_PASSWORD" "$KRAFT_DB_NAME" -e "$insert_sql"

echo "Inserted winning number: round=${round}, draw_date=${draw_date}, numbers=${nums[*]}, bonus=${bonus}"
