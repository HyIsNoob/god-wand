"""Offline release checks: python -m unittest discover -s tools -p test_release.py"""
import io
import urllib.error
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import release


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.root_patch = patch.object(release, "ROOT", self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        (self.root / "gradle.properties").write_text("mod_version=1.3.5\n")
        (self.root / "CHANGELOG.md").write_text("## [1.3.5]\n\n- New fix.\n\n## [1.3.4]\n- Old fix.\n")
        self.directory = self.root / "build/release/1.3.5"
        self.directory.mkdir(parents=True)
        sums = []
        for _, loader, name in release.targets("1.3.5"):
            path = self.directory / name
            with zipfile.ZipFile(path, "w") as jar:
                if loader == "fabric":
                    jar.writestr("fabric.mod.json", json.dumps({"id": "godwand", "version": "1.3.5"}))
                else:
                    metadata = "META-INF/mods.toml" if loader == "forge" else "META-INF/neoforge.mods.toml"
                    jar.writestr(metadata, '[[mods]]\nmodId="godwand"\nversion="1.3.5"\n')
            sums.append(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + name)
        (self.directory / "SHA256SUMS.txt").write_text("\n".join(sums))

    def test_exact_tags_dependencies_and_current_changelog(self):
        _, entries = release.prepare("1.3.5", "release")
        self.assertEqual(10, len(entries))
        self.assertEqual(10, len({tuple(metadata["gameVersionNames"]) for _, _, metadata in entries}))
        for path, _, metadata in entries:
            self.assertTrue({"Client", "Server"}.issubset(metadata["gameVersionNames"]))
            self.assertIn("New fix", metadata["changelog"])
            self.assertNotIn("Old fix", metadata["changelog"])
            self.assertEqual("relations" in metadata, "-fabric-" in path.name)

    def test_changelog_uses_compact_html_with_escaped_text(self):
        (self.root / "CHANGELOG.md").write_text("## [1.3.5]\n\n### Fixed\n\n- One <tag> & fix.\n\n- Two fixes.\n")
        _, entries = release.prepare("1.3.5", "release")
        metadata = entries[0][2]
        self.assertEqual("html", metadata["changelogType"])
        self.assertIn("<li>One &lt;tag&gt; &amp; fix.</li>", metadata["changelog"])
        self.assertNotIn("<li><p>", metadata["changelog"])
        self.assertEqual(1, metadata["changelog"].count("<ul>"))

    def test_changed_jar_is_rejected(self):
        next(self.directory.glob("*.jar")).write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "Checksum mismatch"):
            release.prepare("1.3.5", "release")

    def test_multipart_keeps_exact_file_and_metadata(self):
        _, entries = release.prepare("1.3.5", "beta")
        path, _, metadata = entries[0]
        body, content_type = release.multipart(path, metadata)
        self.assertIn(path.read_bytes(), body)
        self.assertIn(json.dumps(metadata).encode(), body)
        self.assertIn(b'name="file"', body)
        self.assertIn("boundary=GodWand", content_type)

    def test_default_preview_never_calls_network(self):
        with patch("sys.argv", ["release.py"]), patch.object(release, "request", side_effect=AssertionError("network")), patch("builtins.print"):
            release.main()

    def test_api_error_explains_rejection_without_exposing_token(self):
        body = json.dumps({"errorMessage": "Missing environment; test-token"}).encode()
        error = urllib.error.HTTPError("https://minecraft.curseforge.com/api", 400, "Bad Request", {}, io.BytesIO(body))
        with patch("urllib.request.build_opener") as opener:
            opener.return_value.open.side_effect = error
            with self.assertRaisesRegex(RuntimeError, "Missing environment") as caught:
                release.request("/projects/1760928/upload-file", "test-token", b"file")
            self.assertNotIn("test-token", str(caught.exception))

    def test_upload_journal_skips_successful_files_on_second_run(self):
        versions = [{"name": n} for n in ["Fabric", "Forge", "NeoForge", "1.20.1", "1.21.1", "1.21.11", "26.1.2", "26.3", "Client", "Server"]]
        def response(route, *_):
            return versions if route == "/game/versions" else {"id": 42}
        with patch("sys.argv", ["release.py", "--upload"]), patch.object(release, "settings", return_value=("1760928", "test-token")), patch.object(release, "request", side_effect=response) as api, patch("builtins.print"):
            release.main()
            self.assertEqual(11, api.call_count)
            release.main()
            self.assertEqual(12, api.call_count)


if __name__ == "__main__":
    unittest.main()
