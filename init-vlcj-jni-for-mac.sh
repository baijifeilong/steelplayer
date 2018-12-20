#! /usr/bin/env bash

cd src/main/resources/
cp -r /Applications/VLC.app/Contents/MacOS/lib darwin
rm darwin/*.*.*
cd darwin
install_name_tool -add_rpath @loader_path libvlc.dylib
mkdir vlc
cp -r /Applications/VLC.app/Contents/MacOS/plugins vlc/plugins

