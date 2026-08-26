#!/bin/zsh
DIR="${0:A:h}"
"$DIR/saro-control" login-apple-tv
echo
echo "After signing in on the bike, run Apple TV + Ride Overlay.command."
read -k 1 "?Press any key to close."
