# History-Free Public Release

The working repository is private engineering state. Its history contains an
individual author's Git identity, an old workstation path and namespace, a
unique tablet serial, and historical screenshots. It must remain private.

Do not make the working repository public, publish a fork, mirror its refs, or
use a shallow clone. A shallow clone still carries existing commit identity and
history relationships. Do not ZIP or copy the workspace either: ignored local
files include recovery data, APKs, app-store signing configuration, passwords,
and private keys.

## Export

After the private branch is clean and all release gates pass:

```sh
./tools/install-gitleaks.sh
export SARO_PRIVATE_DENYLIST="$PWD/.private/public-denylist.tsv"
./tools/export-public-snapshot.sh ../saro-public
cd ../saro-public
SARO_PRIVATE_DENYLIST="$SARO_PRIVATE_DENYLIST" ./tools/audit-public-tree.sh .
```

The installer downloads Gitleaks 8.30.1 from its official release and verifies
the archive against a pinned publisher checksum; the downloaded executable is
ignored. Release scripts reject any other scanner version, including an
explicit `SARO_GITLEAKS_BIN` override. The private denylist is an ignored
mode-`0600` TSV with `scope` and `value`
columns. Keep installation-specific names, paths, serials, and fingerprints in
that file rather than reconstructing them in tracked audit code. The exporter
first runs the complete release gate, then reads only the committed Git tree,
streams `git archive` directly into a temporary extraction directory, verifies
the extracted files, confirms that no `.git` directory exists, and atomically
publishes the result. It never retains the tar stream. Ignored and untracked
private workspace files cannot enter the snapshot. The archive pathspec also
excludes the complete private screenshot directory, and the public-tree audit
rejects any screenshot file or reference that appears by another route.

Inspect the result, then initialize a new repository in that directory:

```sh
git init -b main
git add -A
git commit -m "Initial public release"
git rev-list --count HEAD
```

Configure Git with a platform-provided privacy address or a deliberately
project-neutral identity before committing. The final command must print `1`.
Create a brand-new empty public repository, add it as the new origin, and push
only this root commit. Never add the private working repository as a remote.

Run `tools/audit-public-tree.sh` again whenever the public tree changes. Its
strict checks reject personal paths/names, unique device serials, email
addresses, common token formats, signing files, APKs, private-build
fingerprints, unexpected binaries, and all screenshots. Each permitted binary
is pinned by path, MIME type, and SHA-256. PNGs also pass strict chunk, CRC,
semantic, decompression, scanline, ordering, and trailing-data validation; a
matching manifest hash alone is not sufficient.
Every publishable file is additionally scanned by Gitleaks in both the working
tree gate and extracted snapshot audit.
