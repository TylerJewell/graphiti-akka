# graphiti-akka

Tell it things. It remembers them, and when you later tell it something that contradicts what it
already knew, it works out when the old thing stopped being true and records that — instead of
overwriting it. You can still ask what it believed last year.

A port of [getzep/graphiti](https://github.com/getzep/graphiti) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

`getzep/graphiti` is a Python library that turns a stream of messages into a memory an AI assistant
can search. It was rebuilt here to find out how precisely a system has to be written down before it
can be rebuilt on a different stack.

Those written specifications are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness) under
`graphiti-port/`.

---

## getzep/graphiti → this port

📉 11,387 Python lines → **2,411 Java lines**<br>
📁 37 files → **26 files**<br>
⚡ 1,107 → **69** nanoseconds, deciding whether one fact replaces another<br>
⚡ 2,917,079 → **938,176** nanoseconds, deciding whether two names mean the same thing<br>
⚡ 93,734 → **14,390** nanoseconds, merging search results into one order<br>
🎯 3 of 3 calculations give identical answers<br>
🖥️ 2 programs running → **2 programs running**<br>
💾 not measured → **425 MB** of memory<br>
🚀 not measured → **3.55 seconds** to start<br>
🔌 4 databases supported → **1**

The original needs a graph database and a paid model account before it will start, so its startup
time and memory use were never observed.

Full method and every number behind these:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/graphiti-port/bench/REPORT.md).

---

## What it does

- **A newer, conflicting fact ends the older one instead of deleting it.** If Ana joined Acme in
  March and Globex in July, the Acme fact is marked as having stopped being true in July. Ask about
  April and you still get Acme.
- **It keeps "when it was true" separate from "when we found out".** Something learned in March
  about January is recorded as true from January, and as unknown to the service until March. Each
  question gives a different answer.
- **Two facts that start at the exact same moment both stay.** Neither ends the other.
- **A fact with no date does nothing.** It cannot end another fact and nothing can end it.
- **Deciding whether two names mean the same person runs through checks, cheapest first.** Look for
  things it might be, then an exact name match, then a near-match on spelling, and only then ask the
  model. Two identical names are never merged unless the first step found one while looking for the
  other.
- **It says "got it" before it does the work.** Only the date is checked while you wait. Anything
  else that goes wrong happens afterwards, quietly.

---

## Design decisions

**Event-sourced entities.** Instead of keeping only the newest version of everything, the service
writes down each change as it happens and works out the current state by replaying that list. If it
is restarted or something breaks halfway through, it can rebuild exactly where it was, and anyone
can see how it got there rather than having to trust it.

**One group at a time.** Everything about a single group of messages is handled by one worker in
order, so two facts about the same person can never be decided at the same instant and end up
contradicting each other. Different groups run side by side at full speed, so being careful about
one topic never slows down any of the others.

**Command Query Responsibility Segregation.** Questions are answered from a separate up-to-date
copy, and never from the records the service writes into. A long write cannot make someone's search
slow, and a thousand people reading at once cannot get in the way of new information arriving.

**One database instead of four.** The original let you pick between four different databases, which
meant four versions of the same code to write and keep working forever. Choosing a single one that
can do all three jobs — how things connect, finding things that mean something similar, and finding
words — deleted about thirteen thousand lines that existed only to hide the differences between
them.

**A domain layer with no framework in it.** The decisions that actually matter — when a new fact
replaces an old one, whether two names mean the same person, how to put search results in order —
are ordinary code that does not know the database, the web server or the model exist. They can be
tested in a few milliseconds with nothing running, so most of the ways this could have gone wrong
were ruled out before the rest of it was built.

---

## Running it — the short path

You do not need Java, Maven, or the Akka command-line tool installed. Akka Specify installs them.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/graphiti-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9000/healthcheck.

You will also need FlureeDB running on `127.0.0.1:8090`. That is where everything is stored.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once
- FlureeDB running on `127.0.0.1:8090`
- `OPENAI_API_KEY`, for reading the messages you send it

### Start it

```bash
mvn compile
akka local run
```

It listens on **port 9000**.

### Try it

```bash
# Ana joins Acme in March
curl -X POST localhost:9000/messages -H 'content-type: application/json' -d '{
  "group_id": "demo",
  "messages": [{"content": "Ana started at Acme in March 2024.", "name": "note",
                "role_type": "user", "timestamp": "2024-03-01T00:00:00Z",
                "source_description": "readme"}]}'

# Ana moves to Globex in July
curl -X POST localhost:9000/messages -H 'content-type: application/json' -d '{
  "group_id": "demo",
  "messages": [{"content": "Ana joined Globex in July 2024.", "name": "note",
                "role_type": "user", "timestamp": "2024-07-01T00:00:00Z",
                "source_description": "readme"}]}'

# Ask what it knows
curl -X POST localhost:9000/search -H 'content-type: application/json' \
  -d '{"group_ids": ["demo"], "query": "Where does Ana work?", "max_facts": 10}'
```

You get both facts back. The Acme one now has an end date of July.

### Everything else

```bash
curl "localhost:9000/episodes/demo?last_n=5"       # the messages it was sent, newest first
curl localhost:9000/entity-edge/<fact-id>          # one fact
curl -X DELETE localhost:9000/entity-edge/<id>     # delete a fact, keep the message it came from
curl -X DELETE localhost:9000/episode/<id>         # delete a message and anything only it taught
curl -X DELETE localhost:9000/group/demo           # empty one group
curl -X POST   localhost:9000/clear                # empty every group
```

All of it is also available to AI assistants at `/mcp`. The addresses, field names, reply codes and
wording of the replies match the original exactly, so anything already written to talk to the
original works here unchanged.

### Tests

```bash
mvn test                          # the rules, the two ways in, and sending a message end to end
mvn verify                        # adds the tests that need the database running
mvn test -Dtest=BenchmarkRunner   # compares answers and speed against the original
```

118 tests. The ones needing the database skip when it is not running, so check the skipped count.
No test calls a real model.

---

## Settings

| Variable | Default | What happens |
|---|---|---|
| `OPENAI_API_KEY` | none | Reading messages and comparing meanings. Without it the service runs and learns nothing rather than failing. |
| `MEMORY_STORE_URL` | `http://127.0.0.1:8090` | Where the database is. |
| `MEMORY_STORE_LEDGER` | `memory` | Which collection to write to. |
| `MEMORY_AUTH_TOKEN` | none | An optional password on every request. Without it there is no password, which is what lets anything written for the original keep working. |

---

## Where it differs from getzep/graphiti

Everything not listed here was copied on purpose, including the parts that look like mistakes.

| | getzep/graphiti | This port |
|---|---|---|
| Storage | four databases to choose from | one |
| Two messages in one group at once | no predictable result | handled one at a time |
| Replying "got it" | after putting the work in memory | after writing it down, about 90 ms slower |
| Loading many messages at once | yes | not built |
| Grouping related things into clusters | yes | not built; the tool says so when asked |
| A second pass to re-order search results | written, never switched on | left out |
| Password on requests | none | optional, off by default |
| Recording when the service learned something | read from the database's own history | written down explicitly |

---

## Licence

`getzep/graphiti` is Apache-2.0, © 2024 Zep Software, Inc. This port is built from it and includes
eight instruction files copied word for word, so it is **Apache-2.0** as well. See
[`ACKNOWLEDGEMENTS.md`](ACKNOWLEDGEMENTS.md).
