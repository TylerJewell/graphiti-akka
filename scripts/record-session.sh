#!/usr/bin/env bash
#
# Records a session against the SOURCE system, producing the fixture the replay test compares
# against. Run it against the Python service, not against this port — the point is to capture what
# the original actually sends, not what we believe it sends.
#
# Prerequisites, and the reason this fixture does not exist yet:
#   * the source service running (it needs a graph database — Neo4j, FalkorDB or Kuzu)
#   * a model account configured for it, because ingest calls one
#
#   BASE=http://localhost:8000 ./scripts/record-session.sh > src/test/resources/recorded-session.json
#
# The exchanges below cover the routes whose responses a caller can see and depend on. Extend it
# rather than hand-editing the fixture: a recording is only evidence while it stays a recording.

set -euo pipefail
BASE="${BASE:-http://localhost:8000}"
GROUP="rec-$(date +%s)"

exchange() {
  local method="$1" path="$2" body="${3:-}"
  local status response
  if [ -n "$body" ]; then
    response=$(curl -s -w '\n%{http_code}' -X "$method" "$BASE$path" \
      -H 'content-type: application/json' -d "$body")
  else
    response=$(curl -s -w '\n%{http_code}' -X "$method" "$BASE$path")
  fi
  status=$(printf '%s' "$response" | tail -n1)
  response=$(printf '%s' "$response" | sed '$d')
  [ -n "$response" ] || response='null'
  jq -n --arg m "$method" --arg p "$path" --argjson s "$status" \
        --argjson b "${body:-null}" --argjson r "$response" \
     '{method:$m, path:$p, status:$s, body:$b, response:$r}'
}

message() {
  jq -n --arg g "$GROUP" --arg c "$1" --arg t "$2" \
    '{group_id:$g, messages:[{content:$c, name:"note", role_type:"user", role:"ana",
                              timestamp:$t, source_description:"recording"}]}'
}

{
  exchange GET /healthcheck
  exchange POST /messages "$(message 'Ana started at Acme in March 2024.' '2024-03-01T00:00:00Z')"
  # Ingest is asynchronous, so the recording has to wait before asking what was learned.
  sleep 20
  exchange POST /messages "$(message 'Ana joined Globex in July 2024.' '2024-07-01T00:00:00Z')"
  sleep 20
  exchange POST /search "$(jq -n --arg g "$GROUP" \
    '{group_ids:[$g], query:"Where does Ana work?", max_facts:10}')"
  exchange GET "/episodes/$GROUP?last_n=10"
  # The failure shapes matter as much as the success ones.
  exchange POST /messages "$(message 'unparseable' 'the fourth of July')"
  exchange GET /entity-edge/00000000-0000-0000-0000-000000000000
  exchange DELETE "/group/$GROUP"
} | jq -s --arg g "$GROUP" '{recordedGroup:$g, exchanges:.}'
