# Track My Board Games: AWS Deployment Guide

Run command examples from the repository root unless a step says otherwise.

This guide covers:

1. React frontend deployment to AWS Amplify Hosting.
2. Backend option 1: Spring Boot JAR/ZIP deployment to Elastic Beanstalk.
3. Backend option 2: Docker image deployment through ECR to Elastic Beanstalk.

Choose one backend option per environment. These instructions do not create AWS resources or add deployment files to the project.

## Architecture and environments

```text
Browser -> React on Amplify -> HTTPS API on Beanstalk -> Managed PostgreSQL
```

Start with Dev, verify it end-to-end, then repeat for Production.

| Setting | Dev | Production |
|---|---|---|
| Amplify branch | `development` | Your production branch, normally `main` |
| Frontend address | `https://development.dag28nzl054yt.amplifyapp.com` | Production Amplify address or custom domain |
| Backend | Separate Dev Beanstalk environment | Separate Production Beanstalk environment |
| Database | Isolated Dev database | Production database |
| Secrets/configuration | Dev values | Production values |

Both frontend and backend can stay in the same Git repository. Amplify deploys only the frontend build output.

Two running backend environments incur separate costs. Single-instance Beanstalk is cheaper but is not highly available. Database, load balancer, storage, and other resources can charge independently.

## Before you begin

- Confirm the intended Git branch contains the latest committed code.
- Choose an AWS region and set up AWS budget alerts.
- Have your Auth0 SPA application domain, client ID, and API audience identifier available.
- Have a managed PostgreSQL database and its connection settings ready before starting the backend.
- The current backend targets **Java 25**.
- Keep credentials out of Git, ZIP packages, Docker images, and frontend build variables.

---

## Part 1: Amplify Hosting

### 1. Connect GitHub

1. Open Amplify Hosting and create/deploy an application.
2. Select GitHub and authorize the Amplify integration.
3. Prefer access to only the required repository.
4. Select the repository and `development` branch.

Amplify builds committed code from Git. Local unstaged changes and ignored local configuration files are not included.

### 2. Configure the build

Your repository contains a frontend and backend, so it is a monorepo.

Use **one** of the following configurations. Do not combine the output paths from one with the working directory of the other.

#### A. Current setup: build from the repository root

If there is no application-root field and `frontend` is directly inside the Git repository:

**Frontend build command**

```bash
npm --prefix frontend ci && npm --prefix frontend run build
```

**Build output directory**

```text
frontend/dist
```

These paths are relative to the Git checkout on AWS, not your Windows directory.

#### B. Explicit monorepo application root

If you select the monorepo option and set the app root to `frontend`:

- Install dependencies with `npm ci` in the pre-build phase.
- Build with `npm run build`.
- Publish `dist`.

An example monorepo build specification is:

```yaml
version: 1
applications:
  - appRoot: frontend
    frontend:
      phases:
        preBuild:
          commands:
            - npm ci
        build:
          commands:
            - npm run build
      artifacts:
        baseDirectory: dist
        files:
          - '**/*'
      cache:
        paths:
          - node_modules/**/*
```

For this configuration, `AMPLIFY_MONOREPO_APP_ROOT` must also be `frontend`. Verify the generated build settings and build log.

If Git contains a parent folder above `frontend`, include that folder in the applicable paths.

**Do not use `npm run dev`:** it starts a development server rather than producing deployable files.

Use a Node version supported by your current Vite version. Check the Amplify build log for the selected version.

### 3. Add frontend environment variables

Configure these in Amplify, using branch-specific overrides for Dev and Production:

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | Backend HTTPS origin, for example `https://api-dev.example.com` |
| `VITE_AUTH0_DOMAIN` | Your Auth0 tenant domain |
| `VITE_AUTH0_CLIENT_ID` | Your Auth0 SPA client ID |
| `VITE_AUTH0_AUDIENCE` | Your Auth0 API identifier, currently `https://trackmyboardgames-api` |

Important:

- Do not add `/api` or a trailing slash to the backend base URL. Frontend requests already append the API path.
- Do not use `localhost` for the hosted API: it refers to the visitor's computer.
- The audience is an identifier, not necessarily a working website. It does not need to change to the Beanstalk URL.
- Domain, SPA client ID, and audience are public configuration and can be included in browser code.
- Never put an Auth0 client secret, database password, AWS credentials, or access/refresh token in a `VITE_` variable.
- Vite embeds these values at build time. Rebuild/redeploy after changing them.
- You can deploy the frontend before the backend, but API functionality will not work until the backend is reachable and correctly configured.

### 4. Configure SPA routing

In Amplify, open **Rewrites and redirects -> Manage redirects**.

Replace the default catch-all fallback with this SPA rewrite if it is the only existing rule:

```json
[
  {
    "source": "</^[^.]+$|\\.(?!(css|gif|ico|jpg|js|png|txt|svg|woff|woff2|ttf|map|json|webp)$)([^.]+$)/>",
    "status": "200",
    "target": "/index.html"
  }
]
```

If you already have intentional domain redirects or other routing rules, preserve them and review rule order instead of replacing everything.

- In the JSON editor, there must be **two backslashes** before the dot.
- Use status `200`, not `404-200`.
- Leave the country condition unset.
- This one rule covers React routes; you do not need separate rules for each game or scan token.
- Static assets such as JavaScript and CSS are excluded.

Example routes covered:

```text
/collection
/collection/<game-id>
/scan
/scan/<token>
```

Without a suitable fallback, navigation inside the loaded app may work, but refreshing or opening a nested URL directly can fail.

This is not general error handling. API authentication and validation errors must retain their real status codes. Your API is on a separate backend origin.

### 5. Configure Auth0

For the current Dev deployment, add:

| Auth0 setting | Value |
|---|---|
| Allowed Callback URLs | `https://development.dag28nzl054yt.amplifyapp.com/` |
| Allowed Logout URLs | `https://development.dag28nzl054yt.amplifyapp.com/` |
| Allowed Web Origins | `https://development.dag28nzl054yt.amplifyapp.com` |

Use `/`, **not `/login`**, for the callback. The current React Auth0 provider uses the origin plus `/`; sign-out also returns there.

Keep the applicable localhost entries for local development:

```text
Callback: http://localhost:5173/
Logout:   http://localhost:5173/
Origin:   http://localhost:5173
```

Save the Auth0 settings. Add equivalent Production/custom-domain addresses when those are available.

The Auth0 application should be a **Single Page Application**, using the SDK's Authorization Code Flow with PKCE, not a client secret.

#### Safari-compatible session restoration

The React provider requests the API audience and both `read:games` and
`write:games` during interactive login. Existing page-level requests use those
permissions for collection, shelf images, play history, scanning, and QR labels;
backend permission enforcement is unchanged.

Before deploying the refresh-token configuration:

1. In **Auth0 -> Applications -> APIs**, select the API identified by
   `https://trackmyboardgames-api`. Under **Settings**, enable **Allow Offline Access**.
2. In **Applications -> Applications**, select the SPA. Under **Settings**, enable
   **Refresh Token Rotation**. Confirm **Advanced Settings -> Grant Types** includes
   **Refresh Token**, then save. Set appropriate refresh-token idle and maximum
   expiration policies rather than assuming sessions last indefinitely.
3. Keep callback/logout URLs and Allowed Web Origins configured for each frontend,
   including `https://www.swampdragoninn.casa/` for callback/logout and
   `https://www.swampdragoninn.casa` for Web Origins.
4. Build and deploy React. Existing users should sign out and sign in again to
   obtain refresh tokens and the newly requested permissions.

The SDK automatically adds `offline_access` when `useRefreshTokens` is enabled.
It persists its token cache in local storage so Safari can restore sessions after
reload without requiring third-party Auth0 cookies. Hidden-iframe fallback is
disabled. Local storage increases token exposure if an XSS vulnerability occurs;
do not log tokens, insert untrusted scripts, or treat storage as a secure vault.
Expired or revoked sessions still require interactive sign-in; existing login
and page error displays surface authentication errors.

Verify on Safari with cross-site tracking protection left enabled: sign in,
load the collection, reload, and test a write operation. Check later token renewal
uses `/oauth/token` with the refresh-token grant, not iframe `/authorize` requests.
Also verify sign-out and desktop/local development. Live rotation and Safari
behavior require testing against the configured Auth0 tenant, not just a build.

### 6. Deploy and verify

- Confirm the Amplify build succeeds and publishes `dist`.
- Open the hosted site.
- Verify sign-in and sign-out.
- Open a nested route directly and refresh it.
- Confirm JavaScript/CSS assets load normally.
- After deploying the backend, verify collection loading and saving.

Future pushes to the connected branch trigger builds/deployments.

---

## Part 2: Backend preparation for either Beanstalk option

### 1. Configure backend settings and secrets

In the Beanstalk **Environment properties** section, click **Add environment property** for each application setting below.

Leave the platform defaults `M2_HOME`, `M2`, and `GRADLE_HOME` unchanged. They describe installed build tools and do not replace your application's settings.

| Name | Value for Dev / purpose |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://development.dag28nzl054yt.amplifyapp.com` |
| `DB_URL` | `jdbc:postgresql://<database-host>:5432/<database-name>` with your service's recommended TLS parameters |
| `DB_USERNAME` | Your managed database username |
| `DB_PASSWORD` | Your managed database password, supplied through a secret reference |
| `AUTH0_ISSUER_URI` | Your Auth0 issuer URL, including `https://` and normally a trailing `/` |
| `AUTH0_AUDIENCE` | `https://trackmyboardgames-api`, if using the existing Auth0 API |
| `SERVER_PORT` | `5000` for Java SE; `8080` for the Docker example |

The CORS code change is complete. You only need to deploy the updated build and supply the environment setting. When unset, CORS defaults to `http://localhost:5173` for local development.

For multiple allowed origins, use comma-separated values with no path or trailing slash:

```text
http://localhost:5173,https://development.dag28nzl054yt.amplifyapp.com
```

Use the Production frontend origin in the Production environment. CORS and Auth0's origin allowlist are separate; CORS does not replace authentication.

Use supported Secrets Manager/Parameter Store references for sensitive values and grant only the required IAM permissions.

Do not upload your local credentials file.

### 2. Verify database access

- The database must exist before application startup.
- For RDS, ensure the backend can reach it through the selected VPC/networking and allow PostgreSQL from the backend's security group.
- For Neon, the backend needs outbound connectivity to the Neon endpoint on port 5432. No inbound database rule on the Beanstalk instance is needed.
- Follow the database service's recommended TLS settings.
- Use separate Dev and Production data.

Flyway runs pending migrations during application startup. Back up Production and review migration compatibility before deployment.

#### Current Neon development setup

- Project: `TrackMyBoardGames` (`billowing-leaf-68750763`).
- Region: AWS US West (Oregon), `aws-us-west-2`.
- Branch: `development` (renamed from the initial unused `production` branch).
- Database: `trackmyboardgames`.
- Neon Auth is disabled; the app continues using Auth0.
- The existing PostgreSQL JDBC driver, JPA, and Flyway remain unchanged.
- The direct endpoint is used because Flyway migrations run on application startup.

Local credentials are stored in the ignored `backend/config/application-neon.properties` file, outside application resources. They are not included in the JAR or React build.

Local PostgreSQL remains the default. To opt into Neon, run from the backend directory:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=neon"
```

Use Java 25 as described below. The local Neon profile allows the pool to shrink to zero idle connections and limits it to five connections; free-plan usage and wake-up latency still need monitoring.

To configure Beanstalk, use Neon's Connect panel for the `development` branch and set:

```text
DB_URL=jdbc:postgresql://<direct-neon-host>:5432/trackmyboardgames?sslmode=require
DB_USERNAME=<neon-database-role>
DB_PASSWORD=<secret or SecureString reference>
```

Do not upload the local credential file or put these credentials into Amplify. Do not use a credential-bearing `postgresql://` URL as the JDBC URL.

Project-level Neon agent skills are installed under `.agents/skills`, with provenance in `skills-lock.json`. The VS Code MCP configuration in `.vscode/mcp.json` uses OAuth and is scoped to this project; it contains no API key. Authorize the server in VS Code on first use. These are development tools, not runtime dependencies.

### 3. Plan HTTPS

Amplify HTTPS does not automatically secure the backend.

An HTTPS frontend calling an HTTP backend is blocked as mixed content.

Choose one:

1. **Load-balanced Beanstalk + ACM certificate:** terminate HTTPS at the load balancer. This adds load-balancer cost.
2. **Single-instance Beanstalk with instance-side HTTPS:** configure certificate renewal and the reverse proxy yourself using deployment configuration that survives instance replacement.

A Route 53 record alone does not provide HTTPS. Use a domain you control for the API certificate and configure DNS to the environment.

#### Current Dev: single-instance Java SE with Let's Encrypt

The backend bundle now includes DNS-validated HTTPS for `api-dev.swampdragoninn.casa`.
Keep the existing Route 53 CNAME pointing to the Dev Beanstalk environment. No load
balancer or ACM certificate is required.

Before uploading this bundle:

1. On the environment's EC2 instance role, allow `route53:ListHostedZones` on `*`
   and `route53:GetChange` on `arn:aws:route53:::change/*`.
2. Allow `route53:ChangeResourceRecordSets` on
   `arn:aws:route53:::hostedzone/Z010213337Y1R1IFV90JX`, restricted with
   `ForAllValues:StringEquals` to record type `TXT` and normalized record name
   `_acme-challenge.api-dev.swampdragoninn.casa`.
   Do not add access keys; Certbot uses the instance role.
3. Add the plain-text Beanstalk environment property `LETSENCRYPT_EMAIL` with your
   certificate account contact email. Keep `SERVER_PORT=5000` and, if `PORT` is
   explicitly configured, keep it `5000` too. Preserve the DB, Auth0, and CORS settings.
4. If you previously added port 443 manually to the instance security group,
   remove only that manually added HTTPS rule immediately before deploying:
   `.ebextensions/https.config` now owns the rule. Leave HTTP and other rules intact.
   An identical pre-existing rule can cause CloudFormation deployment to fail.

From `backend`, using Java 25:

```powershell
.\mvnw.cmd clean package
.\package-beanstalk.ps1
```

Upload `backend/target/trackmyboardgames-beanstalk.zip` through Beanstalk's
**Upload and deploy**, not the standalone JAR. The packaging script includes only
the executable JAR (as `application.jar`), `Procfile`, `.platform`, and `.ebextensions`.
It excludes local configuration, credentials, source files, and build-time tools.
It writes deployment text files as UTF-8 without a BOM and with Unix LF line
endings, including helper scripts that Beanstalk does not automatically normalize.
Future deployments must also use this ZIP to retain HTTPS setup.

The prebuild hook installs pinned Certbot packages in a dedicated Python 3.11
virtual environment and obtains the certificate before Beanstalk configures Nginx.
Configuration deployments run the same setup. HTTP remains available; the TLS
server uses Beanstalk's existing upstream mappings and health logging.
Certificate keys remain on the instance under `/etc/letsencrypt`, not in Git.
A root systemd timer checks renewal twice daily, reloads Nginx after renewal,
and records failures in the system journal. Monitor renewal failures; Let's
Encrypt email is not a substitute for renewal monitoring.
Instance replacements obtain a new certificate automatically, provided DNS,
IAM, and outbound package/ACME/Route 53 access are available. Frequent replacements
can hit Let's Encrypt issuance limits; this is a low-traffic Dev setup, not a
high-availability or shared-certificate solution.

After deployment:

1. Confirm Beanstalk health returns to OK.
2. Open `https://api-dev.swampdragoninn.casa/api/health` and verify the certificate
   is trusted and the JSON reports `status: ok`.
3. Set Amplify `VITE_API_BASE_URL=https://api-dev.swampdragoninn.casa` and rebuild.
   Keep backend CORS allowing the actual Amplify frontend origin.
4. When instance access is available, check
   `systemctl status trackmyboardgames-certbot.timer` and test renewal with
   `/opt/trackmyboardgames-certbot/bin/certbot renew --dry-run`.

If deployment fails, inspect `/var/log/eb-engine.log` and
`/var/log/letsencrypt/letsencrypt.log`; do not disable TLS verification or expose
the Java port to work around errors. This configuration has to be verified on AWS;
local packaging checks do not prove certificate issuance or renewal.

### 4. Health checks and capacity

- Configure `/api/health` as the health-check path where supported.
- The application already permits that endpoint without authentication.
- Choose enough memory for Spring Boot and measure actual usage.
- A single-instance environment can have downtime during failures or deployments.

---

## Backend option 1: Java SE JAR/ZIP upload

### 1. Build the executable JAR

Open PowerShell in:

```text
C:\Users\edwar\OneDrive\Documents\repos\TrackMyBoardGames_JavaReact\TrackMyBoardGames\backend
```

Verify Maven uses the intended JDK:

```powershell
.\mvnw.cmd -version
```

The project targets Java 25. If necessary, use your installed Java 25 for this terminal:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Build:

```powershell
.\mvnw.cmd clean package
```

This runs tests; the required local test database/configuration must be available.

Expected artifact:

```text
target\trackmyboardgames-0.0.1-SNAPSHOT.jar
```

### 2. Create the Beanstalk environment

1. Open Elastic Beanstalk and create an application.
2. Create a **Web server environment**.
3. Choose **Java SE**, not Tomcat.
4. Select **Corretto 25 on 64bit Amazon Linux 2023**. This matches the project's Java 25 target. The platform version (for example `4.13.0`) is the Beanstalk platform release, not the Java version; prefer AWS's current recommended supported release.
5. Choose a single-instance environment for a lower-cost initial deployment, or load-balanced if that is your chosen HTTPS/availability approach.
6. Configure the Beanstalk service role and EC2 instance profile.
7. Configure networking, database access, secrets, and environment settings.
8. Set `SERVER_PORT=5000` to match this Java SE proxy setup.

Do not assume an older Java runtime can run your Java 25 JAR. If a suitable Java SE platform is unavailable, use the Docker option rather than arbitrarily changing Java versions.

### 3. Upload either a JAR or ZIP

**Simplest:** upload the executable JAR directly.

Do not ZIP the backend source folder or the entire `target` directory. Run `clean package` first, then upload `target\trackmyboardgames-0.0.1-SNAPSHOT.jar`. Running `compile` or `test` alone does not create the deployment JAR.

**ZIP package:** put these at the archive root:

```text
application.jar
Procfile
```

Rename/copy the executable build artifact to `application.jar` in a separate packaging directory.

The `Procfile` is a plain text file with no extension:

```text
web: java -jar application.jar
```

ZIP the files themselves, not their containing folder. Additional Beanstalk configuration directories can be included when needed.

Do not include the whole repository, frontend source, local secrets, or the non-executable `.jar.original` artifact.

### 4. Verify and update

1. Wait for deployment and health checks.
2. Inspect Beanstalk events/logs if startup fails.
3. Check `/api/health`.
4. Verify the HTTPS endpoint and protected requests through Amplify.

For future releases, rebuild and use **Upload and deploy** with a new application version.

---

## Backend option 2: Docker image + ECR + Beanstalk

The image is pushed to ECR. Beanstalk receives a small descriptor that tells it which image to pull.

### 1. Set up local tools

- Install Docker Desktop and use Linux containers.
- Install/configure the AWS CLI with your intended account and region, preferably using short-lived/SSO credentials.
- Build the backend JAR using the preceding Maven steps.

Developing on Windows is compatible with deploying a Linux container.

### 2. Create a Dockerfile

Create a `Dockerfile` in the backend directory. A minimal learning example:

```dockerfile
FROM eclipse-temurin:25-jre

WORKDIR /app
COPY target/trackmyboardgames-0.0.1-SNAPSHOT.jar application.jar

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "application.jar"]
```

Before business production use, harden the image: run as a non-root user, verify and maintain the base image, use reproducible version/digest pinning where appropriate, and scan dependencies/images.

Never bake database credentials or environment-specific secrets into the image.

### 3. Build the image

From the backend directory, for an x86-64 Beanstalk instance:

```powershell
docker build --platform linux/amd64 -t trackmyboardgames-api:dev-001 .
```

Use `linux/arm64` only when deliberately selecting compatible ARM instances and images.

The same image can be used in different environments with different runtime configuration.

### 4. Push to Amazon ECR

1. Open **ECR -> Private repositories**.
2. Create `trackmyboardgames-api` in the intended region.
3. Select **View push commands**.
4. Select the appropriate command instructions for your shell.
5. Authenticate Docker, tag the image with the ECR repository address, and push it.

Use a unique version tag such as `dev-001` or a commit SHA. Avoid overwriting `latest` for releases you need to trace or roll back.

### 5. Create Dockerrun.aws.json

Use this single-container descriptor, replacing the placeholders:

```json
{
  "AWSEBDockerrunVersion": "1",
  "Image": {
    "Name": "<account-id>.dkr.ecr.<region>.amazonaws.com/trackmyboardgames-api:dev-001",
    "Update": "true"
  },
  "Ports": [
    {
      "ContainerPort": "8080"
    }
  ]
}
```

Use version **1** for this non-Compose Docker platform example. Version 2 applies to the separate ECS-managed Docker platform.

### 6. Create the Docker Beanstalk environment

1. Create a Web server environment.
2. Choose **Docker running on Amazon Linux 2023**, where available.
3. Do not choose the separate ECS-managed Docker platform for this example.
4. Select an instance architecture matching the image.
5. Grant the EC2 instance profile permission to pull from the private ECR repository.
6. Configure database networking, Auth0 settings, and secrets.
7. Keep `SERVER_PORT=8080` to match the descriptor.
8. Configure the health-check path and chosen HTTPS approach.

Upload `Dockerrun.aws.json` directly, or include it at the root of a ZIP with any additional deployment configuration.

Beanstalk pulls the image from ECR and starts the container. You do not upload a `docker save` image archive to Beanstalk.

### 7. Verify and update

- Check environment health and logs.
- Verify `/api/health` over the final HTTPS endpoint.
- Test authenticated frontend operations.

For each release:

1. Build and push a newly tagged image.
2. Update the descriptor's image reference.
3. Upload and deploy a new Beanstalk application version.

You can later automate these steps with GitHub Actions. Amplify's frontend Git integration does not automatically deploy the Java backend.

---

## Final integration checklist

- [ ] Amplify builds the intended branch and publishes the correct `dist` directory.
- [ ] Frontend variables point to the correct environment.
- [ ] Auth0 callback/logout/origin allowlists include the hosted frontend.
- [ ] Direct navigation and refresh work on collection, game history, and scan routes.
- [ ] Backend runs with a compatible Java runtime.
- [ ] Backend HTTPS is configured; no mixed-content requests occur.
- [ ] Backend CORS allows the hosted frontend origin.
- [ ] Database access is restricted and migrations succeed.
- [ ] Collection loading, add/edit/delete, play history, and QR scanning work.
- [ ] Logs, budget alerts, database backups, and recovery procedures are configured.
- [ ] Dev and Production do not share sensitive runtime configuration or application data unintentionally.

## Common failures

| Symptom | Check |
|---|---|
| Amplify cannot find package.json | Build working directory and `frontend` path |
| Amplify build never finishes | Do not use `npm run dev` |
| Blank frontend or missing assets | Build output directory and rewrite rules |
| JSON reports a bad escaped character | SPA regex must contain `\\.` in the JSON editor |
| Auth0 callback mismatch | Use the exact origin plus `/`, not `/login` |
| Frontend calls localhost | Set hosted `VITE_API_BASE_URL` and rebuild |
| Browser reports mixed content | Backend URL must use HTTPS |
| Browser reports CORS failure | Backend still only allows localhost or the wrong frontend origin |
| UnsupportedClassVersionError | Align build/runtime Java versions and clean stale compiled classes |
| Beanstalk proxy reports 502 | Application startup, DB connectivity, Java version, and port mapping |
| ECR image pull fails | Repository address, image tag, region, and instance-profile permissions |

## Official references

- [Amplify monorepo build configuration](https://docs.aws.amazon.com/amplify/latest/userguide/monorepo-configuration.html)
- [Amplify SPA rewrite examples](https://docs.aws.amazon.com/amplify/latest/userguide/redirect-rewrite-examples.html#redirects-for-single-page-web-apps-spa)
- [Elastic Beanstalk Java SE deployment](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-platform.html)
- [Elastic Beanstalk Docker deployment](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/single-container-docker-configuration.html)
