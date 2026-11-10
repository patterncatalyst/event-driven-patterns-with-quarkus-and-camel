---
layout: plan
title: Versions
render_with_liquid: false
---

# Version pins (surveyed 2026-11-10)

Rule: latest stable upstream, exact pins, no prerelease, no floating tag. Every image is pinned by tag; the digest is recorded so a later re-survey can tell whether a tag moved. Versions appear in chapter 00 and appendices only.

## Pins

| Component | Pin (exact) | Surveyed (2026-11-10) | Source URL | Notes/holds |
|---|---|---|---|---|
| Temurin JDK | 25.0.5+8 (SDKMAN id 25.0.5-tem) | 2026-11-10 | https://api.adoptium.net/v3/assets/latest/25/hotspot | LTS line; JDK 25 is the minimum. |
| Apache Maven | 3.9.16 | 2026-11-10 | https://repo1.maven.org/maven2/org/apache/maven/apache-maven/maven-metadata.xml | HOLD: 3.10.0 is GA (2026-10-01) but Quarkus has open Maven 3.10 / Resolver 2 issues (quarkusio/quarkus #57216 open, #57381 closed). 4.0.0 is still rc-7, not GA. Re-survey at r02. |
| Quarkus platform (quarkus-bom) | io.quarkus.platform:quarkus-bom:3.40.1.2 | 2026-11-10 | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-bom/maven-metadata.xml | 3.40 is the LTS stream, recommended on code.quarkus.io (https://code.quarkus.io/api/streams), JDK 25 recommended. 4.0.0.Beta3 skipped (prerelease). Maven metadata `release` still says 3.40.1; 3.40.1.2 (published 2026-11-09) is the newest final respin and the stream's platformVersion. Carries kafka-clients 4.2.1 and JUnit Jupiter 6.1.3. |
| Quarkus platform (quarkus-camel-bom) | io.quarkus.platform:quarkus-camel-bom:3.40.1.2 | 2026-11-10 | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-camel-bom/3.40.1.2/quarkus-camel-bom-3.40.1.2.pom | Same platform version as quarkus-bom. 3.40.0.CR1 skipped. |
| Camel Quarkus | 3.40.0 | 2026-11-10 | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-camel-bom/3.40.1.2/quarkus-camel-bom-3.40.1.2.pom | Read from camel-quarkus-kafka in the BOM; do not override. |
| Apache Camel (core) | 4.22.1 | 2026-11-10 | https://repo1.maven.org/maven2/io/quarkus/platform/quarkus-camel-bom/3.40.1.2/quarkus-camel-bom-3.40.1.2.pom | Read from camel-kafka in the BOM. Newer Camel 4.23.0 exists but the platform carries 4.22.1; follow the platform. |
| camel-launcher (Camel CLI) | 4.22.1 | 2026-11-10 | https://repo1.maven.org/maven2/org/apache/camel/camel-launcher/maven-metadata.xml | HOLD: latest is 4.23.0; pinned to 4.22.1 to match the Camel line the platform carries. |
| Apache Kafka (book-wide) | 4.3.2 | 2026-11-10 | https://github.com/strimzi/strimzi-kafka-operator/blob/1.3.1/kafka-versions.yaml | HOLD: Kafka 4.4.0 is released but not supported by the latest Strimzi (1.3.1, 2026-11-04). 1.3.1 supports 4.2.0 to 4.3.2 and marks 4.3.2 as default. One Kafka version across the book (compose broker and Strimzi in r11), so the pin is the newest version Strimzi supports. Move to 4.4.x when a Strimzi release supports it. |
| docker.io/apache/kafka | docker.io/apache/kafka:4.3.2 (sha256:852a0d49fc4e656b022e7f23785411802c9a088c78a7c5e39f33969de9c227a7) | 2026-11-10 | https://hub.docker.com/r/apache/kafka/tags | KRaft single node. Also the Kafka Dev Services image to set in Quarkus tests. Quarkus platform's kafka-clients is 4.2.1 (compatible with a 4.3.2 broker). |
| Strimzi operator | quay.io/strimzi/operator:1.3.1 | 2026-11-10 | https://github.com/strimzi/strimzi-kafka-operator/releases/tag/1.3.1 | Record now, used at r11. 1.3.1-rc* skipped. |
| CloudNativePG operator | v1.30.1 | 2026-11-10 | https://github.com/cloudnative-pg/cloudnative-pg/releases/tag/v1.30.1 | Record now, used at r11. Latest stable minor line (1.30), published 2026-09-23. |
| CNPG operand image | ghcr.io/cloudnative-pg/postgresql:18.6-202611090812-standard-trixie (sha256:de572898499fab9406f0da51929da562f96f3be40ca79fafea8ec1a8d5b42065 is the digest of the moving 18.6-standard-trixie tag) | 2026-11-10 | https://github.com/cloudnative-pg/postgres-containers/pkgs/container/postgresql | Standard flavor on Debian trixie (current stable codename); the timestamped tag is immutable. 19beta1 skipped. Skopeo inspect: User 26, Entrypoint null, Cmd ["bash"], no ExposedPorts, no docker-library entrypoint. The infra step must supply its own start command (initdb on an empty PGDATA, then `postgres -c wal_level=logical`), run as UID 26, with PATH already including /usr/lib/postgresql/18/bin. |
| docker.io/library/postgres | docker.io/library/postgres:18.6 (sha256:e3d24f6906d0760bf4da0dae872415391ededefad23fd471c47dc53b0ddf3c42) | 2026-11-10 | https://hub.docker.com/_/postgres | Same minor as the CNPG operand. Dev Services only (the CNPG image has no docker-library entrypoint, so Dev Services cannot use it). |
| Apicurio Registry | quay.io/apicurio/apicurio-registry:3.3.4 (sha256:3ee4423c0f212c364d9525f7ab22ae1a009f610f3e6b8fc2cdba8b2ae68de460) | 2026-11-10 | https://github.com/Apicurio/apicurio-registry/releases/tag/3.3.4 | Not the `-mutable` variant. The Quarkus platform manages apicurio-registry serde at 3.1.7; same 3.x major, verify serde against the 3.3.4 server at first use. |
| Debezium Connect image | quay.io/debezium/connect:3.7.1.Final (sha256:9bf1239e4cc46199848d24489517432c98ab0071eb2b1fc3408a8767dbd65386) | 2026-11-10 | https://quay.io/repository/debezium/connect?tab=tags | Published image first (owner decision). Bundles Kafka 4.3.1 (image env KAFKA_VERSION=4.3.1) and DEBEZIUM_VERSION=3.7.1.Final; runs as user kafka, entrypoint /docker-entrypoint.sh, Cmd start. Connect 4.3.1 against the 4.3.2 broker is the same minor line. JDK inside not inspected; fallback if JDK 25 matters is a UBI build on ubi9/openjdk-25 (never a lower JDK). Image was rebuilt 2026-11-10, so the digest may change for the same tag; re-check when infra lands. |
| debezium-quarkus-outbox | io.debezium:debezium-quarkus-outbox:3.7.1.Final (plus -deployment is resolved transitively) | 2026-11-10 | https://repo1.maven.org/maven2/io/debezium/debezium-quarkus-outbox/maven-metadata.xml | Exists on Maven Central, same 3.7.1.Final release. No quarkiverse replacement found (io.quarkiverse.debezium is not on Central). HOLD: Debezium builds it against Quarkus 3.33.2 (quarkus.version.runtime in the debezium v3.7.1.Final pom); compatibility with platform 3.40.1.2 is not checked until the outbox chapter builds it. If it fails, hold the outbox example on its own and record the reason here. Not managed by the Quarkus BOM, so pin the version explicitly. |
| docker.io/grafana/otel-lgtm | docker.io/grafana/otel-lgtm:0.40.0 (sha256:72e05804790e6e0d8c0b70a6d5ff1f721ef7b203463374631e99aa3429bd0cf6) | 2026-11-10 | https://github.com/grafana/docker-otel-lgtm/releases/tag/v0.40.0 | Published 2026-11-06. |
| ubi10/openjdk-25 | registry.access.redhat.com/ubi10/openjdk-25:1.24-19 (sha256:79b8ab3de9d0b8bf1e5a4e951523bc7bee3f17cfe129fe22377248999d16482d) | 2026-11-10 | https://catalog.redhat.com/software/containers/ubi10/openjdk-25 | Build image. JAVA_VERSION=25, release 1793642335; 1.24-19 equals `latest` and `1.24` today. |
| ubi10/openjdk-25-runtime | registry.access.redhat.com/ubi10/openjdk-25-runtime:1.24-19 (sha256:48aa04c85c098713c6c4dc3f35a7a23d2c129e6dc139b3b28f3684ea249e0e86) | 2026-11-10 | https://catalog.redhat.com/software/containers/ubi10/openjdk-25-runtime | Runtime image, same release as the build image. ubi9/openjdk-25 (1.24-3.1793588563) is the fallback only on a real incompatibility. |
| Ruby image | docker.io/library/ruby:4.0.9 (sha256:07042b74555ff2eef7365abd72d39cba6e499ab63e2d2942fd34d4778338495b) | 2026-11-10 | https://hub.docker.com/_/ruby | Site build container (ruby:4.0 + Jekyll 4.4.1 known to work). |
| Jekyll | 4.4.1 | 2026-11-10 | https://rubygems.org/gems/jekyll | Latest 4.x. |
| kramdown-parser-gfm | 1.1.0 | 2026-11-10 | https://rubygems.org/gems/kramdown-parser-gfm | |
| webrick | 1.9.2 | 2026-11-10 | https://rubygems.org/gems/webrick | |
| JUnit Jupiter (BOM) | 6.1.3 | 2026-11-10 | https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter/maven-metadata.xml | Same version the Quarkus platform BOM manages; do not override. |
| maven-surefire-plugin | 3.6.1 | 2026-11-10 | https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-surefire-plugin/maven-metadata.xml | Latest final. Use -Dtest/-DskipTests flags as the Quarkus docs show. |
| maven-compiler-plugin | 3.16.0 | 2026-11-10 | https://repo1.maven.org/maven2/org/apache/maven/plugins/maven-compiler-plugin/maven-metadata.xml | HOLD: 4.0.0-beta-5 is a prerelease. Use `maven.compiler.release` 25. |
| Python | 3.14.10 | 2026-11-10 | https://www.python.org/downloads/ | Latest 3.14 patch (docker.io/library/python lists 3.14.10). Python 3.15.2 also exists; this book's Python tooling (validate.py, diagram generator) stays on the 3.14 floor. Host python3 is 3.15.2, which also satisfies the floor. |
| PyYAML | 6.0.3 | 2026-11-10 | https://pypi.org/project/PyYAML/ | Latest on PyPI. |
| actions/checkout | v7.0.1 | 2026-11-10 | https://github.com/actions/checkout/releases/tag/v7.0.1 | EIP Camel still uses v4/v5; take the newest. |
| actions/setup-java | v6.2.0 | 2026-11-10 | https://github.com/actions/setup-java/releases/tag/v6.2.0 | With distribution temurin, java-version 25. |
| actions/setup-python | v7.1.0 | 2026-11-10 | https://github.com/actions/setup-python/releases/tag/v7.1.0 | |
| ruby/setup-ruby | v1.338.0 | 2026-11-10 | https://github.com/ruby/setup-ruby/releases/tag/v1.338.0 | EIP Camel uses the floating v1; pin the exact tag. |
| actions/configure-pages | v6.0.0 | 2026-11-10 | https://github.com/actions/configure-pages/releases/tag/v6.0.0 | |
| actions/upload-pages-artifact | v5.0.0 | 2026-11-10 | https://github.com/actions/upload-pages-artifact/releases/tag/v5.0.0 | |
| actions/deploy-pages | v5.0.1 | 2026-11-10 | https://github.com/actions/deploy-pages/releases/tag/v5.0.1 | |
| dorny/paths-filter | v4.0.3 | 2026-11-10 | https://github.com/dorny/paths-filter/releases/tag/v4.0.3 | Only if the examples workflow filters by path; omit otherwise. |
| Docker Engine (host) | 29.9.3 | 2026-11-10 | `docker version --format '{{.Server.Version}}'` | Recorded from the host, no changes. Docker Engine only, never Desktop or podman. |
| Docker Compose plugin (host) | 5.7.0 | 2026-11-10 | `docker compose version --short` | Recorded from the host, no changes. |

## Deferred: survey at first use

| Component | Pin (exact) | Surveyed | Source URL | Notes/holds |
|---|---|---|---|---|
| Narayana LRA coordinator | survey at first use (r09) | not yet | https://github.com/jbosstm/narayana | |
| Ollama | survey at first use (r09) | not yet | https://github.com/ollama/ollama/releases | |
| qwen2.5:3b model | survey at first use (r09) | not yet | https://ollama.com/library/qwen2.5 | |
| Quarkus Flow | survey at first use | not yet | https://github.com/quarkiverse/quarkus-flow | |
| Drools | survey at first use | not yet | https://github.com/apache/incubator-kie-drools | |
| quarkus-langchain4j | survey at first use (r09) | not yet | https://github.com/quarkiverse/quarkus-langchain4j | |
| Apache Flink | survey at first use | not yet | https://flink.apache.org/downloads/ | |
| RisingWave | survey at first use (r13) | not yet | https://github.com/risingwavelabs/risingwave/releases | |
| minikube | survey at first use (r11) | not yet | https://github.com/kubernetes/minikube/releases | Indicative only: latest release seen 2026-11-10 was v1.39.0. |
| Kubernetes | survey at first use (r11) | not yet | https://github.com/kubernetes/kubernetes/releases | Indicative only: latest release seen 2026-11-10 was v1.37.2. |

## Kafka choice against Strimzi

The latest Strimzi release is 1.3.1 (2026-11-04). Its kafka-versions.yaml lists 4.2.0, 4.2.1, 4.2.2, 4.3.0, 4.3.1 and 4.3.2 as supported, with 4.3.2 as the default. Apache Kafka 4.4.0 is on Maven Central but is not in that list, so it is not used. The book pins Kafka 4.3.2 everywhere (the docker.io/apache/kafka broker in compose, the Kafka Dev Services image, and the Strimzi Kafka resource at r11), which is the newest version the latest Strimzi supports. The Debezium Connect image bundles Kafka 4.3.1 and the Quarkus platform brings kafka-clients 4.2.1; both are the same major line as the 4.3.2 broker and are not overridden.
