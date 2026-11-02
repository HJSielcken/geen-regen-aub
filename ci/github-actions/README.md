# CI workflows (waiting to be enabled)

These are the GitHub Actions workflows for the app and the relay. Claude could not push them to
`.github/workflows/` because its GitHub access lacks the `workflow` permission, which GitHub
requires for any change to workflow files.

To enable them, move both files into `.github/workflows/`, either:

- locally: `git mv ci/github-actions/android.yml ci/github-actions/relay.yml .github/workflows/`
  (create the folder first), then commit and push; or
- on github.com: open each file, copy it, and use **Add file → Create new file** with the name
  `.github/workflows/android.yml` (and `relay.yml`) on this branch.

Then delete this folder.
