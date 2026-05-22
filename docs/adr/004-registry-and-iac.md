# ADR 004: Container registry and infrastructure as code

## Status

Proposed — choose one path per environment before implementation.

## Context

Today backend deploy uploads `app.jar` + `Dockerfile` over SCP and runs `docker compose` on the server. This works for a single team and few services but does not scale to many hosts, rollbacks across regions, or audited image promotion.

## Options

### A — Docker Registry (recommended next step)

| Aspect | Detail |
|--------|--------|
| Flow | CI builds image → push to GHCR/ECR/ACR → server `docker compose pull` + `up` |
| Pros | Faster deploys, immutable tags, easy rollback (`app:20260521-1430`) |
| Cons | Registry auth, image scanning, storage cost |
| Fits | Current compose-per-service layout; replace scp JAR step with `image:` pin |

**Implementation sketch:**

1. Add `IMAGE_REGISTRY` / `IMAGE_TAG` to `ops/config/deploy.env`.
2. CI job `docker build` + `docker push` on merge to main.
3. `deploy.sh` uploads compose + env only; `docker compose pull` on server.
4. Keep JAR path as fallback via `DEPLOY_MODE=jar|registry` flag.

### B — Infrastructure as Code (Terraform / Ansible)

| Aspect | Detail |
|--------|--------|
| Flow | IaC provisions VM, Docker, firewall, users; app deploy stays scripts or registry |
| Pros | Reproducible servers, drift detection |
| Cons | State backend, secrets in vault, learning curve |
| Fits | New ADR per cloud (AWS vs on-prem) |

**Implementation sketch:**

1. `infra/terraform/` or `infra/ansible/` with modules: docker, user, ufw, directory layout.
2. `ensure-prereqs.sh` becomes idempotent play (or stays bootstrap).
3. App deploy unchanged until registry ADR done.

### C — Kubernetes (out of scope for now)

Not recommended until service count and team size justify ops overhead.

## Decision (interim)

- **Keep JAR + SCP** as default until registry credentials exist.
- **Document** registry and IaC as explicit phase-2; no half-migrated state.
- **CI** validates compose health locally (see `ops/ci/integration-stack.sh`).

## Consequences

- When registry is adopted, update `deploy/upload.sh`, server compose templates, and ADR 002 deploy flow.
- When IaC is adopted, `DEPLOY_SERVER` may come from Terraform output instead of manual `deploy.env`.
