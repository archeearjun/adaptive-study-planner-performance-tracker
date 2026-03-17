# Vercel Deployment Notes

This repository contains two deliverables:

1. A JavaFX desktop application
2. A static portfolio site at the repository root

Only the static portfolio site is intended for Vercel deployment.

## What Gets Deployed

Deploy the repository root as a static site. The relevant files are:

- `index.html`
- `site.css`
- `vercel.json`
- `docs/screenshots/*`

## Deployment Flow

1. Make sure the GitHub repo is up to date.
2. Authenticate with Vercel:
   - `npx vercel@latest login`
   - or use a valid `VERCEL_TOKEN`
3. From the repository root, run:
   - `npx vercel@latest deploy`
4. When the preview looks correct, promote it:
   - `npx vercel@latest deploy --prod`

The repository also includes:

- `powershell -ExecutionPolicy Bypass -File scripts/deploy-vercel.ps1`
- `powershell -ExecutionPolicy Bypass -File scripts/deploy-vercel.ps1 -Production`

If you prefer the dashboard flow, import the GitHub repository into Vercel and keep the project at the repository root.

## Important Constraint

Vercel is not hosting the JavaFX runtime here. The desktop application should be:

- run locally with Maven
- packaged with `scripts/package-windows.ps1`
- shared as a GitHub release asset or other desktop download

## Current Status

- The repository layout is ready for Vercel.
- Live production URL: `https://adaptive-study-planner-performance.vercel.app/`
- Future CLI deploys still require a valid Vercel login or token.
