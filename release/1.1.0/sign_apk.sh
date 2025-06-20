#!/bin/bash

# ACC IDE APK Signing Script
# This script helps sign the unsigned APK with your keystore

# Default values
APK_FILE="acc_ide-1.0.0-unsigned.apk"
SIGNED_APK="acc_ide-1.0.0-signed.apk"
ALIGNED_APK="acc_ide-1.0.0.apk"

echo "=== ACC IDE APK Signing Tool ==="
echo ""

# Check if the input file exists
if [ ! -f "$APK_FILE" ]; then
    echo "Error: $APK_FILE not found in current directory!"
    exit 1
fi

# Get keystore information
read -p "Enter path to your keystore: " KEYSTORE_PATH
if [ ! -f "$KEYSTORE_PATH" ]; then
    echo "Error: Keystore file not found!"
    exit 1
fi

read -p "Enter keystore alias: " KEY_ALIAS
read -s -p "Enter keystore password: " KEYSTORE_PASSWORD
echo ""
read -s -p "Enter key password (press Enter if same as keystore password): " KEY_PASSWORD
echo ""

if [ -z "$KEY_PASSWORD" ]; then
    KEY_PASSWORD="$KEYSTORE_PASSWORD"
fi

# Sign the APK
echo "Signing APK..."
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore "$KEYSTORE_PATH" \
    -storepass "$KEYSTORE_PASSWORD" -keypass "$KEY_PASSWORD" \
    -signedjar "$SIGNED_APK" "$APK_FILE" "$KEY_ALIAS"

if [ $? -ne 0 ]; then
    echo "Error: APK signing failed!"
    exit 1
fi

# Check if zipalign is available
if command -v zipalign &> /dev/null; then
    echo "Aligning the APK..."
    zipalign -v 4 "$SIGNED_APK" "$ALIGNED_APK"
    
    if [ $? -eq 0 ]; then
        echo "APK successfully signed and aligned: $ALIGNED_APK"
    else
        echo "Warning: APK alignment failed. Using unaligned APK: $SIGNED_APK"
    fi
else
    echo "Warning: zipalign tool not found. Using unaligned APK: $SIGNED_APK"
fi

echo ""
echo "The APK is now ready for distribution."
echo "=============================================" 