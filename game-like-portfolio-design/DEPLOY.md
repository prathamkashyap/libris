# Deploying your portfolio

Your whole site is one file: **`index.html`**. No build step, no npm install — that's deliberate, since it's your first portfolio and this keeps it simple to host and edit.

## Fastest option: GitHub Pages (free, matches your GitHub username)

1. Create a new repo named exactly **`prathamkashyap.github.io`** on GitHub.
2. Upload `index.html` to it (rename nothing — GitHub Pages looks for `index.html` at the root).
3. In the repo, go to **Settings → Pages**, set source to the `main` branch, root folder.
4. Wait a minute, then visit `https://prathamkashyap.github.io`.

That's it — no CLI required, you can do all of this by dragging the file into GitHub's web uploader.

## Alternative: Vercel or Netlify

Drag the `index.html` file onto vercel.com/new or app.netlify.com/drop — both give you a live URL in seconds. Good if you want a custom domain later.

## Previewing before you deploy

Just double-click `index.html` to open it in any browser. Everything — fonts, the resume download, the game — works offline except the GitHub stats panel near the bottom, which needs internet to fetch your live stats.

## How to update content later

Everything is organized in clearly commented sections inside the single file — search for these landmarks (Ctrl+F):

| Want to change... | Search for... |
|---|---|
| Hero headline | `hero__title` |
| Project cards | `PROJECTS` (the HTML comment) |
| Skills / constellation | `clusters = [` in the `<script>` |
| Timeline / education | `EXPERIENCE & EDUCATION` |
| Colors | `:root {` near the top of the `<style>` block |
| Resume file | Re-run the same base64 trick, or just swap in a normal `<a href="resume.pdf">` link and upload the PDF alongside `index.html` |

## What I deliberately left out (easy to add back)

- **Phone number** — public portfolios that list a phone number tend to get scraped and spammed, so I left it off. Your email and socials are all there. Say the word and I'll add it back.
- **Light mode** — the whole design is built around a night-sky metaphor, which doesn't really translate to a light theme, so I shipped dark-only. I can add a toggle if you want one for accessibility reasons.
- **EmailJS contact form** — your old site had one wired to a template author's account (not yours). I replaced it with a one-click "copy email" button so it works immediately with zero setup. I can wire up a real form if you make a free EmailJS account.

## If you want it as separate files instead

Everything's inlined (CSS, JS, your logo, your resume PDF) into one file on purpose, so you can preview it instantly and deploy by uploading a single file. If the project grows and you'd rather have `style.css` / `script.js` / `assets/` as separate files for easier editing, just ask — it's a quick split.
