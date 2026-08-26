#!/bin/zsh
DIR="${0:A:h}"
"$DIR/saro-control" login-netflix
echo
echo "After signing in on the bike, run Netflix + Ride Overlay.command."
read -k 1 "?Press any key to close."
