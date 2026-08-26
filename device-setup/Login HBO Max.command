#!/bin/zsh
DIR="${0:A:h}"
"$DIR/saro-control" login-hbo
echo
echo "After signing in on the bike, run HBO Max + Ride Overlay.command."
read -k 1 "?Press any key to close."
