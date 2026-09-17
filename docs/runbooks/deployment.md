# Deploying coupon-service

`coupon-service` is Tier 1 and sits on the payments path, so **deployments to Production are
gated**. Nothing is deployed until a change request says it may be.

## What happens when you push to `main`

```
git push origin main
  └─▶ .github/workflows/deploy.yml   →  environment: Production
       └─▶ GitHub holds the job at the "Atlassian" deployment protection rule
            └─▶ the Atlassian app submits the deployment to JSM's gating API,
                with the service IDs from .jira/config.yml
                 └─▶ Jira automation creates the change request
                      └─▶ the preset flow stamps change type and risk
                           ├─ low risk       → auto-approved → GitHub resumes
                           └─ needs signoff  → the change freezes until an approver decides
                                ├─ approve → GitHub resumes the job
                                └─ decline → the job fails, with the CR linked in the run
```

**The developer does nothing.** No Jira key in the commit message, no extra workflow step, no
custom action. Push as usual.

## Where the wiring lives

| Thing | Where |
| --- | --- |
| Gated environments, retry and poll settings | `.jira/config.yml` |
| The service the deployment is submitted against | `.jira/config.yml`, `deployments.services.ids` |
| The Production environment and its protection rule | GitHub repository settings → Environments |
| Change type, risk stamping and auto-approval | the JSM space's Automation library |

The service ID is a base64-wrapped ARI. It is what puts `coupon-service` in **Affected services**
on the change request — which is also what turns the service-graph lens on in the risk
assessment, so the upstream and downstream dependency walk depends on it being right.

## Reading a held deployment

| What you see | What it means |
| --- | --- |
| Job sits at `waiting`, Deployment protection rules → Atlassian | working as intended — the change request is open and awaiting its decision |
| `waiting` → `failure` after roughly 40 seconds, message `rejected by JSM Change Request: undefined` | gating is **not** wired up. JSM returned `gatingStatus: invalid`, no CR was created, and the issue key came back empty. Check the space's change-management settings and that the service ID matches an entity on the right site. |
| `failure` with a CR linked in the run summary | an approver declined. The reason is on the change request. |

## Re-running

Deployments are **per-SHA**. A new test needs a fresh commit to `main`, or use **Re-run jobs** on
a previous run.

`can_admins_bypass` is `true` on Production, so a misbehaving gate can be forced through rather
than debugged live.
