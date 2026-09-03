# Load tests

[k6](https://k6.io) scripts, inherited from
[bcgov/quickstart-openshift](https://github.com/bcgov/quickstart-openshift).

> **These are unmodified samples and do not currently work against FTA.**
> `backend-test.js` requests `${BACKEND_URL}/v1/users`, a quickstart endpoint
> FTA does not expose — every FTA API path is under `/api/fta/**`. They are kept
> as a starting point, not as a working suite.

No workflow runs them. `.github/workflows/reusable-tests.yml` deliberately drops
the load-test job: besides the endpoint mismatch, FTA's backend talks to the
**shared** BC Gov Oracle instance, so a load run would degrade a database other
teams depend on.

## If you adopt them

1. Repoint `backend-test.js` at a real read-only endpoint (e.g.
   `/api/fta/clients`), and give it a bearer token — every `/api/fta/**` route
   requires one.
2. Talk to Platform Services **before** running anything substantial on
   OpenShift, and never point a load run at an environment sharing the Oracle
   instance with other applications.
3. Run locally against a disposable target:

   ```bash
   BACKEND_URL=http://localhost:8080 k6 run tests/load/backend-test.js
   FRONTEND_URL=http://localhost:3000 k6 run tests/load/frontend-test.js
   ```
