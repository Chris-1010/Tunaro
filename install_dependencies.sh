#!/bin/bash
mkdir -p app/libs
cd app/libs
if [ ! -f "spotify-app-remote-release-0.8.0.aar" ]; then
    echo "Downloading Spotify App Remote SDK..."
    wget https://github.com/spotify/android-sdk/releases/download/v0.8.0-appremote_v2.1.0-auth/spotify-app-remote-release-0.8.0.aar
fi

if [ ! -f "spotify-auth-release-2.1.0.aar" ]; then
    echo "Downloading Spotify Auth SDK..."
    wget https://github.com/spotify/android-sdk/releases/download/v0.8.0-appremote_v2.1.0-auth/spotify-auth-release-2.1.0.aar
fi