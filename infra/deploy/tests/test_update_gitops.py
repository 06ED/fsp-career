import importlib.util
from pathlib import Path
import tempfile
import unittest

import yaml


spec = importlib.util.spec_from_file_location("update_gitops", Path(__file__).parents[1] / "update_gitops.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class PromotionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name) / "repository"
        self.digests = Path(self.temporary.name) / "digests"
        self.digests.mkdir()
        self.paths = {}
        for directory, components in (("app", ("api", "web")), ("identity", ("keycloak",))):
            path = self.repository / "clusters/homelab/apps/fsp-career/prod" / directory / "kustomization.yaml"
            path.parent.mkdir(parents=True)
            path.write_text(yaml.safe_dump({
                "apiVersion": "kustomize.config.k8s.io/v1beta1",
                "kind": "Kustomization",
                "resources": ["deployment.yaml"],
                "images": [
                    {"name": f"ghcr.io/06ed/fsp-career-{component}", "newTag": "previous"}
                    for component in components
                ] + [{"name": "unrelated/image", "newTag": "keep"}],
            }))
            self.paths[directory] = path
            for component in components:
                (self.digests / f"{component}.digest").write_text("sha256:" + "a" * 64)

    def test_promotes_entire_release_and_preserves_unrelated_images(self):
        module.promote(self.repository, self.digests, "b" * 40)
        for path in self.paths.values():
            manifest = yaml.safe_load(path.read_text())
            self.assertEqual(manifest["resources"], ["deployment.yaml"])
            self.assertEqual(manifest["images"][-1], {"name": "unrelated/image", "newTag": "keep"})
            for image in manifest["images"][:-1]:
                self.assertEqual(image["newTag"], "sha-" + "b" * 40)
                self.assertEqual(image["digest"], "sha256:" + "a" * 64)
        previous = {name: path.read_text() for name, path in self.paths.items()}
        module.promote(self.repository, self.digests, "b" * 40)
        self.assertEqual(previous, {name: path.read_text() for name, path in self.paths.items()})

    def test_invalid_or_missing_digest_leaves_both_files_unchanged(self):
        previous = {name: path.read_text() for name, path in self.paths.items()}
        (self.digests / "keycloak.digest").write_text("not-a-digest")
        with self.assertRaises(ValueError):
            module.promote(self.repository, self.digests, "b" * 40)
        self.assertEqual(previous, {name: path.read_text() for name, path in self.paths.items()})
        (self.digests / "keycloak.digest").unlink()
        with self.assertRaises(FileNotFoundError):
            module.promote(self.repository, self.digests, "b" * 40)
        self.assertEqual(previous, {name: path.read_text() for name, path in self.paths.items()})

    def test_unknown_image_and_invalid_revision_fail_closed(self):
        with self.assertRaises(ValueError):
            module.promote(self.repository, self.digests, "main")
        manifest = yaml.safe_load(self.paths["identity"].read_text())
        manifest["images"][0]["name"] = "unexpected/image"
        self.paths["identity"].write_text(yaml.safe_dump(manifest))
        with self.assertRaises(ValueError):
            module.promote(self.repository, self.digests, "b" * 40)
