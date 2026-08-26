#!/bin/zsh
DIR="${0:A:h}"
"$DIR/saro-control" login-disney
echo
echo "After signing in on the bike, run Disney+ + Ride Overlay.command."
read -k 1 "?Press any key to close."
