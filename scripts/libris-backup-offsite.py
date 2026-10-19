#!/usr/bin/env python3
"""Encrypt a Libris backup and upload it to Backblaze B2 using the native API.

Standard library only: no external dependencies required.
Follows the official Backblaze B2 Native API v2 specification.

Credentials and authorization tokens are read from secure storage and are never
logged, printed, or passed in command-line arguments.
Ciphertext is verified by downloading the uploaded object and comparing its
SHA-256 hash against the local ciphertext before declaring success.
Plaintext dumps are never uploaded and never deleted.
"""

import argparse
import base64
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_CREDS = "/etc/libris-backup/b2.credentials"
DEFAULT_PREFIX = "backups/"
AUTHORIZED_NAME_PREFIX = "backups/"
DEFAULT_BUCKET = "libris-backups"
DEFAULT_BUCKET_ID = "38fd5596187d47ffa5110c1d"
DEFAULT_BACKUP_DIR = "/var/lib/libris-backups"
DEFAULT_STATUS_FILE = "/var/lib/libris-backups/last-run.status"
DEFAULT_FAILURE_FILE = "/var/lib/libris-backups/LAST_OFFSITE_FAILURE"
B2_AUTH_URL = "https://api.backblazeb2.com/b2api/v4/b2_authorize_account"
# Primary fingerprint of the offsite recipient. The backup host holds only the
# public key; ciphertext is addressed to an encryption-capable subkey of this key.
EXPECTED_RECIPIENT_FINGERPRINT = "64A47F2F83952FF9EE23884407C9D6143095EBD7"

REQUIRED_CAPABILITIES = frozenset({"listBuckets", "listFiles", "readFiles", "writeFiles"})
FORBIDDEN_CAPABILITIES = (
    "deleteFiles", "deleteBuckets", "writeKeys", "deleteKeys", "writeBuckets", "shareFiles",
    "writeBucketEncryption", "writeBucketLifecycleRules", "writeBucketLogging",
    "writeBucketNotifications", "writeBucketReplications"
)

MAX_RETRIES = 3
RETRY_BASE = 2  # seconds
GPG_TIMEOUT_SECONDS = 60
GPG_PACKET_TIMEOUT_SECONDS = 30

# OpenPGP packet inspection. `gpg --list-packets` prints structural records on
# stdout. When the secret key is absent it still emits a valid `:pubkey enc
# packet:` (and encrypted-data / AEAD payload packet) then exits 2 with
# "No secret key" on stderr while attempting decryption. Exit 0 occurs when a
# secret key is present and GPG continues into the decrypted payload.
_PUBKEY_ENC_PACKET_RE = re.compile(
    r"(?im)^:pubkey enc packet:.*\bkeyid\s+([0-9A-F]{16})\b"
)
_ENCRYPTED_DATA_PACKET_RE = re.compile(
    r"(?im)^:(?:encrypted data packet|ocb encrypted packet|aead encrypted packet):"
)
_NO_SECRET_KEY_RE = re.compile(
    r"(?is)(?:no secret key|secret key not available)"
)
_UNRELATED_GPG_ERROR_RE = re.compile(
    r"(?im)(?:no valid OpenPGP data found|invalid packet|CRC error|"
    r"fatal error|can't open|cannot open|No such file|I/O error|"
    r"bad session key|unexpected error|broken pipe|permission denied|"
    r"not a valid OpenPGP message)"
)
_PREFIX_COMPONENT_RE = re.compile(r"^[A-Za-z0-9._-]+$")


class B2Error(Exception):
    """Base exception for B2 operations with sensitive data redacted."""
    pass


class B2SecurityError(B2Error):
    """Raised when B2 security invariants (bucket restriction, no deletes) are violated."""
    pass


def log(msg):
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    print("%s %s" % (stamp, redact(msg)), flush=True)


def redact(text, secrets=None):
    """Sanitize strings to prevent tokens, keys, or secrets from appearing in output."""
    if not isinstance(text, str):
        text = str(text)
    # Redact Authorization header values
    text = re.sub(r'(?i)(authorization[:=]\s*)(?:Bearer\s+|Basic\s+)?[^\s,;&]+', r'\1[REDACTED]', text)
    # Redact common credential keys
    text = re.sub(r'(?i)(applicationKey[:=]\s*)[^\s,;&]+', r'\1[REDACTED]', text)
    text = re.sub(r'(?i)(authorizationToken[:=]\s*)[^\s,;&]+', r'\1[REDACTED]', text)
    # Redact specific secrets if provided
    if secrets:
        for s in secrets:
            if s and isinstance(s, str) and len(s) >= 4:
                text = text.replace(s, "[REDACTED]")
    return text


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def sha1_file(path):
    h = hashlib.sha1()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_credentials(path):
    """Parse credentials from a 0600 root-owned file. Values are never logged."""
    if not os.path.exists(path):
        raise B2SecurityError("credentials file %s not found" % path)

    st = os.stat(path)
    # If not running as root (e.g. during local tests), allow owner match
    # On production, enforce root ownership and mode 0600
    if os.getuid() == 0 and st.st_uid != 0:
        raise B2SecurityError("credentials file %s must be owned by root" % path)
    if st.st_mode & 0o077:
        raise B2SecurityError("credentials file %s has overly permissive permissions (%o); must be 0600" %
                              (path, st.st_mode & 0o777))

    creds = {}
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, val = line.partition("=")
            creds[key.strip()] = val.strip()

    for req in ("keyID", "applicationKey"):
        if not creds.get(req):
            raise B2SecurityError("required key '%s' missing or empty in %s" % (req, path))

    return creds


def update_status(status_file, offsite_status, failure_file=None):
    """Durably and atomically update the status file with offsite state."""
    status_dir = os.path.dirname(status_file)
    if status_dir and not os.path.exists(status_dir):
        try:
            os.makedirs(status_dir, mode=0o700, exist_ok=True)
        except OSError:
            pass

    status = {}
    if os.path.exists(status_file):
        try:
            with open(status_file, "r", encoding="utf-8") as fh:
                status = json.load(fh)
        except Exception:
            status = {}

    status["offsite"] = offsite_status

    tmp = status_file + ".tmp.%d" % os.getpid()
    try:
        with open(tmp, "w", encoding="utf-8") as fh:
            json.dump(status, fh, indent=2)
            fh.write("\n")
        os.chmod(tmp, 0o600)
        os.replace(tmp, status_file)
    except OSError as exc:
        log("WARNING: could not update status file %s: %s" % (status_file, exc))
        if os.path.exists(tmp):
            try:
                os.unlink(tmp)
            except OSError:
                pass

    if failure_file:
        if offsite_status.get("status") == "ok":
            if os.path.exists(failure_file):
                try:
                    os.unlink(failure_file)
                except OSError:
                    pass
        else:
            try:
                fail_tmp = failure_file + ".tmp.%d" % os.getpid()
                with open(fail_tmp, "w", encoding="utf-8") as fh:
                    json.dump(offsite_status, fh, indent=2)
                    fh.write("\n")
                os.chmod(fail_tmp, 0o600)
                os.replace(fail_tmp, failure_file)
            except OSError:
                pass


def b2_request(url, headers=None, data=None, method=None, timeout=120, retry=True, secrets=None):
    """Perform an HTTP request against B2 Native API with exponential backoff on transient errors.

    Retries: 429, 500, 502, 503, 504, and transient network errors (if retry=True).
    Does NOT retry: 400, 401, 403, 404, or checksum/security errors.
    """
    req_headers = dict(headers or {})
    last_error = None
    max_attempts = MAX_RETRIES if retry else 1

    for attempt in range(max_attempts):
        req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                body = resp.read()
                return resp.code, resp.headers, body
        except urllib.error.HTTPError as exc:
            code = exc.code
            try:
                raw_body = exc.read().decode("utf-8", errors="replace")
                detail = redact(raw_body, secrets=secrets)[:400]
            except Exception:
                detail = ""
            finally:
                exc.close()
            last_error = "HTTP %s %s" % (code, detail)

            # Retry transient rate-limit (429) or server errors (5xx)
            if retry and code in (429, 500, 502, 503, 504) and attempt < max_attempts - 1:
                delay = RETRY_BASE ** attempt
                log("Transient error (%s), retrying in %ds (attempt %d/%d)..." %
                    (last_error, delay, attempt + 1, max_attempts))
                time.sleep(delay)
                continue

            raise B2Error("B2 request failed (%s)" % last_error)
        except urllib.error.URLError as exc:
            last_error = redact(str(exc.reason), secrets=secrets)
            if retry and attempt < max_attempts - 1:
                delay = RETRY_BASE ** attempt
                log("Network error (%s), retrying in %ds (attempt %d/%d)..." %
                    (last_error, delay, attempt + 1, max_attempts))
                time.sleep(delay)
                continue
            raise B2Error("B2 endpoint unreachable after retries: %s" % last_error)
        except Exception as exc:
            raise B2Error("Unexpected error during B2 request: %s" % redact(str(exc), secrets=secrets))

    raise B2Error("B2 request failed after %d attempts: %s" % (max_attempts, last_error))


def _is_safe_object_prefix(prefix):
    """Return True if prefix is a relative, slash-terminated object-key prefix.

    Rejects empty values, absolute paths, traversal, empty/dot components,
    backslashes, NULs, and characters outside a conservative object-key set.
    """
    if not isinstance(prefix, str) or not prefix:
        return False
    if "\\" in prefix or "\x00" in prefix or "\n" in prefix or "\r" in prefix:
        return False
    if prefix.startswith("/") or prefix.startswith("./") or prefix.startswith("../"):
        return False
    if not prefix.endswith("/"):
        return False
    if len(prefix) > 512 or "//" in prefix:
        return False
    parts = prefix.split("/")
    # Trailing slash yields a final empty string; every other component must be safe.
    if not parts or parts[-1] != "":
        return False
    for component in parts[:-1]:
        if component in ("", ".", ".."):
            return False
        if not _PREFIX_COMPONENT_RE.fullmatch(component):
            return False
    return True


def requested_prefix_in_scope(requested_prefix, authorized_prefix=AUTHORIZED_NAME_PREFIX):
    """True when requested_prefix is the authorized prefix or a safe sub-prefix of it.

    `backups/canary/` is in scope for authorized `backups/`. `backups-other/` is not,
    because it does not begin with the authorized prefix including its trailing slash.
    """
    if not _is_safe_object_prefix(requested_prefix):
        return False
    if not _is_safe_object_prefix(authorized_prefix):
        return False
    return requested_prefix == authorized_prefix or requested_prefix.startswith(authorized_prefix)


def assert_requested_prefix_in_scope(requested_prefix, authorized_prefix=AUTHORIZED_NAME_PREFIX):
    """Raise B2SecurityError unless the uploader prefix lies within authorized scope."""
    if not requested_prefix_in_scope(requested_prefix, authorized_prefix):
        raise B2SecurityError("requested object prefix is empty, malformed, or outside authorized scope")


def authorize(creds, expected_bucket=DEFAULT_BUCKET, expected_bucket_id=DEFAULT_BUCKET_ID,
              expected_prefix=AUTHORIZED_NAME_PREFIX):
    """Authorize against B2 via b2_authorize_account (Native API v4).

    Uses HTTP Basic Auth with keyID:applicationKey.
    Enforces that the application key is restricted to the specific bucket,
    the specific bucket ID, the authorized filename prefix (exactly
    AUTHORIZED_NAME_PREFIX), and has exactly the required capabilities with
    NO delete or administrative permissions.
    The uploader's requested object prefix is validated separately as a safe
    sub-prefix of that authorized prefix; it must not be compared for equality
    against the key's namePrefix.
    Returns auth details including apiUrl, downloadUrl, and the raw authorization token.
    """
    key_id = creds["keyID"]
    app_key = creds["applicationKey"]
    raw_auth = ("%s:%s" % (key_id, app_key)).encode("utf-8")
    b64_auth = base64.b64encode(raw_auth).decode("ascii")

    headers = {
        "Authorization": "Basic %s" % b64_auth,
        "Accept": "application/json",
    }

    try:
        _, _, body = b2_request(B2_AUTH_URL, headers=headers, method="GET", secrets=(app_key, b64_auth))
    except Exception as exc:
        raise B2Error(redact(str(exc), secrets=(app_key, b64_auth)))
    try:
        data = json.loads(body.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise B2Error("Invalid JSON in authorization response: %s" % exc)

    token = data.get("authorizationToken")
    if not token or not isinstance(token, str):
        raise B2Error("Authorization response missing or invalid authorizationToken")

    api_info = data.get("apiInfo")
    if not isinstance(api_info, dict):
        raise B2Error("Authorization response missing apiInfo object")

    storage_api = api_info.get("storageApi")
    if not isinstance(storage_api, dict):
        raise B2Error("Authorization response missing apiInfo.storageApi object")

    api_url = storage_api.get("apiUrl")
    download_url = storage_api.get("downloadUrl")
    if not api_url or not isinstance(api_url, str):
        raise B2Error("Authorization response missing or invalid storageApi.apiUrl")
    if not download_url or not isinstance(download_url, str):
        raise B2Error("Authorization response missing or invalid storageApi.downloadUrl")

    allowed = storage_api.get("allowed")
    if not isinstance(allowed, dict):
        raise B2Error("Authorization response missing storageApi.allowed object")

    # Invariant: Must be restricted to exactly the expected bucket
    buckets = allowed.get("buckets")
    if not isinstance(buckets, list) or len(buckets) != 1:
        raise B2SecurityError("B2 application key is not restricted to exactly one bucket")

    bucket_entry = buckets[0]
    if not isinstance(bucket_entry, dict):
        raise B2SecurityError("B2 application key bucket entry is malformed")

    bucket_id = bucket_entry.get("id")
    bucket_name = bucket_entry.get("name")
    if not bucket_id or not bucket_name:
        raise B2SecurityError("B2 application key bucket entry missing id or name")

    if expected_bucket and bucket_name != expected_bucket:
        raise B2SecurityError("B2 application key is restricted to bucket '%s', expected '%s'" %
                              (bucket_name, expected_bucket))

    if expected_bucket_id and bucket_id != expected_bucket_id:
        raise B2SecurityError("B2 application key is restricted to bucket ID '%s', expected '%s'" %
                              (bucket_id, expected_bucket_id))

    # Invariant: Must have exact prefix restriction
    name_prefix = allowed.get("namePrefix")
    if expected_prefix and name_prefix != expected_prefix:
        raise B2SecurityError("B2 application key filename prefix is '%s', expected '%s'" %
                              (name_prefix, expected_prefix))

    # Invariant: Capabilities must match REQUIRED_CAPABILITIES exactly
    capabilities = allowed.get("capabilities")
    if not isinstance(capabilities, list):
        raise B2SecurityError("B2 application key missing capabilities list")

    caps_set = set(capabilities)
    if caps_set != REQUIRED_CAPABILITIES:
        raise B2SecurityError("B2 application key capabilities mismatch: got %s, expected %s" %
                              (sorted(caps_set), sorted(REQUIRED_CAPABILITIES)))

    # Invariant: Must NEVER have delete or administration permissions
    for forbidden in FORBIDDEN_CAPABILITIES:
        if forbidden in caps_set:
            raise B2SecurityError("B2 application key possesses forbidden capability '%s'" % forbidden)

    return {
        "account_id": data.get("accountId"),
        "auth_token": token,
        "api_url": api_url.rstrip("/"),
        "download_url": download_url.rstrip("/"),
        "bucket_id": bucket_id,
        "bucket_name": bucket_name,
        "name_prefix": name_prefix,
        "capabilities": sorted(caps_set),
    }


def get_upload_url(auth):
    """Obtain an upload URL and upload authorization token via b2_get_upload_url.

    The account authorization token is passed directly in Authorization header without Bearer.
    """
    url = "%s/b2api/v4/b2_get_upload_url" % auth["api_url"]
    headers = {
        "Authorization": auth["auth_token"],
        "Content-Type": "application/json",
    }
    payload = json.dumps({"bucketId": auth["bucket_id"]}).encode("utf-8")

    _, _, body = b2_request(url, headers=headers, data=payload, method="POST")
    try:
        data = json.loads(body.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise B2Error("Invalid JSON in get_upload_url response: %s" % exc)

    upload_url = data.get("uploadUrl")
    upload_token = data.get("authorizationToken")
    if not upload_url or not upload_token:
        raise B2Error("get_upload_url response missing uploadUrl or authorizationToken")

    return upload_url, upload_token


def upload(upload_url, upload_token, local_path, remote_name):
    """Upload raw ciphertext to B2 uploadUrl.

    Headers:
      - Authorization: <upload_token> (direct token, no Bearer scheme)
      - X-Bz-File-Name: URL-encoded filename (preserves / for virtual directory structure)
      - Content-Type: b2/x-auto
      - Content-Length: size in bytes
      - X-Bz-Content-Sha1: SHA-1 hex digest of file
    """
    sha1 = sha1_file(local_path)
    size = os.path.getsize(local_path)

    quoted_name = urllib.parse.quote(remote_name, safe="/")
    headers = {
        "Authorization": upload_token,
        "X-Bz-File-Name": quoted_name,
        "Content-Type": "b2/x-auto",
        "Content-Length": str(size),
        "X-Bz-Content-Sha1": sha1,
    }

    with open(local_path, "rb") as fh:
        body = fh.read()

    _, _, resp_body = b2_request(upload_url, headers=headers, data=body, method="POST", timeout=300, retry=False)
    try:
        resp_data = json.loads(resp_body.decode("utf-8"))
    except json.JSONDecodeError as exc:
        raise B2Error("Invalid JSON in upload response: %s" % exc)

    return resp_data


def download(auth, remote_name, dest_path):
    """Download an object by name via Backblaze B2 Native API and compute its SHA-256.

    URL structure: <download_url>/file/<bucket_name>/<encoded_file_name>
    Header: Authorization: <account_auth_token> (direct token, no Bearer scheme)
    Writes to dest_path with mode 0600 and returns the SHA-256 hex digest.
    """
    quoted_name = urllib.parse.quote(remote_name, safe="/")
    url = "%s/file/%s/%s" % (auth["download_url"], auth["bucket_name"], quoted_name)
    headers = {
        "Authorization": auth["auth_token"],
    }

    h = hashlib.sha256()
    req = urllib.request.Request(url, headers=headers, method="GET")

    # Use umask 0077 to ensure the destination file is private
    old_umask = os.umask(0o077)
    try:
        for attempt in range(MAX_RETRIES):
            try:
                with urllib.request.urlopen(req, timeout=300) as resp:
                    with open(dest_path, "wb") as out:
                        for chunk in iter(lambda: resp.read(1 << 20), b""):
                            h.update(chunk)
                            out.write(chunk)
                    os.chmod(dest_path, 0o600)
                    return h.hexdigest()
            except urllib.error.HTTPError as exc:
                code = exc.code
                try:
                    if code in (429, 500, 502, 503, 504) and attempt < MAX_RETRIES - 1:
                        time.sleep(RETRY_BASE ** attempt)
                        h = hashlib.sha256()
                        continue
                    raise B2Error("Download failed with HTTP %s" % code)
                finally:
                    exc.close()
            except urllib.error.URLError as exc:
                if attempt < MAX_RETRIES - 1:
                    time.sleep(RETRY_BASE ** attempt)
                    h = hashlib.sha256()
                    continue
                raise B2Error("Download unreachable: %s" % redact(str(exc.reason)))
    finally:
        os.umask(old_umask)

    raise B2Error("Download failed after retries")


def newest_dump(backup_dir):
    """Locate the newest local plaintext MySQL backup."""
    candidates = [
        os.path.join(backup_dir, n)
        for n in os.listdir(backup_dir)
        if n.startswith("libris-") and n.endswith(".sql.gz") and not n.endswith(".partial.gz")
    ]
    if not candidates:
        raise FileNotFoundError("no MySQL dump found in %s" % backup_dir)
    return max(candidates, key=os.path.getmtime)


def encryption_keyids_for_fingerprint(colon_listing, fingerprint=EXPECTED_RECIPIENT_FINGERPRINT):
    """Return 16-hex key IDs of encryption-capable keys under the pinned fingerprint.

    Parses GnuPG `--with-colons --with-fingerprint --with-subkey-fingerprint` output.
    Only lowercase `e` in the capabilities field counts as encrypt-enabled (uppercase
    `E` means the usage is theoretically available, not enabled). Fail closed with an
    empty set if the primary fingerprint is absent or no encrypt-capable key is found.
    """
    expected = re.sub(r"\s+", "", fingerprint or "").upper()
    if not re.fullmatch(r"[0-9A-F]{40}", expected):
        return set()

    allowed = set()
    in_target = False
    pending = None
    for raw in colon_listing.splitlines():
        fields = raw.split(":")
        rec = fields[0] if fields else ""
        if rec in ("pub", "sec"):
            in_target = False
            usage = fields[11] if len(fields) > 11 else ""
            pending = ("pub", usage)
        elif rec in ("sub", "ssb"):
            usage = fields[11] if len(fields) > 11 else ""
            pending = ("sub", usage)
        elif rec == "fpr" and pending is not None:
            fpr = re.sub(r"\s+", "", fields[9] if len(fields) > 9 else "").upper()
            kind, usage = pending
            pending = None
            if not re.fullmatch(r"[0-9A-F]{40}", fpr):
                continue
            if kind == "pub":
                in_target = fpr == expected
                if in_target and "e" in usage:
                    allowed.add(fpr[-16:])
            elif kind == "sub" and in_target and "e" in usage:
                allowed.add(fpr[-16:])
    return allowed


def evaluate_list_packets_result(returncode, stdout, stderr, allowed_keyids):
    """Accept ciphertext only when packet structure and recipient are proven.

    Observed GPG behavior on a public-key-only backup host:
      - stdout lists `:pubkey enc packet: ... keyid <16 hex>` and an encrypted
        payload packet (`:encrypted data packet:` or `:ocb encrypted packet:`).
      - stderr reports `public key decryption failed: No secret key` /
        `decryption failed: No secret key`.
      - process exit status is 2.

    Exit 0 is accepted when the same structure and recipient are present (secret
    key available). A nonzero status is accepted only when that structure matches
    and the status is attributable to an unavailable secret key. Missing packets,
    unexpected recipients, misleading text that merely mentions a key ID,
    unrelated errors, timeouts, and unexplained nonzero statuses fail closed.
    """
    if returncode is None:
        raise B2Error("GPG packet inspection produced no exit status")
    if not isinstance(allowed_keyids, set) or not allowed_keyids:
        raise B2Error("could not identify encryption-capable subkeys for configured recipient fingerprint")

    stdout_text = stdout if isinstance(stdout, str) else (stdout or b"").decode("utf-8", errors="replace")
    stderr_text = stderr if isinstance(stderr, str) else (stderr or b"").decode("utf-8", errors="replace")
    combined = "%s\n%s" % (stdout_text, stderr_text)

    if _UNRELATED_GPG_ERROR_RE.search(combined):
        raise B2Error("GPG packet inspection failed with an unrelated error")

    packet_keyids = {m.group(1).upper() for m in _PUBKEY_ENC_PACKET_RE.finditer(stdout_text)}
    if not packet_keyids:
        raise B2Error("GPG ciphertext missing a public-key encrypted-data packet")
    if not _ENCRYPTED_DATA_PACKET_RE.search(stdout_text):
        raise B2Error("GPG ciphertext missing an encrypted payload packet")

    unexpected = packet_keyids - {k.upper() for k in allowed_keyids}
    if unexpected:
        raise B2Error("GPG ciphertext recipient does not match the configured encryption subkey")

    if returncode == 0:
        return
    if returncode != 0 and _NO_SECRET_KEY_RE.search(combined):
        return
    raise B2Error("GPG packet inspection failed with an unexplained nonzero status")


def _gpg_encryption_keyids(env, fingerprint=EXPECTED_RECIPIENT_FINGERPRINT):
    """Resolve encrypt-capable key IDs from the local public keyring. Public key only."""
    cmd = [
        "gpg", "--batch", "--with-colons",
        "--with-fingerprint", "--with-subkey-fingerprint",
        "--list-keys", fingerprint,
    ]
    try:
        res = subprocess.run(
            cmd, env=env, capture_output=True, timeout=GPG_PACKET_TIMEOUT_SECONDS, check=False,
        )
    except FileNotFoundError:
        raise B2Error("gpg binary not found on host")
    except subprocess.TimeoutExpired:
        raise B2Error("GPG public-key listing timed out")
    stdout = res.stdout.decode("utf-8", errors="replace")
    if res.returncode != 0:
        raise B2Error("GPG could not list the configured recipient public key")
    allowed = encryption_keyids_for_fingerprint(stdout, fingerprint)
    if not allowed:
        raise B2Error("configured recipient fingerprint has no encryption-capable subkey in the public keyring")
    return allowed


def inspect_openpgp_ciphertext(path, env, allowed_keyids):
    """Inspect ciphertext with `gpg --list-packets` without requiring a secret key."""
    check_cmd = ["gpg", "--batch", "--list-packets", path]
    try:
        res = subprocess.run(
            check_cmd, env=env, capture_output=True, timeout=GPG_PACKET_TIMEOUT_SECONDS, check=False,
        )
    except FileNotFoundError:
        raise B2Error("gpg binary not found on host")
    except subprocess.TimeoutExpired:
        raise B2Error("GPG packet inspection timed out")
    evaluate_list_packets_result(res.returncode, res.stdout, res.stderr, allowed_keyids)


def encrypt_file(input_path, output_path, recipient, gpg_home=None):
    """Encrypt input_path using GPG to recipient.

    Uses only the recipient public key. Never requires, transfers, or logs a
    private key or passphrase. Ensures mode 0600 on output and validates that
    the ciphertext is a public-key encrypted OpenPGP message for an
    encryption-capable subkey of EXPECTED_RECIPIENT_FINGERPRINT.
    """
    env = dict(os.environ)
    if gpg_home:
        env["GNUPGHOME"] = gpg_home

    cmd = [
        "gpg", "--batch", "--yes", "--trust-model", "always",
        "--recipient", recipient,
        "--output", output_path,
        "--encrypt", input_path,
    ]

    try:
        subprocess.run(cmd, check=True, env=env, capture_output=True, timeout=GPG_TIMEOUT_SECONDS)
    except FileNotFoundError:
        raise B2Error("gpg binary not found on host")
    except subprocess.TimeoutExpired:
        raise B2Error("GPG encryption timed out")
    except subprocess.CalledProcessError as exc:
        err = (exc.stderr or b"").decode("utf-8", errors="replace")[:300]
        raise B2Error("GPG encryption failed: %s" % redact(err))

    os.chmod(output_path, 0o600)

    allowed_keyids = _gpg_encryption_keyids(env)
    inspect_openpgp_ciphertext(output_path, env, allowed_keyids)


def run_offsite_backup(args):
    """Execute complete offsite backup flow: find dump, encrypt, upload, download-verify."""
    started_utc = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    dump_path = args.dump_file
    if not dump_path:
        dump_path = newest_dump(args.backup_dir)

    log("Selected dump: %s" % os.path.basename(dump_path))

    # Plaintext protection: ensure dump_path is .sql.gz
    if not dump_path.endswith(".sql.gz"):
        raise ValueError("Target dump must be a .sql.gz file: %s" % dump_path)

    encrypted_path = dump_path + ".gpg"
    remote_object_name = "%s%s" % (args.prefix, os.path.basename(encrypted_path))
    verify_path = os.path.join(args.backup_dir, ".verify-%s" % os.path.basename(encrypted_path))

    try:
        # 1. Encrypt to public key
        log("Encrypting %s..." % os.path.basename(dump_path))
        encrypt_file(dump_path, encrypted_path, args.recipient, args.gpg_home)
        local_sha = sha256_file(encrypted_path)
        ciphertext_size = os.path.getsize(encrypted_path)
        log("Encrypted ciphertext SHA-256: %s (%d bytes)" % (local_sha[:16], ciphertext_size))

        # 2. Authorize with B2
        log("Authorizing against B2...")
        creds = read_credentials(args.credentials)
        expected_bucket_id = getattr(args, "bucket_id", DEFAULT_BUCKET_ID)
        # Key authorization is pinned to AUTHORIZED_NAME_PREFIX. The uploader
        # prefix (e.g. backups/canary/) is a separate in-scope sub-prefix check.
        assert_requested_prefix_in_scope(args.prefix, AUTHORIZED_NAME_PREFIX)
        auth = authorize(creds, expected_bucket=args.bucket,
                         expected_bucket_id=expected_bucket_id,
                         expected_prefix=AUTHORIZED_NAME_PREFIX)
        assert_requested_prefix_in_scope(args.prefix, auth["name_prefix"])
        log("Authorized against bucket '%s' (bucketId: %s)" % (auth["bucket_name"], auth["bucket_id"]))

        # 3 & 4. Obtain upload URL and upload ciphertext.
        # Per B2 API specification, an upload URL is single-use; if an upload fails,
        # a new upload URL must be requested before retrying.
        upload_res = None
        for attempt in range(MAX_RETRIES):
            log("Obtaining upload URL (attempt %d/%d)..." % (attempt + 1, MAX_RETRIES))
            upload_url, upload_token = get_upload_url(auth)
            log("Uploading ciphertext to '%s'..." % remote_object_name)
            try:
                upload_res = upload(upload_url, upload_token, encrypted_path, remote_object_name)
                log("Uploaded: fileId=%s" % upload_res.get("fileId"))
                break
            except B2Error as exc:
                if attempt < MAX_RETRIES - 1:
                    delay = RETRY_BASE ** attempt
                    log("Upload attempt %d failed (%s), retrying with fresh upload URL in %ds..." %
                        (attempt + 1, exc, delay))
                    time.sleep(delay)
                    continue
                raise

        # 5. Download and verify
        log("Verifying upload via download-by-name...")
        remote_sha = download(auth, remote_object_name, verify_path)
        if remote_sha != local_sha:
            raise B2Error("Integrity check failed: local SHA-256 %s != remote SHA-256 %s" %
                          (local_sha, remote_sha))

        log("Integrity verified: remote SHA-256 matches local ciphertext (%s)" % local_sha[:16])

        # 6. Record success in persistent status
        now_utc = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        offsite_status = {
            "status": "ok",
            "bucket": auth["bucket_name"],
            "object": remote_object_name,
            "sha256": local_sha,
            "bytes": ciphertext_size,
            "file_id": upload_res.get("fileId"),
            "verified_utc": now_utc,
        }
        update_status(args.status_file, offsite_status, args.failure_file)
        log("Recorded offsite status 'ok' in %s" % args.status_file)
        return 0

    except Exception as exc:
        err_msg = redact(str(exc))
        log("ERROR: %s" % err_msg)
        now_utc = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        offsite_status = {
            "status": "failed",
            "error": err_msg,
            "dump": os.path.basename(dump_path) if dump_path else None,
            "attempted_utc": now_utc,
        }
        update_status(args.status_file, offsite_status, args.failure_file)
        return 1

    finally:
        # Secure cleanup: NEVER remove plaintext dump; ONLY remove ciphertext and verify temp files
        if os.path.exists(encrypted_path):
            try:
                os.unlink(encrypted_path)
            except OSError:
                pass
        if os.path.exists(verify_path):
            try:
                os.unlink(verify_path)
            except OSError:
                pass


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--backup-dir", default=DEFAULT_BACKUP_DIR,
                        help="Directory containing local backups (default: %(default)s)")
    parser.add_argument("--credentials", default=DEFAULT_CREDS,
                        help="Path to B2 credentials file (default: %(default)s)")
    parser.add_argument("--bucket", default=DEFAULT_BUCKET,
                        help="Target B2 bucket name (default: %(default)s)")
    parser.add_argument("--bucket-id", default=DEFAULT_BUCKET_ID,
                        help="Target B2 bucket ID (default: %(default)s)")
    parser.add_argument("--prefix", default=DEFAULT_PREFIX,
                        help="Object key prefix in bucket (default: %(default)s)")
    parser.add_argument("--recipient", default="libris-backup@local",
                        help="GPG recipient identifier (default: %(default)s)")
    parser.add_argument("--gpg-home", default=os.path.expanduser("~/.gnupg"),
                        help="GPG home directory (default: %(default)s)")
    parser.add_argument("--status-file", default=DEFAULT_STATUS_FILE,
                        help="Persistent status file path (default: %(default)s)")
    parser.add_argument("--failure-file", default=DEFAULT_FAILURE_FILE,
                        help="Persistent failure flag file path (default: %(default)s)")
    parser.add_argument("--dump-file", default=None,
                        help="Explicit dump file to upload (default: newest in backup-dir)")
    args = parser.parse_args()

    rc = run_offsite_backup(args)
    sys.exit(rc)


if __name__ == "__main__":
    main()
