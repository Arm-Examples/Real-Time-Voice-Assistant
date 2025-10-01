#
# SPDX-FileCopyrightText: Copyright 2025 Arm Limited and/or its affiliates <open-source-office@arm.com>
#
# SPDX-License-Identifier: Apache-2.0
#

"""
Utility script to push resources needed by the app onto device.

Pushes local models, configuration files and test images to an Android device using ADB.

Supports:
- STT model files (flat files)
- Configuration JSON files (flat files)
- LLM model files (organized under a framework subdirectory, e.g., llama.cpp)

Usage:
    STT / Config:
        python3 pushModel.py <local_dir> <device_dir>

    LLM:
        python3 pushModel.py <local_dir> <device_dir> --llm_framework <llm_framework>

Arguments:
    local_dir      Path to the local folder containing files to push
    device_dir     Target directory on the Android device
    llm_framework  Optional. Subfolder to push from (e.g., 'llama.cpp').
                   Applies only for LLM models.

Behavior:
- For LLM: Adds the framework subfolder to local and device paths
- Creates remote directories with `mkdir -p`
- Adds executable permission (`chmod +x`) to the LLM framework directory
- Skips files that are already up to date (by comparing md5 checksums)
- Falls back to using `cat >` if `adb push` fails
"""

from argparse import ArgumentParser
import os
import sys
import subprocess
import hashlib
import logging

def md5sum(filepath):
    """
    Calculate the MD5 checksum of a local file.

    @param filepath: Path to the local file
    @return:         Hexadecimal MD5 checksum string
    """
    hash_md5 = hashlib.md5()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def adb_md5sum(device_path):
    """
    Retrieve the MD5 checksum of a file on the Android device.

    @param device_path: Full path to the file on the device
    @return:            MD5 checksum string or None if file does not exist
    """
    try:
        result = subprocess.check_output(['adb', 'shell', f'md5sum "{device_path}"'], stderr=subprocess.DEVNULL)
        return result.decode().split()[0]
    except subprocess.CalledProcessError:
        return None


def adb_delete_file(device_path):
    """
    Delete file on the Android device.

    @param device_path: Full path to the file on the device
    """
    try:
        result = subprocess.check_output(['adb', 'shell', f'rm "{device_path}"'], stderr=subprocess.DEVNULL)
        logging.info(f"adb delete succeed: {device_path}")
    except subprocess.CalledProcessError:
        logging.info(f"adb delete failed: {device_path}")


def push_file(local_path, device_path):
    """
    Push a local file to the device using `adb push`.
    Falls back to `cat >` if ADB fails.

    @param local_path:  Path to the local file
    @param device_path: Destination path on the Android device
    """
    try:
        logging.info(f"Pushing: {device_path}")
        subprocess.run(['adb', 'push', local_path, device_path], check=True)
    except subprocess.CalledProcessError:
        logging.info(f"adb push failed, retrying with cat > {device_path}")
        with open(local_path, 'rb') as f:
            subprocess.run(['adb', 'shell', f'cat > "{device_path}"'], input=f.read(), check=True)


def adb_mkdir(path):
    logging.info(f"mkdir'ing and chowning {path}")
    subprocess.run(['adb', 'shell', f'mkdir -p "{path}"'], check=True)
    subprocess.run(['adb', 'shell', f'chmod +xr "{path}"'], check=True)

def sync_dir(local_dir, device_dir):
    """
    Recursively sync files and folders from a local directory to a device directory via ADB.
    Skips files that are already up to date (based on MD5 hash).

    @param local_dir:   Path to the local directory
    @param device_dir:  Destination directory on the Android device
    """


    for name in os.listdir(local_dir):
        local_path = os.path.join(local_dir, name)
        device_path = f"{device_dir}/{name}"

        if os.path.isdir(local_path):
            # Recursively sync subdirectories
            logging.info(f"Entering directory: {local_path}")
            adb_mkdir(device_path)
            sync_dir(local_path, device_path)
        else:
            # Sync individual files
            local_hash = md5sum(local_path)
            remote_hash = adb_md5sum(device_path)

            if local_hash != remote_hash:
                logging.info(f"Updating file: {device_path}")
                adb_delete_file(device_path)
                push_file(local_path, device_path)
                subprocess.run(['adb', 'shell', f'chmod +xr "{device_path}"'], check=True)
            else:
                logging.info(f"{device_path} is up to date, skipping.")


def main():
    logging.basicConfig(filename="download.log", level=logging.DEBUG)
    logging.getLogger().addHandler(logging.StreamHandler(sys.stdout))

    parser = ArgumentParser(description="Push local model/config files to an Android device using ADB.")
    parser.add_argument("local_dir",
                        help="Path to the local model/config directory")
    parser.add_argument("device_dir",
                        help="Path to the destination directory on the device")
    parser.add_argument("--llm_framework",
                        help="Optional: LLM framework subdirectory (e.g., llama.cpp)",
                        default=None)

    args = parser.parse_args()

    local_dir = os.path.abspath(args.local_dir)
    device_dir = args.device_dir
    is_llm = args.llm_framework is not None
    llm_framework = args.llm_framework

    subprocess.run(['adb', 'shell', f'mkdir -p "{device_dir}"'], check=True)

    if not os.path.isdir(local_dir):
        logging.info(f"Directory does not exist: {local_dir}, skipping.")
        return

    sync_dir(local_dir, device_dir)
    logging.info("Done.")

if __name__ == "__main__":
    main()
