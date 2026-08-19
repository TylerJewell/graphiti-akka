# Acknowledgements

This project is a port of **[getzep/graphiti](https://github.com/getzep/graphiti)**, a temporal
graph building library, © 2024 Zep Software, Inc., licensed under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## What is derived, and how

**Behaviour is reproduced deliberately and precisely.** The bi-temporal fact lifecycle, the entity
recognition cascade, the rank-fusion constants and the two external surfaces were specified from
the original and rebuilt to match it — including its defects, so that equivalence could be
demonstrated rather than asserted. This is a derived work in substance even where it is not a
copy in text.

**No application source was copied.** The Java in `src/main/java` was written against the
specifications in a separate repository, `akka-specify-harness`, under `graphiti-port/` — not
translated line by line.

**Eight files were copied verbatim, and had to be.** Everything in
`src/main/resources/prompts/` is the original's model instructions, unaltered:

```
dedupe_nodes.nodes.txt              extract_nodes.extract_summary.txt
extract_edges.edge.txt              extract_nodes.extract_text.txt
extract_nodes.extract_json.txt      summarize_nodes.summarize_pair.txt
extract_nodes.extract_message.txt   summarize_nodes.summary_description.txt
```

These are specification artifacts, not prose. They are read by a language model, and rewording one
changes what the model returns — so paraphrasing them would have quietly abandoned the equivalence
the port exists to demonstrate. They remain © 2024 Zep Software, Inc. under Apache-2.0.

## Licensing consequence

Because this project ships Apache-2.0 material, **it is licensed under Apache-2.0**, not under the
permissive licence of the template it was scaffolded from. That template licence was still in place
when the port was first published and was corrected once the copied prompts were accounted for;
recording the correction here rather than silently replacing the file, because the earlier state
was wrong and someone may have read it.

## Also used

- **[Akka SDK](https://doc.akka.io/)** — the runtime and component model this port targets.
- **[FlureeDB](https://flur.ee/)** — the graph, vector and full-text store.
- **[Bouncy Castle](https://www.bouncycastle.org/)** (MIT) — BLAKE2b, which the JDK does not
  provide and which the entity-identity hash requires bit-for-bit.
