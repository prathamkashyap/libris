#!/usr/bin/env python3
"""Comprehensive mocked regression tests for scripts/libris-backup-offsite.py.

All network operations are mocked using unittest.mock.
No external network connections or Backblaze endpoints are called.
No credentials or private keys are used or logged.
"""

import base64
import hashlib
import importlib.util
import io
import json
import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from unittest import mock
import urllib.error
import urllib.request

# Load libris-backup-offsite.py dynamically
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SCRIPT_PATH = os.path.join(SCRIPT_DIR, "libris-backup-offsite.py")

spec = importlib.util.spec_from_file_location("libris_backup_offsite", SCRIPT_PATH)
uploader = importlib.util.module_from_spec(spec)
spec.loader.exec_module(uploader)


class MockHTTPResponse:
    """Mock for urllib.request.urlopen context manager."""
    def __init__(self, body=b"{}", code=200, headers=None):
        self._body = body if isinstance(body, bytes) else body.encode("utf-8")
        self.code = code
        self.headers = headers or {}

    def read(self, n=None):
        if n is None:
            res = self._body
            self._body = b""
            return res
        chunk = self._body[:n]
        self._body = self._body[n:]
        return chunk

    def close(self):
        pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False


class TestCredentials(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.creds_path = os.path.join(self.tmp, "b2.credentials")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_valid_credentials(self):
        with open(self.creds_path, "w") as f:
            f.write("# Backblaze credentials\n")
            f.write("keyID=synthetic_key_id_12345\n")
            f.write("applicationKey=synthetic_application_key_67890\n")
        os.chmod(self.creds_path, 0o600)

        creds = uploader.read_credentials(self.creds_path)
        self.assertEqual(creds["keyID"], "synthetic_key_id_12345")
        self.assertEqual(creds["applicationKey"], "synthetic_application_key_67890")

    def test_missing_file_raises_security_error(self):
        with self.assertRaises(uploader.B2SecurityError):
            uploader.read_credentials(os.path.join(self.tmp, "nonexistent"))

    def test_missing_required_key_raises_security_error(self):
        with open(self.creds_path, "w") as f:
            f.write("keyID=only_key_id\n")
        os.chmod(self.creds_path, 0o600)

        with self.assertRaises(uploader.B2SecurityError):
            uploader.read_credentials(self.creds_path)

    def test_permissive_permissions_rejected(self):
        with open(self.creds_path, "w") as f:
            f.write("keyID=abc\napplicationKey=def\n")
        # chmod 0644 (world readable)
        os.chmod(self.creds_path, 0o644)

        with self.assertRaises(uploader.B2SecurityError):
            uploader.read_credentials(self.creds_path)


class TestRedaction(unittest.TestCase):
    def test_redact_authorization_header(self):
        raw = "Making request with Authorization: Basic YWJjOnNlY3JldA=="
        redacted = uploader.redact(raw)
        self.assertNotIn("YWJjOnNlY3JldA==", redacted)
        self.assertIn("Authorization: [REDACTED]", redacted)

    def test_redact_direct_token(self):
        raw = "Header Authorization: 2_20261018_test_token_value_abc123"
        redacted = uploader.redact(raw)
        self.assertNotIn("2_20261018_test_token_value_abc123", redacted)
        self.assertIn("[REDACTED]", redacted)

    def test_redact_application_key(self):
        raw = "Parsed applicationKey=K00123456789abcdef"
        redacted = uploader.redact(raw)
        self.assertNotIn("K00123456789abcdef", redacted)
        self.assertIn("applicationKey=[REDACTED]", redacted)


class TestHashing(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.file_path = os.path.join(self.tmp, "data.bin")
        self.data = b"ciphertext sample payload for hashing tests"
        with open(self.file_path, "wb") as f:
            f.write(self.data)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_sha256_file(self):
        expected = hashlib.sha256(self.data).hexdigest()
        self.assertEqual(uploader.sha256_file(self.file_path), expected)

    def test_sha1_file(self):
        expected = hashlib.sha1(self.data).hexdigest()
        self.assertEqual(uploader.sha1_file(self.file_path), expected)


class TestAuthorization(unittest.TestCase):
    def _make_v4_resp(
        self,
        account_id="acc_001",
        token="account_auth_token_xyz",
        api_url="https://api001.backblazeb2.com",
        download_url="https://f001.backblazeb2.com",
        bucket_id="38fd5596187d47ffa5110c1d",
        bucket_name="libris-backups",
        name_prefix="backups/",
        capabilities=None,
    ):
        if capabilities is None:
            capabilities = ["listBuckets", "listFiles", "readFiles", "writeFiles"]
        buckets = []
        if bucket_id is not None or bucket_name is not None:
            buckets.append({"id": bucket_id, "name": bucket_name})
        return {
            "accountId": account_id,
            "authorizationToken": token,
            "apiInfo": {
                "storageApi": {
                    "apiUrl": api_url,
                    "downloadUrl": download_url,
                    "allowed": {
                        "buckets": buckets,
                        "capabilities": capabilities,
                        "namePrefix": name_prefix,
                    },
                }
            },
        }

    def test_authorize_v4_success(self):
        """1. Successful v4 authorization with the expected restricted key."""
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}
        mock_response = self._make_v4_resp()

        captured_req = {}

        def fake_urlopen(req, timeout=120):
            captured_req["url"] = req.full_url
            captured_req["method"] = req.get_method()
            captured_req["headers"] = dict(req.headers)
            return MockHTTPResponse(json.dumps(mock_response))

        with mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
            auth = uploader.authorize(creds, expected_bucket="libris-backups")

        self.assertEqual(captured_req["url"], "https://api.backblazeb2.com/b2api/v4/b2_authorize_account")
        self.assertEqual(captured_req["method"], "GET")
        expected_basic = "Basic " + base64.b64encode(b"kid123:appsec456").decode("ascii")
        self.assertEqual(captured_req["headers"]["Authorization"], expected_basic)

        self.assertEqual(auth["account_id"], "acc_001")
        self.assertEqual(auth["auth_token"], "account_auth_token_xyz")
        self.assertEqual(auth["api_url"], "https://api001.backblazeb2.com")
        self.assertEqual(auth["download_url"], "https://f001.backblazeb2.com")
        self.assertEqual(auth["bucket_id"], "38fd5596187d47ffa5110c1d")
        self.assertEqual(auth["bucket_name"], "libris-backups")
        self.assertEqual(auth["name_prefix"], "backups/")
        self.assertEqual(auth["capabilities"], ["listBuckets", "listFiles", "readFiles", "writeFiles"])

    def test_authorize_v4_extracts_nested_storage_api(self):
        """2. Correct extraction of the nested API and bucket information."""
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}
        # Include trailing slashes to verify normalization
        mock_response = self._make_v4_resp(
            api_url="https://api003.backblazeb2.com/",
            download_url="https://f003.backblazeb2.com/",
        )
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(mock_response))):
            auth = uploader.authorize(creds)

        self.assertEqual(auth["api_url"], "https://api003.backblazeb2.com")
        self.assertEqual(auth["download_url"], "https://f003.backblazeb2.com")
        self.assertEqual(auth["bucket_id"], "38fd5596187d47ffa5110c1d")
        self.assertEqual(auth["bucket_name"], "libris-backups")

    def test_authorize_v4_rejects_missing_or_malformed_fields(self):
        """3. Rejection of missing or malformed v4 fields."""
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}

        # Missing authorizationToken
        resp = self._make_v4_resp()
        del resp["authorizationToken"]
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2Error):
                uploader.authorize(creds)

        # Missing apiInfo
        resp = self._make_v4_resp()
        del resp["apiInfo"]
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2Error):
                uploader.authorize(creds)

        # Missing storageApi
        resp = self._make_v4_resp()
        del resp["apiInfo"]["storageApi"]
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2Error):
                uploader.authorize(creds)

        # Missing apiUrl
        resp = self._make_v4_resp()
        resp["apiInfo"]["storageApi"]["apiUrl"] = ""
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2Error):
                uploader.authorize(creds)

        # Missing downloadUrl
        resp = self._make_v4_resp()
        resp["apiInfo"]["storageApi"]["downloadUrl"] = ""
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2Error):
                uploader.authorize(creds)

        # Missing allowed
        resp = self._make_v4_resp()
        del resp["apiInfo"]["storageApi"]["allowed"]
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2Error):
                uploader.authorize(creds)

        # Empty buckets list
        resp = self._make_v4_resp()
        resp["apiInfo"]["storageApi"]["allowed"]["buckets"] = []
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds)

        # Multiple buckets list
        resp = self._make_v4_resp()
        resp["apiInfo"]["storageApi"]["allowed"]["buckets"] = [
            {"id": "id1", "name": "b1"},
            {"id": "id2", "name": "b2"},
        ]
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds)

        # Bucket entry missing id or name
        resp = self._make_v4_resp()
        resp["apiInfo"]["storageApi"]["allowed"]["buckets"] = [{"name": "libris-backups"}]
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds)

    def test_authorize_v4_rejects_bucket_mismatch(self):
        """4. Rejection of a bucket ID or bucket name mismatch."""
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}

        # Bucket name mismatch
        resp = self._make_v4_resp(bucket_name="other-bucket")
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_bucket="libris-backups")

        # Bucket ID mismatch
        resp = self._make_v4_resp(bucket_id="other_bucket_id_999")
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_bucket_id="38fd5596187d47ffa5110c1d")

    def test_authorize_v4_rejects_prefix_mismatch(self):
        """5. Rejection of a missing or incorrect filename prefix."""
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}

        # None prefix
        resp = self._make_v4_resp(name_prefix=None)
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_prefix="backups/")

        # Wrong prefix
        resp = self._make_v4_resp(name_prefix="other-prefix/")
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_prefix="backups/")

        # Empty prefix
        resp = self._make_v4_resp(name_prefix="")
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_prefix="backups/")

    def test_authorize_v4_rejects_capability_mismatches(self):
        """6. Rejection of extra, missing, or unexpected capabilities."""
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}

        # Missing required capability
        resp = self._make_v4_resp(capabilities=["readFiles", "writeFiles"])
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds)

        # Extra unexpected capability
        resp = self._make_v4_resp(capabilities=["listBuckets", "listFiles", "readFiles", "writeFiles", "shareFiles"])
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds)

        # Prohibited deleteFiles
        resp = self._make_v4_resp(capabilities=["listBuckets", "listFiles", "readFiles", "writeFiles", "deleteFiles"])
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds)

        # Prohibited writeKeys
        resp = self._make_v4_resp(capabilities=["listBuckets", "listFiles", "readFiles", "writeFiles", "writeKeys"])
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(resp))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds)

    @mock.patch("time.sleep", return_value=None)
    def test_authorize_v4_handles_http_errors_without_leaking_secrets(self, mock_sleep):
        """7. Correct handling of HTTP errors and timeouts without leaking secrets."""
        creds = {"keyID": "kid123", "applicationKey": "super_secret_key_value_999"}

        # HTTP error with secret in body
        secret_body = b'{"code": "unauthorized", "message": "Failed key super_secret_key_value_999"}'
        def fake_error(req, timeout=120):
            raise urllib.error.HTTPError(req.full_url, 401, "Unauthorized", {}, io.BytesIO(secret_body))

        with mock.patch("urllib.request.urlopen", side_effect=fake_error):
            with self.assertRaises(uploader.B2Error) as ctx:
                uploader.authorize(creds)
        self.assertNotIn("super_secret_key_value_999", str(ctx.exception))
        self.assertIn("401", str(ctx.exception))

        # Timeout / URLError
        def fake_timeout(req, timeout=120):
            raise urllib.error.URLError("Connection timed out to api.backblazeb2.com")

        with mock.patch("urllib.request.urlopen", side_effect=fake_timeout):
            with self.assertRaises(uploader.B2Error) as ctx:
                uploader.authorize(creds)
        self.assertNotIn("super_secret_key_value_999", str(ctx.exception))

    def test_regression_v2_response_format_incompatibility(self):
        """9. Regression coverage for the previous v2 response-format incompatibility."""
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}

        # Legacy v2 response without apiInfo
        legacy_v2_resp = {
            "accountId": "acc_001",
            "authorizationToken": "account_auth_token_xyz",
            "apiUrl": "https://api001.backblazeb2.com",
            "downloadUrl": "https://f001.backblazeb2.com",
            "allowed": {
                "bucketId": "38fd5596187d47ffa5110c1d",
                "bucketName": "libris-backups",
                "capabilities": ["listBuckets", "listFiles", "readFiles", "writeFiles"],
            },
        }

        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(legacy_v2_resp))):
            with self.assertRaises(uploader.B2Error) as ctx:
                uploader.authorize(creds)
            self.assertIn("missing apiInfo object", str(ctx.exception))

        # Backblaze B2 HTTP 400 rejection on v2 endpoint
        v2_rejection_body = (
            b'{"code": "bad_request", '
            b'"message": "This request is not currently supported on API version number 2", '
            b'"status": 400}'
        )
        def fake_v2_rejected(req, timeout=120):
            raise urllib.error.HTTPError(req.full_url, 400, "Bad Request", {}, io.BytesIO(v2_rejection_body))

        with mock.patch("urllib.request.urlopen", side_effect=fake_v2_rejected):
            with self.assertRaises(uploader.B2Error) as ctx:
                uploader.authorize(creds)
            self.assertIn("400", str(ctx.exception))
            self.assertIn("not currently supported", str(ctx.exception))


class TestUploadUrlAndUpload(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.dump_path = os.path.join(self.tmp, "ciphertext.bin")
        self.payload = b"encrypted backup ciphertext block"
        with open(self.dump_path, "wb") as f:
            f.write(self.payload)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_get_upload_url_passes_direct_token(self):
        """8. Correct upload URL retrieval using mocked HTTP responses."""
        auth = {
            "api_url": "https://api001.backblazeb2.com",
            "auth_token": "token_without_bearer_prefix_123",
            "bucket_id": "bucket_abc_99",
        }
        mock_resp = {
            "bucketId": "bucket_abc_99",
            "uploadUrl": "https://upload001.backblazeb2.com/b2_upload_file/123",
            "authorizationToken": "upload_specific_token_456",
        }

        captured_req = {}

        def fake_urlopen(req, timeout=120):
            captured_req["url"] = req.full_url
            captured_req["method"] = req.get_method()
            captured_req["headers"] = dict(req.headers)
            captured_req["data"] = json.loads(req.data.decode("utf-8"))
            return MockHTTPResponse(json.dumps(mock_resp))

        with mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
            url, token = uploader.get_upload_url(auth)

        self.assertEqual(url, "https://upload001.backblazeb2.com/b2_upload_file/123")
        self.assertEqual(token, "upload_specific_token_456")
        self.assertEqual(captured_req["url"], "https://api001.backblazeb2.com/b2api/v4/b2_get_upload_url")
        self.assertEqual(captured_req["method"], "POST")
        # Assert auth header is direct token, not Bearer
        self.assertEqual(captured_req["headers"]["Authorization"], "token_without_bearer_prefix_123")
        self.assertEqual(captured_req["data"]["bucketId"], "bucket_abc_99")

    def test_upload_headers_and_body(self):
        upload_url = "https://upload001.backblazeb2.com/b2_upload_file/123"
        upload_token = "upload_tok_789"
        remote_name = "backups/libris-20261018.sql.gz.gpg"

        captured_req = {}

        def fake_urlopen(req, timeout=300):
            captured_req["url"] = req.full_url
            captured_req["method"] = req.get_method()
            captured_req["headers"] = dict(req.headers)
            captured_req["data"] = req.data
            resp = {
                "fileId": "file_id_001",
                "fileName": remote_name,
                "contentLength": len(self.payload),
            }
            return MockHTTPResponse(json.dumps(resp))

        with mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
            resp = uploader.upload(upload_url, upload_token, self.dump_path, remote_name)

        self.assertEqual(resp["fileId"], "file_id_001")
        self.assertEqual(captured_req["method"], "POST")
        self.assertEqual(captured_req["headers"]["Authorization"], "upload_tok_789")
        self.assertEqual(captured_req["headers"]["X-bz-file-name"], "backups/libris-20261018.sql.gz.gpg")
        self.assertEqual(captured_req["headers"]["Content-type"], "b2/x-auto")
        self.assertEqual(captured_req["headers"]["Content-length"], str(len(self.payload)))
        self.assertEqual(captured_req["headers"]["X-bz-content-sha1"], hashlib.sha1(self.payload).hexdigest())
        self.assertEqual(captured_req["data"], self.payload)


class TestDownloadAndVerification(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.dest_path = os.path.join(self.tmp, "downloaded.bin")
        self.content = b"exact ciphertext content returned from b2"

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_download_by_name_flow(self):
        auth = {
            "download_url": "https://f001.backblazeb2.com",
            "bucket_name": "libris-backups",
            "auth_token": "account_auth_token_download",
        }
        remote_name = "backups/libris-20261018T053204Z.sql.gz.gpg"

        captured_req = {}

        def fake_urlopen(req, timeout=300):
            captured_req["url"] = req.full_url
            captured_req["method"] = req.get_method()
            captured_req["headers"] = dict(req.headers)
            return MockHTTPResponse(self.content)

        with mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
            remote_sha = uploader.download(auth, remote_name, self.dest_path)

        expected_url = "https://f001.backblazeb2.com/file/libris-backups/backups/libris-20261018T053204Z.sql.gz.gpg"
        self.assertEqual(captured_req["url"], expected_url)
        self.assertEqual(captured_req["method"], "GET")
        self.assertEqual(captured_req["headers"]["Authorization"], "account_auth_token_download")

        # Verify output file
        with open(self.dest_path, "rb") as f:
            written_bytes = f.read()
        self.assertEqual(written_bytes, self.content)
        self.assertEqual(remote_sha, hashlib.sha256(self.content).hexdigest())

        # Verify permissions: 0600
        mode = stat.S_IMODE(os.stat(self.dest_path).st_mode)
        self.assertEqual(mode, 0o600)


class TestRetriesAndErrors(unittest.TestCase):
    @mock.patch("time.sleep", return_value=None)
    def test_retry_on_503_then_succeed(self, mock_sleep):
        calls = {"count": 0}

        def fake_urlopen(req, timeout=120):
            calls["count"] += 1
            if calls["count"] < 3:
                raise urllib.error.HTTPError(req.full_url, 503, "Service Unavailable", {}, io.BytesIO(b"busy"))
            return MockHTTPResponse(b'{"status":"ok"}')

        with mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
            code, _, body = uploader.b2_request("https://api.example.com/test", method="GET")

        self.assertEqual(calls["count"], 3)
        self.assertEqual(body, b'{"status":"ok"}')
        self.assertEqual(mock_sleep.call_count, 2)

    def test_no_retry_on_401_unauthorized(self):
        calls = {"count": 0}

        def fake_urlopen(req, timeout=120):
            calls["count"] += 1
            raise urllib.error.HTTPError(req.full_url, 401, "Unauthorized", {}, io.BytesIO(b"bad auth"))

        with mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
            with self.assertRaises(uploader.B2Error) as ctx:
                uploader.b2_request("https://api.example.com/test", method="GET")

        self.assertEqual(calls["count"], 1)
        self.assertIn("401", str(ctx.exception))

    def test_no_retry_on_403_forbidden(self):
        calls = {"count": 0}

        def fake_urlopen(req, timeout=120):
            calls["count"] += 1
            raise urllib.error.HTTPError(req.full_url, 403, "Forbidden", {}, io.BytesIO(b"forbidden"))

        with mock.patch("urllib.request.urlopen", side_effect=fake_urlopen):
            with self.assertRaises(uploader.B2Error):
                uploader.b2_request("https://api.example.com/test", method="GET")

        self.assertEqual(calls["count"], 1)


class TestStatusRecording(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.status_file = os.path.join(self.tmp, "last-run.status")
        self.failure_file = os.path.join(self.tmp, "LAST_OFFSITE_FAILURE")

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_record_success_clears_failure(self):
        # Create an existing failure file
        with open(self.failure_file, "w") as f:
            f.write("previous failure")

        # Initial status
        with open(self.status_file, "w") as f:
            json.dump({"dump": "libris-123.sql.gz", "status": "ok"}, f)

        offsite_status = {
            "status": "ok",
            "bucket": "libris-backups",
            "object": "backups/libris-123.sql.gz.gpg",
            "sha256": "abcdef0123456789",
            "bytes": 5000,
        }

        uploader.update_status(self.status_file, offsite_status, self.failure_file)

        with open(self.status_file, "r") as f:
            data = json.load(f)

        self.assertEqual(data["dump"], "libris-123.sql.gz")
        self.assertEqual(data["offsite"]["status"], "ok")
        self.assertEqual(data["offsite"]["object"], "backups/libris-123.sql.gz.gpg")
        self.assertFalse(os.path.exists(self.failure_file))

    def test_record_failure_creates_failure_file(self):
        offsite_status = {
            "status": "failed",
            "error": "Upload timed out after retries",
            "attempted_utc": "2026-10-18T12:00:00Z",
        }

        uploader.update_status(self.status_file, offsite_status, self.failure_file)

        with open(self.status_file, "r") as f:
            data = json.load(f)
        self.assertEqual(data["offsite"]["status"], "failed")

        with open(self.failure_file, "r") as f:
            fail_data = json.load(f)
        self.assertEqual(fail_data["error"], "Upload timed out after retries")


class TestEndToEndMockedRun(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.backup_dir = os.path.join(self.tmp, "backups")
        os.makedirs(self.backup_dir)

        self.dump_file = os.path.join(self.backup_dir, "libris-20261018T100000Z.sql.gz")
        with open(self.dump_file, "wb") as f:
            f.write(b"plaintext mysql dump contents")

        self.creds_file = os.path.join(self.tmp, "b2.credentials")
        with open(self.creds_file, "w") as f:
            f.write("keyID=synthetic_key\napplicationKey=synthetic_secret\n")
        os.chmod(self.creds_file, 0o600)

        self.status_file = os.path.join(self.backup_dir, "last-run.status")
        with open(self.status_file, "w") as f:
            json.dump({"status": "ok", "dump": os.path.basename(self.dump_file)}, f)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def test_successful_run(self):
        args = mock.Mock()
        args.backup_dir = self.backup_dir
        args.credentials = self.creds_file
        args.bucket = "libris-backups"
        args.prefix = "backups/"
        args.recipient = "libris-backup@local"
        args.gpg_home = None
        args.status_file = self.status_file
        args.failure_file = os.path.join(self.backup_dir, "LAST_OFFSITE_FAILURE")
        args.dump_file = self.dump_file

        mock_auth = {
            "account_id": "acc_1",
            "auth_token": "auth_tok_1",
            "api_url": "https://api001.backblazeb2.com",
            "download_url": "https://f001.backblazeb2.com",
            "bucket_id": "b_1",
            "bucket_name": "libris-backups",
            "name_prefix": "backups/",
            "capabilities": ["readFiles", "writeFiles"],
        }

        # Mock encrypt_file to create the encrypted file
        def fake_encrypt(inp, out, rec, home):
            with open(out, "wb") as f:
                f.write(b"mocked encrypted ciphertext")
            os.chmod(out, 0o600)

        with mock.patch.object(uploader, "encrypt_file", side_effect=fake_encrypt), \
             mock.patch.object(uploader, "authorize", return_value=mock_auth), \
             mock.patch.object(uploader, "get_upload_url", return_value=("https://up.com/1", "up_tok")), \
             mock.patch.object(uploader, "upload", return_value={"fileId": "fid_99"}), \
             mock.patch.object(uploader, "download", return_value=hashlib.sha256(b"mocked encrypted ciphertext").hexdigest()):
            rc = uploader.run_offsite_backup(args)

        self.assertEqual(rc, 0)
        # Verify plaintext dump was NEVER deleted
        self.assertTrue(os.path.exists(self.dump_file))
        # Verify encrypted file was cleaned up
        self.assertFalse(os.path.exists(self.dump_file + ".gpg"))

        with open(self.status_file, "r") as f:
            status = json.load(f)
        self.assertEqual(status["offsite"]["status"], "ok")
        self.assertEqual(status["offsite"]["file_id"], "fid_99")

    def test_checksum_mismatch_failure(self):
        args = mock.Mock()
        args.backup_dir = self.backup_dir
        args.credentials = self.creds_file
        args.bucket = "libris-backups"
        args.prefix = "backups/"
        args.recipient = "libris-backup@local"
        args.gpg_home = None
        args.status_file = self.status_file
        args.failure_file = os.path.join(self.backup_dir, "LAST_OFFSITE_FAILURE")
        args.dump_file = self.dump_file

        mock_auth = {
            "account_id": "acc_1",
            "auth_token": "auth_tok_1",
            "api_url": "https://api001.backblazeb2.com",
            "download_url": "https://f001.backblazeb2.com",
            "bucket_id": "b_1",
            "bucket_name": "libris-backups",
            "name_prefix": "backups/",
            "capabilities": ["readFiles", "writeFiles"],
        }

        def fake_encrypt(inp, out, rec, home):
            with open(out, "wb") as f:
                f.write(b"mocked encrypted ciphertext")
            os.chmod(out, 0o600)

        # download returns mismatching SHA-256
        with mock.patch.object(uploader, "encrypt_file", side_effect=fake_encrypt), \
             mock.patch.object(uploader, "authorize", return_value=mock_auth), \
             mock.patch.object(uploader, "get_upload_url", return_value=("https://up.com/1", "up_tok")), \
             mock.patch.object(uploader, "upload", return_value={"fileId": "fid_99"}), \
             mock.patch.object(uploader, "download", return_value="mismatching_sha256_hash_here"):
            rc = uploader.run_offsite_backup(args)

        self.assertEqual(rc, 1)
        # Plaintext dump preserved
        self.assertTrue(os.path.exists(self.dump_file))
        # Encrypted file cleaned up
        self.assertFalse(os.path.exists(self.dump_file + ".gpg"))

        with open(self.status_file, "r") as f:
            status = json.load(f)
        self.assertEqual(status["offsite"]["status"], "failed")
        self.assertIn("Integrity check failed", status["offsite"]["error"])

    @mock.patch("time.sleep", return_value=None)
    def test_upload_retry_fetches_fresh_upload_url(self, mock_sleep):
        args = mock.Mock()
        args.backup_dir = self.backup_dir
        args.credentials = self.creds_file
        args.bucket = "libris-backups"
        args.prefix = "backups/"
        args.recipient = "libris-backup@local"
        args.gpg_home = None
        args.status_file = self.status_file
        args.failure_file = os.path.join(self.backup_dir, "LAST_OFFSITE_FAILURE")
        args.dump_file = self.dump_file

        mock_auth = {
            "account_id": "acc_1",
            "auth_token": "auth_tok_1",
            "api_url": "https://api001.backblazeb2.com",
            "download_url": "https://f001.backblazeb2.com",
            "bucket_id": "b_1",
            "bucket_name": "libris-backups",
            "name_prefix": "backups/",
            "capabilities": ["readFiles", "writeFiles"],
        }

        def fake_encrypt(inp, out, rec, home):
            with open(out, "wb") as f:
                f.write(b"mocked encrypted ciphertext")
            os.chmod(out, 0o600)

        upload_calls = {"count": 0}

        def fake_upload(url, tok, path, remote):
            upload_calls["count"] += 1
            if upload_calls["count"] == 1:
                raise uploader.B2Error("503 Service Unavailable")
            return {"fileId": "fid_retry_success"}

        mock_get_url = mock.Mock(side_effect=[("https://up.com/1", "tok_1"), ("https://up.com/2", "tok_2")])

        with mock.patch.object(uploader, "encrypt_file", side_effect=fake_encrypt), \
             mock.patch.object(uploader, "authorize", return_value=mock_auth), \
             mock.patch.object(uploader, "get_upload_url", mock_get_url), \
             mock.patch.object(uploader, "upload", side_effect=fake_upload), \
             mock.patch.object(uploader, "download", return_value=hashlib.sha256(b"mocked encrypted ciphertext").hexdigest()):
            rc = uploader.run_offsite_backup(args)

        self.assertEqual(rc, 0)
        self.assertEqual(mock_get_url.call_count, 2)
        self.assertEqual(upload_calls["count"], 2)
        self.assertEqual(mock_sleep.call_count, 1)

        with open(self.status_file, "r") as f:
            status = json.load(f)
        self.assertEqual(status["offsite"]["status"], "ok")
        self.assertEqual(status["offsite"]["file_id"], "fid_retry_success")

    def test_canary_prefix_authorizes_against_backups_scope(self):
        args = mock.Mock()
        args.backup_dir = self.backup_dir
        args.credentials = self.creds_file
        args.bucket = "libris-backups"
        args.prefix = "backups/canary/"
        args.bucket_id = "38fd5596187d47ffa5110c1d"
        args.recipient = "libris-backup@local"
        args.gpg_home = None
        args.status_file = self.status_file
        args.failure_file = os.path.join(self.backup_dir, "LAST_OFFSITE_FAILURE")
        args.dump_file = self.dump_file

        mock_auth = {
            "account_id": "acc_1",
            "auth_token": "auth_tok_1",
            "api_url": "https://api001.backblazeb2.com",
            "download_url": "https://f001.backblazeb2.com",
            "bucket_id": "38fd5596187d47ffa5110c1d",
            "bucket_name": "libris-backups",
            "name_prefix": "backups/",
            "capabilities": ["listBuckets", "listFiles", "readFiles", "writeFiles"],
        }

        def fake_encrypt(inp, out, rec, home):
            with open(out, "wb") as f:
                f.write(b"mocked encrypted ciphertext")
            os.chmod(out, 0o600)

        captured = {}

        def fake_authorize(creds, expected_bucket=None, expected_bucket_id=None, expected_prefix=None):
            captured["expected_prefix"] = expected_prefix
            captured["expected_bucket"] = expected_bucket
            captured["expected_bucket_id"] = expected_bucket_id
            return mock_auth

        with mock.patch.object(uploader, "encrypt_file", side_effect=fake_encrypt), \
             mock.patch.object(uploader, "authorize", side_effect=fake_authorize), \
             mock.patch.object(uploader, "get_upload_url", return_value=("https://up.com/1", "up_tok")), \
             mock.patch.object(uploader, "upload", return_value={"fileId": "fid_canary"}), \
             mock.patch.object(uploader, "download", return_value=hashlib.sha256(b"mocked encrypted ciphertext").hexdigest()):
            rc = uploader.run_offsite_backup(args)

        self.assertEqual(rc, 0)
        self.assertEqual(captured["expected_prefix"], "backups/")
        self.assertEqual(captured["expected_bucket"], "libris-backups")
        self.assertEqual(captured["expected_bucket_id"], "38fd5596187d47ffa5110c1d")
        with open(self.status_file, "r") as f:
            status = json.load(f)
        self.assertEqual(status["offsite"]["object"], "backups/canary/libris-20261018T100000Z.sql.gz.gpg")


EXPECTED_FPR = "64A47F2F83952FF9EE23884407C9D6143095EBD7"
ENCRYPT_SUBKEY_FPR = "1234567890ABCDEFFEDCBA0987654321ABCDEF01"
ENCRYPT_SUBKEY_ID = ENCRYPT_SUBKEY_FPR[-16:].upper()
OTHER_KEY_ID = "DEADBEEFDEADBEEF"

COLON_LISTING = """tru::1:1:0:3:1:5
pub:-:4096:1:07C9D6143095EBD7:1000:::-:::scESC::::::
fpr:::::::::64A47F2F83952FF9EE23884407C9D6143095EBD7:
uid:-::::1000::ABCDEF::Libris Backup <libris-backup@local>::::::::::0:
sub:-:4096:1:%s:1000::::::e::::::
fpr:::::::::%s:
""" % (ENCRYPT_SUBKEY_ID, ENCRYPT_SUBKEY_FPR)

VALID_PACKETS = """# off=0 ctb=85 tag=1 hlen=3 plen=268
:pubkey enc packet: version 3, algo 1, keyid %s
	data: [4096 bits]
# off=271 ctb=d2 tag=18 hlen=2 plen=0 new-ctb
:encrypted data packet:
	length: unknown
	mdc_method: 2
""" % ENCRYPT_SUBKEY_ID

VALID_PACKETS_OCB = """# off=0 ctb=85 tag=1 hlen=3 plen=268
:pubkey enc packet: version 3, algo 1, keyid %s
	data: [4096 bits]
# off=271 ctb=d4 tag=20 hlen=2 plen=86 new-ctb
:ocb encrypted packet: cipher=9 aead=2 cb=16
	length: 86
""" % ENCRYPT_SUBKEY_ID

NO_SECRET_STDERR = """gpg: encrypted with rsa4096 key, ID %s, created 2026-01-01
      "Libris Backup <libris-backup@local>"
gpg: public key decryption failed: No secret key
gpg: decryption failed: No secret key
""" % ENCRYPT_SUBKEY_ID


class TestRequestedPrefixScope(unittest.TestCase):
    def test_accepts_canary_subprefix_of_authorized_backups(self):
        uploader.assert_requested_prefix_in_scope("backups/canary/", "backups/")
        self.assertTrue(uploader.requested_prefix_in_scope("backups/canary/", "backups/"))
        self.assertTrue(uploader.requested_prefix_in_scope("backups/", "backups/"))

    def test_rejects_prefix_outside_backups(self):
        with self.assertRaises(uploader.B2SecurityError):
            uploader.assert_requested_prefix_in_scope("other/", "backups/")
        with self.assertRaises(uploader.B2SecurityError):
            uploader.assert_requested_prefix_in_scope("backups-other/", "backups/")
        with self.assertRaises(uploader.B2SecurityError):
            uploader.assert_requested_prefix_in_scope("prod/", "backups/")

    def test_rejects_missing_or_unexpected_authorized_prefix(self):
        with self.assertRaises(uploader.B2SecurityError):
            uploader.assert_requested_prefix_in_scope("backups/canary/", None)
        with self.assertRaises(uploader.B2SecurityError):
            uploader.assert_requested_prefix_in_scope("backups/canary/", "")
        with self.assertRaises(uploader.B2SecurityError):
            uploader.assert_requested_prefix_in_scope("backups/canary/", "files/")
        with self.assertRaises(uploader.B2SecurityError):
            uploader.assert_requested_prefix_in_scope("backups/canary/", "backups")

    def test_rejects_empty_or_malformed_requested_prefix(self):
        malformed = [
            "",
            None,
            "backups/canary",
            "/backups/canary/",
            "backups/../etc/",
            "backups/./canary/",
            "backups//canary/",
            "../backups/canary/",
            "backups\\canary\\",
            "backups/canary/../../x/",
        ]
        for prefix in malformed:
            with self.subTest(prefix=prefix):
                with self.assertRaises(uploader.B2SecurityError):
                    uploader.assert_requested_prefix_in_scope(prefix, "backups/")

    def test_authorize_still_rejects_bucket_and_capability_mismatch(self):
        creds = {"keyID": "kid123", "applicationKey": "appsec456"}

        def v4(bucket_id="38fd5596187d47ffa5110c1d", bucket_name="libris-backups",
               name_prefix="backups/", capabilities=None):
            if capabilities is None:
                capabilities = ["listBuckets", "listFiles", "readFiles", "writeFiles"]
            return {
                "accountId": "acc_001",
                "authorizationToken": "account_auth_token_xyz",
                "apiInfo": {
                    "storageApi": {
                        "apiUrl": "https://api001.backblazeb2.com",
                        "downloadUrl": "https://f001.backblazeb2.com",
                        "allowed": {
                            "buckets": [{"id": bucket_id, "name": bucket_name}],
                            "capabilities": capabilities,
                            "namePrefix": name_prefix,
                        },
                    }
                },
            }

        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(v4(bucket_name="wrong")))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_prefix="backups/")

        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(v4(bucket_id="wrongidwrongidwrongid")))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_prefix="backups/")

        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(v4(name_prefix="files/")))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_prefix="backups/")

        extra_caps = ["listBuckets", "listFiles", "readFiles", "writeFiles", "deleteFiles"]
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(v4(capabilities=extra_caps)))):
            with self.assertRaises(uploader.B2SecurityError):
                uploader.authorize(creds, expected_prefix="backups/")

        # Key remains authorized for backups/ even when the uploader later requests a sub-prefix.
        with mock.patch("urllib.request.urlopen", return_value=MockHTTPResponse(json.dumps(v4()))):
            auth = uploader.authorize(creds, expected_prefix="backups/")
        self.assertEqual(auth["name_prefix"], "backups/")
        uploader.assert_requested_prefix_in_scope("backups/canary/", auth["name_prefix"])


class TestGpgPacketValidation(unittest.TestCase):
    def setUp(self):
        self.allowed = uploader.encryption_keyids_for_fingerprint(COLON_LISTING, EXPECTED_FPR)
        self.assertEqual(self.allowed, {ENCRYPT_SUBKEY_ID})

    def test_valid_packet_exit_zero(self):
        uploader.evaluate_list_packets_result(0, VALID_PACKETS, "", self.allowed)
        uploader.evaluate_list_packets_result(0, VALID_PACKETS_OCB, "", self.allowed)

    def test_valid_packet_nonzero_missing_secret_key(self):
        uploader.evaluate_list_packets_result(2, VALID_PACKETS, NO_SECRET_STDERR, self.allowed)
        uploader.evaluate_list_packets_result(2, VALID_PACKETS_OCB, NO_SECRET_STDERR, self.allowed)

    def test_packet_for_different_recipient(self):
        other = VALID_PACKETS.replace(ENCRYPT_SUBKEY_ID, OTHER_KEY_ID)
        with self.assertRaises(uploader.B2Error) as ctx:
            uploader.evaluate_list_packets_result(0, other, "", self.allowed)
        self.assertIn("recipient", str(ctx.exception).lower())

    def test_missing_malformed_or_empty_packet_output(self):
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(0, "", "", self.allowed)
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(0, ":literal data packet:\n", "", self.allowed)
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(2, ":symkey enc packet: version 4\n", "No secret key", self.allowed)
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(
                0,
                ":pubkey enc packet: version 3, algo 1, keyid %s\n" % ENCRYPT_SUBKEY_ID,
                "",
                self.allowed,
            )

    def test_nonzero_unrelated_error(self):
        with self.assertRaises(uploader.B2Error) as ctx:
            uploader.evaluate_list_packets_result(
                2, VALID_PACKETS, "gpg: no valid OpenPGP data found\n", self.allowed
            )
        self.assertIn("unrelated", str(ctx.exception).lower())
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(1, VALID_PACKETS, "gpg: CRC error\n", self.allowed)
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(2, VALID_PACKETS, "gpg: fatal error: out of memory\n", self.allowed)
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(2, VALID_PACKETS, "gpg: unexpected error during probe\n", self.allowed)

    def test_misleading_output_mentions_keyid_without_packet(self):
        misleading = (
            "# this dump mentions keyid %s and fingerprint %s\n"
            "gpg: encrypted with rsa4096 key, ID %s\n"
            ":literal data packet:\n"
            "\tmode b (62)\n"
        ) % (ENCRYPT_SUBKEY_ID, EXPECTED_FPR, ENCRYPT_SUBKEY_ID)
        with self.assertRaises(uploader.B2Error):
            uploader.evaluate_list_packets_result(2, misleading, "No secret key", self.allowed)

    def test_encrypt_file_accepts_missing_secret_key_exit(self):
        tmp = tempfile.mkdtemp()
        self.addCleanup(lambda: shutil.rmtree(tmp, ignore_errors=True))
        src = os.path.join(tmp, "dump.sql.gz")
        dst = os.path.join(tmp, "dump.sql.gz.gpg")
        with open(src, "wb") as fh:
            fh.write(b"plaintext")

        def fake_run(cmd, check=False, env=None, capture_output=True, timeout=None):
            cmd = list(cmd)
            if "--encrypt" in cmd:
                with open(dst, "wb") as fh:
                    fh.write(b"ciphertext")
                return subprocess.CompletedProcess(cmd, 0, b"", b"")
            if "--list-keys" in cmd:
                self.assertIn(EXPECTED_FPR, cmd)
                return subprocess.CompletedProcess(cmd, 0, COLON_LISTING.encode(), b"")
            if "--list-packets" in cmd:
                return subprocess.CompletedProcess(
                    cmd, 2, VALID_PACKETS.encode(), NO_SECRET_STDERR.encode()
                )
            raise AssertionError("unexpected command %s" % cmd)

        with mock.patch("subprocess.run", side_effect=fake_run):
            uploader.encrypt_file(src, dst, "libris-backup@local")
        self.assertEqual(stat.S_IMODE(os.stat(dst).st_mode), 0o600)

    def test_encrypt_file_rejects_wrong_recipient_and_unrelated_errors(self):
        tmp = tempfile.mkdtemp()
        self.addCleanup(lambda: shutil.rmtree(tmp, ignore_errors=True))
        src = os.path.join(tmp, "dump.sql.gz")
        dst = os.path.join(tmp, "dump.sql.gz.gpg")
        with open(src, "wb") as fh:
            fh.write(b"plaintext")

        wrong_packets = VALID_PACKETS.replace(ENCRYPT_SUBKEY_ID, OTHER_KEY_ID)

        def fake_run_wrong(cmd, check=False, env=None, capture_output=True, timeout=None):
            cmd = list(cmd)
            if "--encrypt" in cmd:
                with open(dst, "wb") as fh:
                    fh.write(b"ciphertext")
                return subprocess.CompletedProcess(cmd, 0, b"", b"")
            if "--list-keys" in cmd:
                return subprocess.CompletedProcess(cmd, 0, COLON_LISTING.encode(), b"")
            if "--list-packets" in cmd:
                return subprocess.CompletedProcess(cmd, 0, wrong_packets.encode(), b"")
            raise AssertionError("unexpected command %s" % cmd)

        with mock.patch("subprocess.run", side_effect=fake_run_wrong):
            with self.assertRaises(uploader.B2Error):
                uploader.encrypt_file(src, dst, "libris-backup@local")

        def fake_run_timeout(cmd, check=False, env=None, capture_output=True, timeout=None):
            cmd = list(cmd)
            if "--encrypt" in cmd:
                with open(dst, "wb") as fh:
                    fh.write(b"ciphertext")
                return subprocess.CompletedProcess(cmd, 0, b"", b"")
            if "--list-keys" in cmd:
                return subprocess.CompletedProcess(cmd, 0, COLON_LISTING.encode(), b"")
            raise subprocess.TimeoutExpired(cmd, timeout)

        with mock.patch("subprocess.run", side_effect=fake_run_timeout):
            with self.assertRaises(uploader.B2Error) as ctx:
                uploader.encrypt_file(src, dst, "libris-backup@local")
            self.assertIn("timed out", str(ctx.exception).lower())


if __name__ == "__main__":
    unittest.main()
