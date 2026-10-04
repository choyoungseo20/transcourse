#!/bin/sh
# usage: q.sh "<sql>"
ROOT=$(cd "$(dirname "$0")/../../.." && pwd)
PW=$(grep '^MYSQL_ROOT_PASSWORD=' "$ROOT/.env" | cut -d= -f2-)
docker exec video-processing-pipeline-mysql-1 mysql -uroot -p"$PW" videopipeline -e "$1" 2>&1 | grep -v "Using a password"
