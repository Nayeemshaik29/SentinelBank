#!/usr/bin/env bash
# Runs at the end of each Claude turn (Stop hook): commit and push any pending changes.
# Never fails the turn: every path exits 0.

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}" || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

git add -A
git diff --cached --quiet && exit 0

# Safety net for a public repo: never auto-commit anything that looks like a secret.
if git diff --cached --name-only | grep -Eiq '(^|/)\.env|\.pem$|\.key$|\.p12$|id_rsa|credentials'; then
  git reset -q
  echo '{"systemMessage":"auto-commit skipped: a file that looks like a secret is pending. Review it and commit by hand."}'
  exit 0
fi

count=$(git diff --cached --name-only | wc -l | tr -d ' ')
areas=$(git diff --cached --name-only | sed 's#/.*##' | sort -u | paste -sd, -)

git commit -q -m "auto: update ${count} file(s) in ${areas}

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>" >/dev/null 2>&1 || exit 0

if ! git push -q origin HEAD >/dev/null 2>&1; then
  echo '{"systemMessage":"auto-commit: committed locally but the push failed. Run git push once from your terminal."}'
fi
exit 0
