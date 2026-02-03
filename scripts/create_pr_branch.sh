#!/usr/bin/env bash
set -euo pipefail

branch_name=$(date +%Y%m%d-%H%M%S)

git checkout -b "$branch_name"

git add -A

git commit -m "Initialize web launcher project"

git push -u origin "$branch_name"

if command -v gh >/dev/null 2>&1; then
  gh pr create --base main --head "$branch_name" --title "Web launcher initial implementation" --body "Automated PR creation."
else
  echo "gh not found; please create a PR manually for branch $branch_name"
fi
