#!/bin/bash

DEPSVIZ_DEPS='{:deps {cnuernber/depsviz {:mvn/version "0.9"}}}'


clojure -Sdeps "$DEPSVIZ_DEPS" -m cnuernber.depsviz -i test/data/deps.edn --no-view --output-file docs/full-example.pdf
clojure -Sdeps "$DEPSVIZ_DEPS" -m cnuernber.depsviz -i test/data/deps.edn --prune --no-view --output-file docs/prune-example.pdf
clojure -Sdeps "$DEPSVIZ_DEPS" -m cnuernber.depsviz -i test/data/deps.edn --focus maven-resolver-api --no-view --output-file docs/focus-example.pdf
