# Vercel Deployment Notes

This repository contains two deliverables:

1. A JavaFX desktop application
2. A static portfolio site at the repository root

Only the static portfolio site is intended for Vercel deployment.

## Recommended Setup

1. Push the repository to GitHub.
2. Import the repository into Vercel.
3. Leave the framework preset as static or other.
4. Deploy the root directory as-is.

Files used by the portfolio site:

- `index.html`
- `site.css`
- `vercel.json`

## What To Update Before Sharing

- Replace screenshot placeholders with real images in `docs/screenshots/`
- Add your GitHub repository URL to the site if you want a public source link
- Add a downloadable build link once you package the desktop app for release

## Important Constraint

Vercel is not hosting the JavaFX runtime here. The desktop application should be:

- run locally with Maven, or
- packaged separately for download
