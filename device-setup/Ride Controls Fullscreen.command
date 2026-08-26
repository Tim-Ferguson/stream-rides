#!/bin/zsh
DIR="${0:A:h}"
"$DIR/saro-control" ride-controls
echo
echo "Use Restore Ride Overlay.command when you are done with the ride controls."
read -k 1 "?Press any key to close."
