#!/usr/bin/env python3
"""Pin all release images in homelab to the digests produced by this CI run."""

import re
import sys
from pathlib import Path

import yaml


def promote(repository: Path, digests: Path, revision: str) -> None:
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise ValueError("Release revision must be a full Git commit SHA.")
    base = repository / "clusters/homelab/apps/fsp-career/prod"
    paths = {
        "app": ("api", "web"),
        "identity": ("keycloak",),
    }
    updates = []
    for directory, components in paths.items():
        path = base / directory / "kustomization.yaml"
        manifest = yaml.safe_load(path.read_text())
        for component in components:
            name = f"ghcr.io/06ed/fsp-career-{component}"
            matching = [image for image in manifest["images"] if image["name"] == name]
            if len(matching) != 1:
                raise ValueError(f"Expected exactly one {name} image in {path}.")
            digest = (digests / f"{component}.digest").read_text().strip()
            if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
                raise ValueError(f"Invalid registry digest for {component}.")
            matching[0]["newTag"] = f"sha-{revision}"
            matching[0]["digest"] = digest
        updates.append((path, yaml.safe_dump(manifest, sort_keys=False)))
    # Validate the complete release before changing either file.
    for path, content in updates:
        path.write_text(content)


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: update_gitops.py REPOSITORY DIGEST_DIRECTORY COMMIT_SHA")
    promote(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3])
